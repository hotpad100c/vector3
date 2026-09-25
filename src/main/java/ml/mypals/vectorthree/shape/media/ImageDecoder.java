package ml.mypals.vectorthree.shape.media;

import com.mojang.blaze3d.platform.NativeImage;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class ImageDecoder {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private ImageDecoder() {}

    static NativeImage decode(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (isPng(bytes)) return NativeImage.read(bytes);
        BufferedImage image = null;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException ignored) {
            // CMYK jpegs and the like: ffmpeg gets a try below.
        }
        if (image == null) image = viaFfmpeg(path);
        if (image == null) throw new IOException("Unsupported image format: " + path);
        return toNative(image);
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG.length) return false;
        for (int i = 0; i < PNG.length; i++) if (bytes[i] != PNG[i]) return false;
        return true;
    }

    private static BufferedImage viaFfmpeg(Path path) throws IOException {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            Frame frame = grabber.grabImage();
            BufferedImage image = frame == null ? null : converter.convert(frame);
            grabber.stop();
            return image;
        } catch (Exception exception) {
            throw new IOException("Could not decode image " + path, exception);
        }
    }

    private static NativeImage toNative(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        NativeImage result = new NativeImage(width, height, false);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) result.setPixel(x, y, row[x]);
        }
        return result;
    }
}
