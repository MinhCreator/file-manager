package com.github.filemanager.service;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background thumbnail loader and cache for image files.
 * Generates and caches thumbnails asynchronously to improve UI performance.
 */
public class ThumbnailCache {

    private static final int THUMBNAIL_SIZE = 64;
    private static final int MAX_CACHE_SIZE = 500;

    private static final Map<String, ImageIcon> thumbnailCache = new ConcurrentHashMap<>();
    private static final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);

    // Supported image formats
    private static final String[] IMAGE_EXTENSIONS = {
            "jpg", "jpeg", "png", "gif", "bmp", "wbmp", "tiff", "tif"
    };

    /**
     * Check if file is an image.
     */
    public static boolean isImageFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (name.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get thumbnail for image file. Returns placeholder immediately, loads actual thumbnail in background.
     */
    public static ImageIcon getThumbnail(File file) {
        if (!isImageFile(file)) {
            return null;
        }

        String key = file.getAbsolutePath();

        // Return cached thumbnail if available
        ImageIcon cached = thumbnailCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Return default icon and load actual thumbnail in background
        ImageIcon defaultIcon = createDefaultThumbnail();

        // Load thumbnail in background
        thumbnailExecutor.submit(() -> {
            try {
                ImageIcon thumbnail = createThumbnail(file);
                if (thumbnail != null) {
                    thumbnailCache.put(key, thumbnail);

                    // Limit cache size
                    if (thumbnailCache.size() > MAX_CACHE_SIZE) {
                        thumbnailCache.clear(); // Simple cleanup strategy
                    }
                }
            } catch (Exception e) {
                // Log error but don't break UI
                System.err.println("Error loading thumbnail for " + file.getAbsolutePath() + ": " + e.getMessage());
            }
        });

        return defaultIcon;
    }

    /**
     * Create thumbnail from image file.
     */
    private static ImageIcon createThumbnail(File file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file);
        if (originalImage == null) {
            return null;
        }

        // Calculate scaled dimensions
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        if (originalWidth <= THUMBNAIL_SIZE && originalHeight <= THUMBNAIL_SIZE) {
            return new ImageIcon(originalImage);
        }

        double scale = Math.min(
                (double) THUMBNAIL_SIZE / originalWidth,
                (double) THUMBNAIL_SIZE / originalHeight
        );

        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);

        // Create scaled image
        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();

        // Use high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return new ImageIcon(scaledImage);
    }

    /**
     * Create default thumbnail placeholder.
     */
    private static ImageIcon createDefaultThumbnail() {
        BufferedImage placeholder = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = placeholder.createGraphics();

        // Draw placeholder
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);

        g2d.setColor(Color.GRAY);
        g2d.drawRect(0, 0, THUMBNAIL_SIZE - 1, THUMBNAIL_SIZE - 1);

        // Draw simple image icon
        g2d.setColor(Color.DARK_GRAY);
        int[] xPoints = {THUMBNAIL_SIZE / 4, THUMBNAIL_SIZE * 3 / 4, THUMBNAIL_SIZE * 3 / 4, THUMBNAIL_SIZE / 4};
        int[] yPoints = {THUMBNAIL_SIZE / 4, THUMBNAIL_SIZE / 4, THUMBNAIL_SIZE * 3 / 4, THUMBNAIL_SIZE * 3 / 4};
        g2d.drawPolygon(xPoints, yPoints, 4);

        g2d.dispose();

        return new ImageIcon(placeholder);
    }

    /**
     * Clear thumbnail cache.
     */
    public static void clearCache() {
        thumbnailCache.clear();
    }

    /**
     * Shutdown thumbnail executor.
     */
    public static void shutdown() {
        thumbnailExecutor.shutdown();
    }
}