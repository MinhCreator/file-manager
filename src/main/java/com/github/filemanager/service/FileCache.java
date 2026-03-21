package com.github.filemanager.service;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for caching file system icons and metadata to improve performance.
 */
public class FileCache {

    /**
     * File system view instance
     */
    public static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    /**
     * Scheduled executor for cache cleanup
     */
    public static final ScheduledExecutorService cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    /**
     * Cache for file system icons to improve performance
     */
    private static final Map<String, Icon> iconCache = new ConcurrentHashMap<>();
    /**
     * Cache for file metadata to reduce repeated filesystem calls
     */
    private static final Map<String, FileMetadata> metadataCache = new ConcurrentHashMap<>();

    /**
     * Statistics counters for cache performance monitoring
     */
    private static final AtomicLong iconCacheHits = new AtomicLong(0);
    private static final AtomicLong iconCacheMisses = new AtomicLong(0);
    private static final AtomicLong metadataCacheHits = new AtomicLong(0);
    private static final AtomicLong metadataCacheMisses = new AtomicLong(0);
    static {
        // Clean up cache every 5 minutes to prevent memory leaks
        cacheCleanupExecutor.scheduleAtFixedRate(() -> {
            iconCache.clear();
            metadataCache.clear();
        }, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Get cached icon for a file, or retrieve and cache it if not present.
     */
    public static Icon getCachedIcon(File file) {
        if (file == null) {
            return null;
        }
        String key = file.getAbsolutePath();
        Icon cached = iconCache.get(key);
        if (cached != null) {
            iconCacheHits.incrementAndGet();
            return cached;
        }
        iconCacheMisses.incrementAndGet();
        Icon icon = fileSystemView.getSystemIcon(file);
        iconCache.put(key, icon);
        return icon;
    }

    /**
     * Get cached metadata for a file, or retrieve and cache it if not present.
     */
    public static FileMetadata getCachedMetadata(File file) {
        String key = file.getAbsolutePath();
        FileMetadata cached = metadataCache.get(key);
        if (cached != null) {
            metadataCacheHits.incrementAndGet();
            return cached;
        }
        metadataCacheMisses.incrementAndGet();
        FileMetadata metadata = new FileMetadata();
        metadata.name = fileSystemView.getSystemDisplayName(file);
        metadata.path = file.getPath();
        metadata.lastModified = file.lastModified();
        metadata.size = file.length();
        metadata.canRead = file.canRead();
        metadata.canWrite = file.canWrite();
        metadata.canExecute = file.canExecute();
        metadata.isDirectory = file.isDirectory();
        metadata.isFile = file.isFile();
        metadata.fileSystemView = fileSystemView;
        metadataCache.put(key, metadata);
        return metadata;
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
     * Shutdown the cache cleanup executor.
     */
    public static void shutdown() {
        cacheCleanupExecutor.shutdown();
    }

    /**
     * Get cache statistics.
     */
    public static CacheStatistics getStatistics() {
        return new CacheStatistics(
                iconCache.size(),
                iconCacheHits.get(),
                iconCacheMisses.get(),
                metadataCache.size(),
                metadataCacheHits.get(),
                metadataCacheMisses.get()
        );
    }

    /**
     * Reset statistics counters.
     */
    public static void resetStatistics() {
        iconCacheHits.set(0);
        iconCacheMisses.set(0);
        metadataCacheHits.set(0);
        metadataCacheMisses.set(0);
    }

    /**
     * Data class for cache statistics.
     */
    public static class CacheStatistics {
        public final int iconCacheSize;
        public final long iconCacheHits;
        public final long iconCacheMisses;
        public final int metadataCacheSize;
        public final long metadataCacheHits;
        public final long metadataCacheMisses;

        public CacheStatistics(int iconCacheSize, long iconCacheHits, long iconCacheMisses,
                             int metadataCacheSize, long metadataCacheHits, long metadataCacheMisses) {
            this.iconCacheSize = iconCacheSize;
            this.iconCacheHits = iconCacheHits;
            this.iconCacheMisses = iconCacheMisses;
            this.metadataCacheSize = metadataCacheSize;
            this.metadataCacheHits = metadataCacheHits;
            this.metadataCacheMisses = metadataCacheMisses;
        }

        public double getIconHitRate() {
            long total = iconCacheHits + iconCacheMisses;
            return total == 0 ? 0.0 : (double) iconCacheHits / total * 100;
        }

        public double getMetadataHitRate() {
            long total = metadataCacheHits + metadataCacheMisses;
            return total == 0 ? 0.0 : (double) metadataCacheHits / total * 100;
        }

        @Override
        public String toString() {
            return String.format(
                "FileCache Statistics:\n" +
                "  Icon Cache: %d entries, %d hits, %d misses (%.1f%% hit rate)\n" +
                "  Metadata Cache: %d entries, %d hits, %d misses (%.1f%% hit rate)",
                iconCacheSize, iconCacheHits, iconCacheMisses, getIconHitRate(),
                metadataCacheSize, metadataCacheHits, metadataCacheMisses, getMetadataHitRate()
            );
        }
    }

    /**
     * Data class for holding file metadata.
     */
    public static class FileMetadata {
        public long lastModified;
        public String name;
        public String path;
        public long size;
        public boolean canRead;
        public boolean canWrite;
        public boolean canExecute;
        public boolean isDirectory;
        public boolean isFile;
        public FileSystemView fileSystemView = FileSystemView.getFileSystemView();

    }


}