package ml.mypals.vectorthree.shape.media;

import com.mojang.blaze3d.platform.NativeImage;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.ffmpeg.global.avutil;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class VideoFrameSource {
    private static final double SEEK_THRESHOLD_SECONDS = 1.0;
    private static final double REVERSE_WINDOW_SECONDS = 1.0;
    private static final long CACHE_BYTES = 192L << 20;
    private static final long LOADING_DELAY_NANOS = 250_000_000L;

    private final Thread thread;
    private final Object requestLock = new Object();
    private volatile boolean closed;
    private volatile double requestedSeconds;

    private volatile boolean ready;
    private volatile boolean failed;
    private volatile long seekStartedNanos;
    private volatile int width = 1;
    private volatile int height = 1;
    private volatile double durationSeconds;

    private final AtomicReference<int[]> latestFrame = new AtomicReference<>();
    private final AtomicLong frameVersion = new AtomicLong();
    private long consumedVersion = -1;

    static VideoFrameSource open(String file) {
        if (file == null || file.isBlank()) return null;
        return new VideoFrameSource(file);
    }

    private VideoFrameSource(String file) {
        thread = new Thread(() -> runDecodeLoop(file), "vector3-video-decode");
        thread.setDaemon(true);
        thread.start();
    }

    boolean isReady() { return ready; }
    boolean failed() { return failed; }
    /** True while opening, or while a seek has been waiting for its frame for a noticeable time. */
    boolean isLoading() {
        if (failed) return false;
        if (!ready || frameVersion.get() == 0) return true;
        long started = seekStartedNanos;
        return started != 0 && System.nanoTime() - started > LOADING_DELAY_NANOS;
    }
    int width() { return width; }
    int height() { return height; }
    double duration() { return durationSeconds; }

    void requestSeconds(double seconds) {
        requestedSeconds = seconds;
        synchronized (requestLock) { requestLock.notifyAll(); }
    }

    /** Copies the newest decoded frame into {@code target} if one has arrived since the last call. */
    boolean pollFrame(NativeImage target) {
        long version = frameVersion.get();
        if (version == consumedVersion) return false;
        int[] pixels = latestFrame.get();
        if (pixels == null) return false;
        if (target.getWidth() == width && target.getHeight() == height) {
            MemoryUtil.memIntBuffer(target.getPointer(), width * height).put(0, pixels);
            consumedVersion = version;
            return true;
        }
        int copyWidth = Math.min(width, target.getWidth());
        int copyHeight = Math.min(height, target.getHeight());
        for (int y = 0; y < copyHeight; y++) {
            int row = y * width;
            for (int x = 0; x < copyWidth; x++) target.setPixelABGR(x, y, pixels[row + x]);
        }
        consumedVersion = version;
        return true;
    }

    private void runDecodeLoop(String file) {
        FFmpegFrameGrabber grabber;
        try {
            Path path = Path.of(file);
            if (!path.isAbsolute()) path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            path = path.toAbsolutePath().normalize();
            grabber = new FFmpegFrameGrabber(path.toFile());
            // RGBA bytes read as little-endian ints are already the ABGR NativeImage wants.
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            grabber.start();
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not open video file {}", file, exception);
            failed = true;
            return;
        }
        width = Math.max(1, grabber.getImageWidth());
        height = Math.max(1, grabber.getImageHeight());
        long lengthMicros = grabber.getLengthInTime();
        durationSeconds = lengthMicros > 0 ? lengthMicros / 1_000_000.0 : 0;
        ready = true;

        try {
            decodeUntilClosed(grabber, file);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception exception) {
                Vector3.LOGGER.warn("Failed to close video decoder for {}", file, exception);
            }
        }
    }

    // Decoders only run forwards, so going backwards decodes a window before the target once and then
    // serves the following backward steps from this cache. A cached frame at t shows for requests in
    // (t - interval, t], the same "first frame at or after" rule forward decoding uses. Decode thread only.
    private final TreeMap<Double, int[]> cache = new TreeMap<>();
    private final ArrayDeque<int[]> spare = new ArrayDeque<>();
    private int[] previousPublished;

    private void decodeUntilClosed(FFmpegFrameGrabber grabber, String file) throws InterruptedException {
        double interval = grabber.getFrameRate() > 0 ? 1 / grabber.getFrameRate() : 1 / 30.0;
        int maxFrames = (int) Math.clamp(CACHE_BYTES / (4L * width * height), 4, 120);
        double reverseWindow = Math.min(REVERSE_WINDOW_SECONDS, (maxFrames - 2) * interval);
        double lastDecodedSeconds = -1;
        double lastRequested = Double.NaN;
        while (!closed) {
            double seconds = requestedSeconds;
            if (seconds == lastRequested) {
                synchronized (requestLock) {
                    if (requestedSeconds == lastRequested) requestLock.wait(200);
                }
                continue;
            }
            lastRequested = seconds;
            if (serveCached(seconds, interval)) continue;

            // A single bad seek/grab must not kill this thread, or the shape would freeze on its last
            // frame forever. Each request is isolated so a failure only skips that frame.
            try {
                boolean backward = lastDecodedSeconds >= 0 && seconds < lastDecodedSeconds;
                boolean farJump = lastDecodedSeconds < 0 || backward
                        || seconds - lastDecodedSeconds > SEEK_THRESHOLD_SECONDS;
                double windowStart = seconds;
                if (farJump) {
                    seekStartedNanos = System.nanoTime();
                    windowStart = backward ? Math.max(0, seconds - reverseWindow) : seconds;
                    grabber.setTimestamp((long) (windowStart * 1_000_000));
                    lastDecodedSeconds = -1;
                } else if (lastDecodedSeconds >= seconds) {
                    continue;
                }

                int[] target = null;
                Frame frame;
                while ((frame = grabber.grab()) != null) {
                    if (frame.image == null) continue;
                    lastDecodedSeconds = frame.timestamp / 1_000_000.0;
                    boolean reached = lastDecodedSeconds >= seconds - 1.0e-4;
                    if (reached || backward && lastDecodedSeconds > windowStart - interval) {
                        int[] pixels = copyPixels(frame);
                        cache.put(lastDecodedSeconds, pixels);
                        if (reached) target = pixels;
                    }
                    if (reached) break;
                }
                if (target == null && !cache.isEmpty()) target = cache.lastEntry().getValue();
                if (target != null) publish(target);
                trimCache(seconds, maxFrames);
            } catch (Exception exception) {
                Vector3.LOGGER.warn("Video seek/decode failed for {} at {}s, skipping", file, seconds, exception);
                lastDecodedSeconds = -1;
            } finally {
                seekStartedNanos = 0;
            }
        }
    }

    private boolean serveCached(double seconds, double interval) {
        Map.Entry<Double, int[]> entry = cache.ceilingEntry(seconds - 1.0e-4);
        if (entry == null || entry.getKey() - seconds >= interval) return false;
        publish(entry.getValue());
        return true;
    }

    private void publish(int[] pixels) {
        int[] current = latestFrame.getAndSet(pixels);
        if (current == pixels) return;
        previousPublished = current;
        frameVersion.incrementAndGet();
    }

    private void trimCache(double seconds, int maxFrames) {
        while (cache.size() > maxFrames) {
            int[] evicted = seconds - cache.firstKey() > cache.lastKey() - seconds
                    ? cache.pollFirstEntry().getValue() : cache.pollLastEntry().getValue();
            // The render thread may still be copying the shown or just-replaced frame.
            if (evicted != latestFrame.get() && evicted != previousPublished && spare.size() < 4) spare.push(evicted);
        }
    }

    private int[] copyPixels(Frame frame) {
        int[] pixels = spare.isEmpty() ? new int[width * height] : spare.pop();
        ByteBuffer bytes = ((ByteBuffer) frame.image[0]).duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int copyWidth = Math.min(width, frame.imageWidth), copyHeight = Math.min(height, frame.imageHeight);
        int stride = frame.imageStride;
        if (stride == width * 4 && copyWidth == width) {
            bytes.position(0);
            bytes.asIntBuffer().get(pixels, 0, width * copyHeight);
            return pixels;
        }
        for (int y = 0; y < copyHeight; y++) {
            bytes.position(y * stride);
            IntBuffer row = bytes.asIntBuffer();
            row.get(pixels, y * width, copyWidth);
        }
        return pixels;
    }

    void close() {
        closed = true;
        synchronized (requestLock) { requestLock.notifyAll(); }
    }
}
