package com.github.filemanager.service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.DecimalFormat;

/**
 * Performance monitoring panel for the file manager.
 * Shows memory usage, cache statistics, and performance metrics.
 */
public class PerformanceMonitor extends JPanel implements Runnable {

    private static final DecimalFormat df = new DecimalFormat("#0.0");

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final Timer updateTimer;

    private JLabel totalMemoryLabel;
    private JLabel usedMemoryLabel;
    private JLabel freeMemoryLabel;
    private JLabel cacheStatsLabel;
    private JTextArea performanceLog;

    public PerformanceMonitor() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Performance Monitor"));

        initComponents();

        // Update every 2 seconds
        updateTimer = new Timer(2000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMemoryInfo();
                updateCacheStatistics();
            }
        });
        updateTimer.start();
    }

    private void initComponents() {
        // Memory panel
        JPanel memoryPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        memoryPanel.setBorder(BorderFactory.createTitledBorder("Memory Usage"));

        totalMemoryLabel = new JLabel();
        usedMemoryLabel = new JLabel();
        freeMemoryLabel = new JLabel();

        memoryPanel.add(new JLabel("Total Memory:"));
        memoryPanel.add(totalMemoryLabel);
        memoryPanel.add(new JLabel("Used Memory:"));
        memoryPanel.add(usedMemoryLabel);
        memoryPanel.add(new JLabel("Free Memory:"));
        memoryPanel.add(freeMemoryLabel);

        // Cache panel
        JPanel cachePanel = new JPanel(new BorderLayout());
        cachePanel.setBorder(BorderFactory.createTitledBorder("Cache Statistics"));

        cacheStatsLabel = new JLabel();
        cacheStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        cachePanel.add(cacheStatsLabel, BorderLayout.CENTER);

        // Performance log
        performanceLog = new JTextArea(8, 40);
        performanceLog.setEditable(false);
        performanceLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScrollPane = new JScrollPane(performanceLog);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Performance Log"));

        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout());

        JButton clearCacheButton = new JButton("Clear All Caches");
        clearCacheButton.addActionListener(e -> {
            FileCache.clearCache();
            ThumbnailCache.clearCache();
            WeakFileCache.clearCache();
            logMessage("All caches cleared");
        });

        JButton gcButton = new JButton("Run Garbage Collection");
        gcButton.addActionListener(e -> {
            System.gc();
            logMessage("Garbage collection requested");
        });

        JButton showStatsButton = new JButton("Show Detailed Stats");
        showStatsButton.addActionListener(e -> {
            WeakFileCache.CacheStatistics stats = WeakFileCache.getStatistics();
            JOptionPane.showMessageDialog(this, stats.toString(), "Cache Statistics",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        JButton showFileCacheStatsButton = new JButton("Show FileCache Stats");
        showFileCacheStatsButton.addActionListener(e -> {
            FileCache.CacheStatistics stats = FileCache.getStatistics();
            JOptionPane.showMessageDialog(this, stats.toString(), "FileCache Statistics",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        controlPanel.add(clearCacheButton);
        controlPanel.add(gcButton);
        controlPanel.add(showStatsButton);
        controlPanel.add(showFileCacheStatsButton);

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(memoryPanel, BorderLayout.WEST);
        topPanel.add(cachePanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(logScrollPane, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        // Initial update
        updateMemoryInfo();
        updateCacheStatistics();
    }

    private void updateMemoryInfo() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long total = heapUsage.getMax();
        long used = heapUsage.getUsed();
        long free = total - used;

        totalMemoryLabel.setText(formatBytes(total));
        usedMemoryLabel.setText(formatBytes(used));
        freeMemoryLabel.setText(formatBytes(free));

        // Update colors based on memory usage
        double usagePercent = (double) used / total * 100;
        if (usagePercent > 80) {
            usedMemoryLabel.setForeground(Color.RED);
        } else if (usagePercent > 60) {
            usedMemoryLabel.setForeground(Color.ORANGE);
        } else {
            usedMemoryLabel.setForeground(Color.BLACK);
        }
    }

    private void updateCacheStatistics() {
        FileCache.CacheStatistics fileCacheStats = FileCache.getStatistics();
        WeakFileCache.CacheStatistics weakCacheStats = WeakFileCache.getStatistics();

        String statsText = String.format(
                "<html>" +
                "<b>FileCache:</b> %d icons (%.1f%% hits), %d metadata (%.1f%% hits)<br>" +
                "<b>WeakFileCache:</b> %d icons (%.1f%% hits), %d metadata (%.1f%% hits)",
                fileCacheStats.iconCacheSize, fileCacheStats.getIconHitRate(),
                fileCacheStats.metadataCacheSize, fileCacheStats.getMetadataHitRate(),
                weakCacheStats.iconCacheSize, weakCacheStats.getIconHitRate(),
                weakCacheStats.metadataCacheSize, weakCacheStats.getMetadataHitRate()
        );

        cacheStatsLabel.setText(statsText);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return df.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return df.format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return df.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    public void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            performanceLog.insert(String.format("[%s] %s%n", timestamp, message), 0);

            // Limit log size
            String text = performanceLog.getText();
            if (text.length() > 5000) {
                performanceLog.setText(text.substring(0, 5000));
            }
        });
    }

    public void stopMonitoring() {
        updateTimer.stop();
    }

    @Override
    public void run() {
        new PerformanceMonitor();
        System.out.println("Performance monitor started");
    }

    public void stop() {
        stopMonitoring();
        System.out.println("Performance monitor stopped");

    }

}