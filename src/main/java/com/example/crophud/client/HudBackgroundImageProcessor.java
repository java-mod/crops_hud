package com.example.crophud.client;

import com.example.crophud.CropHudMod;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts a user-picked image into {@code config/crophud/background.png}, resizing it
 * to "cover" the HUD card's aspect ratio (uniform scale + center crop) so it fills the
 * card without stretch distortion, then downsamples in halving steps (rather than one
 * big bicubic pass) to avoid the aliasing a single-step resize produces on large photos.
 */
final class HudBackgroundImageProcessor {
    private static final Path OUTPUT_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve(CropHudMod.MOD_ID).resolve("background.png");

    /** Render at this multiple of the card's pixel size so the result stays crisp under GUI scaling. */
    private static final int QUALITY_SCALE = 3;

    private HudBackgroundImageProcessor() {
    }

    /**
     * Reads {@code source}, resizes it to cover a box shaped like (cardWidth, cardHeight),
     * and writes the result to config/crophud/background.png.
     *
     * @return {@code null} on success, or a short error description on failure.
     */
    static String process(Path source, int cardWidth, int cardHeight) {
        BufferedImage original;
        try {
            original = ImageIO.read(source.toFile());
        } catch (IOException e) {
            CropHudMod.LOGGER.error("Failed to read image {}", source, e);
            return e.getMessage();
        }
        if (original == null) {
            return "unsupported image format";
        }

        int targetW = Math.max(1, cardWidth  * QUALITY_SCALE);
        int targetH = Math.max(1, cardHeight * QUALITY_SCALE);
        BufferedImage covered = coverResize(original, targetW, targetH);

        try {
            Files.createDirectories(OUTPUT_PATH.getParent());
            ImageIO.write(covered, "png", OUTPUT_PATH.toFile());
            return null;
        } catch (IOException e) {
            CropHudMod.LOGGER.error("Failed to write custom HUD background image", e);
            return e.getMessage();
        }
    }

    /** Uniformly scales {@code src} so it fully covers (targetW, targetH), then center-crops to that exact size. */
    private static BufferedImage coverResize(BufferedImage src, int targetW, int targetH) {
        double scale = Math.max((double) targetW / src.getWidth(), (double) targetH / src.getHeight());
        int scaledW = Math.max(targetW, (int) Math.round(src.getWidth()  * scale));
        int scaledH = Math.max(targetH, (int) Math.round(src.getHeight() * scale));

        BufferedImage scaled = progressiveScale(src, scaledW, scaledH);

        int cropX = (scaled.getWidth()  - targetW) / 2;
        int cropY = (scaled.getHeight() - targetH) / 2;

        BufferedImage cropped = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cropped.createGraphics();
        try {
            g.drawImage(scaled, -cropX, -cropY, null);
        } finally {
            g.dispose();
        }
        return cropped;
    }

    /** Downscales in halving steps (each a high-quality pass) instead of one large single-step resize. */
    private static BufferedImage progressiveScale(BufferedImage src, int targetW, int targetH) {
        BufferedImage current = src;
        int w = src.getWidth();
        int h = src.getHeight();
        while (w > targetW * 2 && h > targetH * 2) {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            current = scaleStep(current, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            current = scaleStep(current, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return current;
    }

    private static BufferedImage scaleStep(BufferedImage src, int w, int h, Object interpolationHint) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
