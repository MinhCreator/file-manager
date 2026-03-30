package com.github.filemanager.component;

import com.github.filemanager.service.FileCache;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.List;

import static com.github.filemanager.component.FileManager.*;
import static com.github.filemanager.service.FileCache.getCachedIcon;

public class Search {
    private final JFrame JFrame = new JFrame();

    /**
     * Global search across all drives/storage.
     * Opens a dialog to search for files by name across the entire file system.
     */
    public void globalSearch() {
        // Create search dialog
        JDialog searchDialog = new JDialog(JFrame, "Global Search", true);
        searchDialog.setSize(600, 500);
        searchDialog.setLocationRelativeTo(JFrame);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Search input panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        JTextField globalSearchField = new JTextField(30);
        globalSearchField.setToolTipText("Enter filename to search across all drives");
        JButton searchBtn = new JButton("Search");
        JButton stopBtn = new JButton("Stop");
        stopBtn.setEnabled(false);

        inputPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        inputPanel.add(globalSearchField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnPanel.add(searchBtn);
        btnPanel.add(stopBtn);
        inputPanel.add(btnPanel, BorderLayout.EAST);

        mainPanel.add(inputPanel, BorderLayout.NORTH);

        // Results list
        DefaultListModel<File> resultsModel = new DefaultListModel<>();
        JList<File> resultsList = new JList<>(resultsModel);
        resultsList.setCellRenderer(new FileListCellRenderer());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(resultsList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        JLabel statusLabel = new JLabel("Ready");
        JProgressBar searchProgress = new JProgressBar();
        searchProgress.setIndeterminate(true);
        searchProgress.setVisible(false);

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(searchProgress, BorderLayout.EAST);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        // Button panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton goToBtn = new JButton("Go to File");
        JButton openBtn = new JButton("Open");
        JButton closeBtn = new JButton("Close");

        goToBtn.setEnabled(false);
        openBtn.setEnabled(false);

        bottomPanel.add(goToBtn);
        bottomPanel.add(openBtn);
        bottomPanel.add(closeBtn);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Wrap status and buttons
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(statusPanel, BorderLayout.NORTH);
        southPanel.add(bottomPanel, BorderLayout.SOUTH);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        // Search worker reference for stopping
        final GlobalSearchWorker[] currentWorker = new GlobalSearchWorker[1];

        // Search button action
        searchBtn.addActionListener(e -> {
            String query = globalSearchField.getText().trim();
            if (query.isEmpty()) {
                JOptionPane.showMessageDialog(searchDialog, "Please enter a search term", "Empty Search", JOptionPane.WARNING_MESSAGE);
                return;
            }

            resultsModel.clear();
            statusLabel.setText("Searching...");
            searchProgress.setVisible(true);
            searchBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            goToBtn.setEnabled(false);
            openBtn.setEnabled(false);

            currentWorker[0] = new GlobalSearchWorker(query, resultsModel, statusLabel, searchProgress, searchBtn, stopBtn);
            currentWorker[0].execute();
        });

        // Stop button action
        stopBtn.addActionListener(e -> {
            if (currentWorker[0] != null) {
                currentWorker[0].cancel(true);
            }
        });

        // List selection listener
        resultsList.addListSelectionListener(e -> {
            boolean hasSelection = resultsList.getSelectedValue() != null;
            goToBtn.setEnabled(hasSelection);
            openBtn.setEnabled(hasSelection);
        });

        // Double-click to go to file
        resultsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    File selected = resultsList.getSelectedValue();
                    if (selected != null) {
                        navigateToFile(selected);
                        searchDialog.dispose();
                    }
                }
            }
        });

        // Go to button action
        goToBtn.addActionListener(e -> {
            File selected = resultsList.getSelectedValue();
            if (selected != null) {
                navigateToFile(selected);
                searchDialog.dispose();
            }
        });

        // Open button action
        openBtn.addActionListener(e -> {
            File selected = resultsList.getSelectedValue();
            if (selected != null) {
                try {
                    FileManager.desktop.open(selected);
                } catch (Exception ex) {
                    showErrorMessage("Cannot open file: " + ex.getMessage(), "Error");
                }
            }
        });

        // Close button action
        closeBtn.addActionListener(e -> {
            if (currentWorker[0] != null) {
                currentWorker[0].cancel(true);
            }
            searchDialog.dispose();
        });

        // Enter key in search field triggers search
        globalSearchField.addActionListener(e -> searchBtn.doClick());

        searchDialog.setContentPane(mainPanel);
        searchDialog.setVisible(true);
    }

    /**
     * Navigate to a file in the tree and select it.
     */
    public void navigateToFile(File file) {
        if (file == null || !file.exists()) {
            showErrorMessage("File does not exist", "Navigation Error");
            return;
        }

        // Get the parent directory path
        File parent = file.getParentFile();
        if (parent == null) {
            parent = file; // For root directories
        }

        // Try to find and expand the tree to show this file
        TreePath path = FileManager.findTreePath(parent);
        if (path != null) {
            tree.expandPath(path);
            tree.setSelectionPath(path);

            // Show children of the parent directory
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            FileManager.showChildren(node);

            // Select the file in the table if it's visible
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < fileTableModel.getRowCount(); i++) {
                    File tableFile = fileTableModel.getFile(i);
                    if (tableFile != null && tableFile.equals(file)) {
                        table.setRowSelectionInterval(i, i);
                        table.scrollRectToVisible(table.getCellRect(i, 0, true));
                        setFileDetails(file);
                        break;
                    }
                }
            });
        } else {
            // If path not found in tree, at least show the directory contents
            FileCache.clearCache();
            showErrorMessage("File found but path not available in tree: " + file.getPath(), "Navigation");
        }
    }

    /**
     * SwingWorker to search for files across all drives.
     */
    private class GlobalSearchWorker extends SwingWorker<Void, File> {
        private final String query;
        private final DefaultListModel<File> resultsModel;
        private final JLabel statusLabel;
        private final JProgressBar progressBar;
        private final JButton searchBtn;
        private final JButton stopBtn;
        private final String queryLower;
        private volatile int foundCount = 0;
        private volatile int searchedDirs = 0;

        GlobalSearchWorker(String query, DefaultListModel<File> resultsModel, JLabel statusLabel,
                           JProgressBar progressBar, JButton searchBtn, JButton stopBtn) {
            this.query = query;
            this.queryLower = query.toLowerCase();
            this.resultsModel = resultsModel;
            this.statusLabel = statusLabel;
            this.progressBar = progressBar;
            this.searchBtn = searchBtn;
            this.stopBtn = stopBtn;
        }

        @Override
        protected Void doInBackground() {
            // Get all roots (drives)
            File[] roots = File.listRoots();

            for (File root : roots) {
                if (isCancelled()) break;
                try {
                    searchDirectory(root);
                } catch (Exception e) {
                    // Skip directories we can't access
                }
            }

            return null;
        }

        private void searchDirectory(File dir) {
            if (isCancelled()) return;

            try {
                File[] files = dir.listFiles();
                if (files == null) return;

                searchedDirs++;
                if (searchedDirs % 100 == 0) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Searched " + searchedDirs + " directories, found " + foundCount + " matches"));
                }

                for (File file : files) {
                    if (isCancelled()) return;

                    // Check if filename matches
                    if (file.getName().toLowerCase().contains(queryLower)) {
                        foundCount++;
                        publish(file);
                    }

                    // Recurse into subdirectories (limit depth for performance)
                    if (file.isDirectory() && searchedDirs < 10000) {
                        searchDirectory(file);
                    }
                }
            } catch (Exception e) {
                // Skip files/directories we can't access
            }
        }

        @Override
        protected void process(List<File> chunks) {
            for (File file : chunks) {
                resultsModel.addElement(file);
            }
            statusLabel.setText("Found " + foundCount + " matches in " + searchedDirs + " directories");
        }

        @Override
        protected void done() {
            progressBar.setVisible(false);
            searchBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            if (isCancelled()) {
                statusLabel.setText("Search stopped. Found " + foundCount + " matches.");
            } else {
                statusLabel.setText("Search complete. Found " + foundCount + " matches in " + searchedDirs + " directories.");
            }
        }
    }

    /**
     * Custom cell renderer for file list in search results.
     */
    private class FileListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof File) {
                File file = (File) value;
                setText(file.getName() + "  (" + file.getParent() + ")");
                setToolTipText(file.getPath());
                setIcon(getCachedIcon(file));
            }
            return this;
        }
    }

}