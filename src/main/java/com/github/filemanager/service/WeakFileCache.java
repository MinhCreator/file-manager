package com.github.filemanager.service;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Memory-optimized file cache using weak references to prevent memory leaks.
 * Automatically cleans up unused entries when memory is needed.
 */
public class WeakFileCache {

    /**
     * Cache for file system icons using weak references
     */
    private static final Map<String, WeakReference<Icon>> iconCache = new ConcurrentHashMap<>();

    /**
     * Cache for file metadata using weak references
     */
    private static final Map<String, WeakReference<FileCache.FileMetadata>> metadataCache = new ConcurrentHashMap<>();

    /**
     * File system view instance
     */
    private static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

    /**
     * Scheduled executor for cache cleanup
     */
    private static final ScheduledExecutorService cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * Cache statistics
     */
    private static volatile int iconHits = 0;
    private static volatile int iconMisses = 0;
    private static volatile int metadataHits = 0;
    private static volatile int metadataMisses = 0;

    static {
        // Clean up weak references every 2 minutes
        cacheCleanupExecutor.scheduleAtFixedRate(WeakFileCache::cleanupWeakReferences, 2, 2, TimeUnit.MINUTES);
    }

    /**
     * Get cached icon for a file, or retrieve and cache it if not present.
     */
    public static Icon getCachedIcon(File file) {
        String key = file.getAbsolutePath();

        // Check weak reference cache
        WeakReference<Icon> ref = iconCache.get(key);
        Icon icon = (ref != null) ? ref.get() : null;

        if (icon != null) {
            iconHits++;
            return icon;
        }

        iconMisses++;

        // Cache miss - get new icon and store with weak reference
        icon = fileSystemView.getSystemIcon(file);
        if (icon != null) {
            iconCache.put(key, new WeakReference<>(icon));
        }

        return icon;
    }

    /**
     * Get cached metadata for a file, or retrieve and cache it if not present.
     */
    public static FileCache.FileMetadata getCachedMetadata(File file) {
        String key = file.getAbsolutePath();

        // Check weak reference cache
        WeakReference<FileCache.FileMetadata> ref = metadataCache.get(key);
        FileCache.FileMetadata metadata = (ref != null) ? ref.get() : null;

        if (metadata != null) {
            metadataHits++;
            return metadata;
        }

        metadataMisses++;

        // Cache miss - get new metadata and store with weak reference
        metadata = new FileCache.FileMetadata();
        metadata.lastModified = file.lastModified();
        metadata.size = file.length();
        metadata.canRead = file.canRead();
        metadata.canWrite = file.canWrite();
        metadata.canExecute = file.canExecute();
        metadata.isDirectory = file.isDirectory();
        metadata.isFile = file.isFile();

        metadataCache.put(key, new WeakReference<>(metadata));
        return metadata;
    }

    /**
     * Clean up null weak references (entries that have been garbage collected).
     */
    private static void cleanupWeakReferences() {
        // Clean up icon cache
        iconCache.entrySet().removeIf(entry -> entry.getValue().get() == null);

        // Clean up metadata cache
        metadataCache.entrySet().removeIf(entry -> entry.getValue().get() == null);

        // Log statistics periodically
        logCacheStatistics();
    }

    /**
     * Log cache hit/miss statistics.
     */
    private static void logCacheStatistics() {
        int totalIconRequests = iconHits + iconMisses;
        int totalMetadataRequests = metadataHits + metadataMisses;

        if (totalIconRequests > 0) {
            double iconHitRate = (double) iconHits / totalIconRequests * 100;
            System.out.println("Icon Cache Hit Rate: " + String.format("%.1f", iconHitRate) + "%");
        }

        if (totalMetadataRequests > 0) {
            double metadataHitRate = (double) metadataHits / totalMetadataRequests * 100;
            System.out.println("Metadata Cache Hit Rate: " + String.format("%.1f", metadataHitRate) + "%");
        }

        System.out.println("Icon Cache Size: " + iconCache.size());
        System.out.println("Metadata Cache Size: " + metadataCache.size());
    }

    /**
     * Clear all caches manually.
     */
    public static void clearCache() {
        iconCache.clear();
        metadataCache.clear();
        resetStatistics();
    }

    /**
     * Reset cache statistics.
     */
    public static void resetStatistics() {
        iconHits = 0;
        iconMisses = 0;
        metadataHits = 0;
        metadataMisses = 0;
    }

    /**
     * Get cache statistics.
     */
    public static CacheStatistics getStatistics() {
        return new CacheStatistics(iconHits, iconMisses, metadataHits, metadataMisses,
                iconCache.size(), metadataCache.size());
    }

    /**
     * Shutdown the cache cleanup executor.
     */
    public static void shutdown() {
        cacheCleanupExecutor.shutdown();
    }

    /**
     * Data class for cache statistics.
     */
    public static class CacheStatistics {
        public final int iconHits;
        public final int iconMisses;
        public final int metadataHits;
        public final int metadataMisses;
        public final int iconCacheSize;
        public final int metadataCacheSize;

        public CacheStatistics(int iconHits, int iconMisses, int metadataHits, int metadataMisses,
                               int iconCacheSize, int metadataCacheSize) {
            this.iconHits = iconHits;
            this.iconMisses = iconMisses;
            this.metadataHits = metadataHits;
            this.metadataMisses = metadataMisses;
            this.iconCacheSize = iconCacheSize;
            this.metadataCacheSize = metadataCacheSize;
        }

        public double getIconHitRate() {
            int total = iconHits + iconMisses;
            return total > 0 ? (double) iconHits / total * 100 : 0;
        }

        public double getMetadataHitRate() {
            int total = metadataHits + metadataMisses;
            return total > 0 ? (double) metadataHits / total * 100 : 0;
        }

        @Override
        public String toString() {
            return String.format("Cache Statistics [Icon Hit Rate: %.1f%%, Metadata Hit Rate: %.1f%%, " +
                            "Icon Cache Size: %d, Metadata Cache Size: %d]",
                    getIconHitRate(), getMetadataHitRate(), iconCacheSize, metadataCacheSize);
        }
    }
}