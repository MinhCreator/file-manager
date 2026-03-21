package com.github.filemanager.operation;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Utility class for handling background file operations with progress tracking.
 */
public class BackgroundFileOperations {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    // basic Copy file function
    public static boolean copyFile(File from, File to) throws IOException {

        boolean created = to.createNewFile();

        if (created) {
            FileChannel fromChannel = null;
            FileChannel toChannel = null;
            try {
                fromChannel = new FileInputStream(from).getChannel();
                toChannel = new FileOutputStream(to).getChannel();

                toChannel.transferFrom(fromChannel, 0, fromChannel.size());

                // set the flags of the to the same as the from
                to.setReadable(from.canRead());
                to.setWritable(from.canWrite());
                to.setExecutable(from.canExecute());
            } finally {
                if (fromChannel != null) {
                    fromChannel.close();
                }
                if (toChannel != null) {
                    toChannel.close();
                }
                return false;
            }
        }
        return created;
    }

    /**
     * Copy file in background with progress tracking.
     */
    public static Future<?> copyFileInBackground(File source, File destination,
                                                 JProgressBar progressBar, Runnable onComplete) {
        return executorService.submit(() -> {
            try {
                if (progressBar != null) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setVisible(true);
                        progressBar.setIndeterminate(true);
                    });
                }

                boolean success = copyFile(source, destination);

                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

                return success;
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                    JOptionPane.showMessageDialog(null,
                            "Error copying file: " + e.getMessage(),
                            "Copy Error",
                            JOptionPane.ERROR_MESSAGE);
                });
                return false;
            }
        });
    }

    /**
     * Delete file in background with progress tracking.
     */
    public static Future<?> deleteFileInBackground(File file,
                                                   JProgressBar progressBar, Runnable onComplete) {
        return executorService.submit(() -> {
            try {
                if (progressBar != null) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setVisible(true);
                        progressBar.setIndeterminate(true);
                    });
                }

                boolean success = org.apache.commons.io.FileUtils.deleteQuietly(file);

                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

                return success;
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                    JOptionPane.showMessageDialog(null,
                            "Error deleting file: " + e.getMessage(),
                            "Delete Error",
                            JOptionPane.ERROR_MESSAGE);
                });
                return false;
            }
        });
    }

    /**
     * Load directory contents in background.
     */
    public static Future<?> loadDirectoryInBackground(File directory,
                                                      JProgressBar progressBar,
                                                      FileArrayCallback callback) {
        return executorService.submit(() -> {
            try {
                if (progressBar != null) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setVisible(true);
                        progressBar.setIndeterminate(true);
                    });
                }

                File[] files = javax.swing.filechooser.FileSystemView.getFileSystemView()
                        .getFiles(directory, true);

                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                    if (callback != null) {
                        callback.onFilesLoaded(files);
                    }
                });

                return files;
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (progressBar != null) {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                    JOptionPane.showMessageDialog(null,
                            "Error loading directory: " + e.getMessage(),
                            "Load Error",
                            JOptionPane.ERROR_MESSAGE);
                });
                return null;
            }
        });
    }

    /**
     * Shutdown the executor service.
     */
    public static void shutdown() {
        executorService.shutdown();
    }

    /**
     * Callback interface for file array loading.
     */
    public interface FileArrayCallback {
        void onFilesLoaded(File[] files);
    }
}