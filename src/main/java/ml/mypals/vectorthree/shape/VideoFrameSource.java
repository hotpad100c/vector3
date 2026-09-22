package ml.mypals.vectorthree.shape;

import com.mojang.blaze3d.platform.NativeImage;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens and decodes a video file on a dedicated background thread. Opening (format probing,
 * codec setup) and per-frame decode/convert are both too slow to run on the render thread without
 * stalling the game, so the render thread only ever does a cheap {@link #requestSeconds} write and
 * {@link #pollFrame} check; it never blocks on ffmpeg.
 */
final class VideoFrameSource {
    private static final double SEEK_THRESHOLD_SECONDS = 1.0;

    private final Thread thread;
    private final Object requestLock = new Object();
    private volatile boolean closed;
    private volatile double requestedSeconds;

    private volatile boolean ready;
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
            grabber.start();
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not open video file {}", file, exception);
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

    private void decodeUntilClosed(FFmpegFrameGrabber grabber, String file) throws InterruptedException {
        Java2DFrameConverter converter = new Java2DFrameConverter();
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

            // A single bad seek/grab/convert (edge-of-file timestamps, a transient decoder hiccup,
            // scrubbing rapidly back and forth) must not kill this thread — that would freeze the
            // shape on whatever frame was last decoded, forever, with no way to recover. Isolate each
            // request so a failure just skips that one frame and the loop keeps serving new requests.
            try {
                boolean farJump = lastDecodedSeconds < 0
                        || seconds < lastDecodedSeconds
                        || seconds - lastDecodedSeconds > SEEK_THRESHOLD_SECONDS;
                if (farJump) {
                    grabber.setTimestamp((long) (seconds * 1_000_000));
                    lastDecodedSeconds = -1;
                } else if (lastDecodedSeconds >= seconds) {
                    continue;
                }

                Frame chosen = null;
                Frame frame;
                while ((frame = grabber.grab()) != null) {
                    if (frame.image == null) continue;
                    chosen = frame;
                    lastDecodedSeconds = frame.timestamp / 1_000_000.0;
                    if (lastDecodedSeconds >= seconds) break;
                }
                if (chosen == null) continue;

                BufferedImage image = converter.getBufferedImage(chosen);
                latestFrame.set(toAbgrPixels(image));
                frameVersion.incrementAndGet();
            } catch (Exception exception) {
                Vector3.LOGGER.warn("Video seek/decode failed for {} at {}s, skipping", file, seconds, exception);
                lastDecodedSeconds = -1;
            }
        }
    }

    private int[] toAbgrPixels(BufferedImage image) {
        int copyWidth = Math.min(width, image.getWidth());
        int copyHeight = Math.min(height, image.getHeight());
        int[] pixels = new int[width * height];
        int[] row = new int[copyWidth];
        for (int y = 0; y < copyHeight; y++) {
            image.getRGB(0, y, copyWidth, 1, row, 0, copyWidth);
            int base = y * width;
            for (int x = 0; x < copyWidth; x++) {
                int argb = row[x];
                pixels[base + x] = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
            }
        }
        return pixels;
    }

    void close() {
        closed = true;
        synchronized (requestLock) { requestLock.notifyAll(); }
    }
}
