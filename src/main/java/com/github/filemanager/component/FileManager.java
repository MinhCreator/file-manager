/*
The MIT License

Copyright (c) 2015-2025 Valentyn Kolesnikov  (https://github.com/javadev/file-manager)
Remodified by (c) 2025 MinhCreator  (https://github.com/MinhCreator/file-manager)
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.github.filemanager.component;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.github.filemanager.operation.BackgroundFileOperations;
import com.github.filemanager.service.FileCache;
import com.github.filemanager.service.PerformanceMonitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.github.filemanager.service.FileCache.*;

/**
 * A basic File Manager. Requires 1.6+ for the Desktop &amp; SwingWorker classes, amongst other
 * minor things.
 *
 * <p>Includes support classes FileTableModel &amp; FileTreeCellRenderer.
 *
 * <p>TODO Bugs
 *
 * <ul>
 *   <li>Still throws occasional AIOOBEs and NPEs, so some update on the EDT must have been missed.
 *   <li>Fix keyboard focus issues - especially when functions like rename/delete etc. are called
 *       that update nodes &amp; file lists.
 *   <li>Needs more testing in general.
 *       <p>TODO Functionality
 *   <li>Implement Read/Write/Execute checkboxes
 *   <li>Implement Copy
 *   <li>Extra prompt for directory delete (camickr suggestion)
 *   <li>Add File/Directory fields to FileTableModel
 *   <li>Double clicking a directory in the table, should update the tree
 *   <li>Move progress bar?
 *   <li>Add other file display modes (besides table) in CardLayout?
 *   <li>Menus + other cruft?
 *   <li>Implement history/back
 *   <li>Allow multiple selection
 *   <li>Add file search
 * </ul>
 */
public class FileManager {

    public FileManager() {
        initComponents();
        initMainFrame();
    }

    private void initMainFrame(){
        SwingUtilities.invokeLater(() -> {
            JFrame MainFrame = new JFrame(APP_TITLE);
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
//                URL urlBig = FileManager.class.getResource("src/main/resources/file-folder-32.png");
//                URL urlSmall = FileManager.class.getResource("src/main/resources/file-folder-16.png");
                MainFrame.setContentPane(InitGui());
                ArrayList<Image> images = new ArrayList<>();
                Image image64 = new ImageIcon("src/main/resources/file-folder-64.png").getImage();
//                images.add(ImageIO.read(urlBig));
//                images.add(ImageIO.read(urlSmall));
                images.add(image64);
                MainFrame.setIconImages(images);

            }catch (Exception weTried) {
                System.out.println("Error: " + weTried.getMessage());
            }
            MainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            MainFrame.pack();
            MainFrame.setLocationByPlatform(true);
            MainFrame.setMinimumSize(MainFrame.getSize());
            MainFrame.setVisible(true);
            showRootFile();
            MainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    shutdown();
                }
            });
        });
    }

    private void initComponents() {
        openFile = new JButton();
        printFile = new JButton();
        editFile = new JButton();
        deleteFile = new JButton();
        newFile = new JButton();
        copyFile = new JButton();
        refreshButton = new JButton();
        performanceMonitor = new JButton();
    }

    private JComponent toolbar(){
        JToolBar toolBar = new JToolBar();
        // mnemonics stop working in a floated toolbar
        toolBar.setFloatable(false);

        openFile = new JButton("Open");
        openFile.setMnemonic('o');

        openFile.addActionListener(
                ae -> {
                    try {
                        desktop.open(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                    gui.repaint();
                });
        toolBar.add(openFile);

        editFile = new JButton("Edit");
        editFile.setMnemonic('e');
        editFile.addActionListener(
                ae -> {
                    try {
                        desktop.edit(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                });
        toolBar.add(editFile);

        printFile = new JButton("Print");
        printFile.setMnemonic('p');
        printFile.addActionListener(
                ae -> {
                    try {
                        desktop.print(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                });
        toolBar.add(printFile);

        // Check the actions are supported on this platform!
        openFile.setEnabled(desktop.isSupported(Desktop.Action.OPEN));
        editFile.setEnabled(desktop.isSupported(Desktop.Action.EDIT));
        printFile.setEnabled(desktop.isSupported(Desktop.Action.PRINT));

        toolBar.addSeparator();

        newFile = new JButton("New");
        newFile.setMnemonic('n');
        newFile.addActionListener(
                ae -> newFile());
        toolBar.add(newFile);

        copyFile = new JButton("Copy");
        copyFile.setMnemonic('c');
        copyFile.addActionListener(
                ae -> copyFile());
        toolBar.add(copyFile);

        JButton renameFile = new JButton("Rename");
        renameFile.setMnemonic('r');
        renameFile.addActionListener(
                ae -> renameFile());
        toolBar.add(renameFile);

        deleteFile = new JButton("Delete");
        deleteFile.setMnemonic('d');
        deleteFile.addActionListener(
                ae -> deleteFile());
        toolBar.add(deleteFile);

        toolBar.addSeparator();

        readable = new JCheckBox("Read  ");
        readable.setMnemonic('a');
        // readable.setEnabled(false);
        toolBar.add(readable);

        writable = new JCheckBox("Write  ");
        writable.setMnemonic('w');
        // writable.setEnabled(false);
        toolBar.add(writable);

        executable = new JCheckBox("Execute");
        executable.setMnemonic('x');
        // executable.setEnabled(false);
        toolBar.add(executable);

        return toolBar;
    }

    private JComponent topPanel(){
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));

        searchField = new JTextField(20);
        searchField.setToolTipText("Fast filter - type to filter current folder files");
        
        // Add real-time search listener for local filtering
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterInCurrPath();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterInCurrPath();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterInCurrPath();
            }
        });
        
        // Add Enter key listener to clear search
        searchField.addActionListener(ae -> clearSearch());
        
        searchButton = new JButton(new FlatSVGIcon("scope.svg"));
        searchButton.setToolTipText("Clear filter");

        searchButton.addActionListener(ae -> {
            if (searchField.getText().isEmpty()) {
                searchField.requestFocus();
            } else {
                clearSearch();
            }
        });
        
        // Global search button
        JButton globalSearchBtn = new JButton("Global Search");
        globalSearchBtn.setToolTipText("Search across all drives/storage");
        globalSearchBtn.setMnemonic('g');
        globalSearchBtn.addActionListener(ae -> new Search().globalSearch());
        
        topPanel.add(new JLabel("Filter: "));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(globalSearchBtn);


        refreshButton = new JButton();
        refreshButton.setIcon(new FlatSVGIcon("reload.svg"));
        refreshButton.setMnemonic('f');
        refreshButton.setToolTipText("Refresh");
        refreshButton.setSize(10,10);
        refreshButton.addActionListener(ae -> refreshCurrent());
        topPanel.add(refreshButton);

        performanceMonitor = new JButton();
        performanceMonitor.setIcon(new FlatSVGIcon("monitor.svg"));
        performanceMonitor.setToolTipText("Performance Monitor");
        performanceMonitor.setSize(10,10);
        performanceMonitor.setMnemonic('m');
        performanceMonitor.addActionListener(ae -> {
            JFrame frame = new JFrame("Performance Monitor");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(400, 300);
            PerformanceMonitor performanceMonitor = new PerformanceMonitor();
            Thread thread = new Thread(performanceMonitor);
            thread.start();
            frame.setContentPane(performanceMonitor);
            frame.setVisible(true);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosed(WindowEvent e) {
                    performanceMonitor.stop();
                }
            });

        });
        topPanel.add(performanceMonitor);
        return topPanel;
    }

    public Container InitGui() throws ExecutionException, InterruptedException {
        if (gui == null) {
            gui = new JPanel(new BorderLayout(3, 3));
            gui.setBorder(new EmptyBorder(5, 5, 5, 5));

            fileSystemView = FileSystemView.getFileSystemView();
            desktop = Desktop.getDesktop();

            JPanel detailView = new JPanel(new BorderLayout(3, 3));

            table = new JTable();
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setAutoCreateRowSorter(false);
            table.setShowVerticalLines(false);

            table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    int column = table.columnAtPoint(e.getPoint());
                    if (column >= 0) {
                        ((FileTableModel) table.getModel()).sortByColumn(column);
                        table.getTableHeader().repaint(); // Refresh header to show sort indicator
                    }
                }
            });

            listSelectionListener =
                    lse -> {
                        int row = table.getSelectionModel().getLeadSelectionIndex();
                        setFileDetails(((FileTableModel) table.getModel()).getFile(row));
                    };
            table.getSelectionModel().addListSelectionListener(listSelectionListener);
            doubleClickToGoToFileOrFolder();
            JScrollPane tableScroll = new JScrollPane(table);
            Dimension d = tableScroll.getPreferredSize();
            tableScroll.setPreferredSize(
                    new Dimension((int) d.getWidth(), (int) d.getHeight() / 2));
            detailView.add(tableScroll, BorderLayout.CENTER);

            // the File tree
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            treeModel = new DefaultTreeModel(root);

            TreeSelectionListener treeSelectionListener =
                    tse -> {
                        DefaultMutableTreeNode node =
                                (DefaultMutableTreeNode) tse.getPath().getLastPathComponent();
                        showChildren(node);
                        setFileDetails((File) node.getUserObject());
                    };

            // show the file system roots.
            File[] roots = fileSystemView.getRoots();
            for (File fileSystemRoot : roots) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(fileSystemRoot);
                root.add(node);
//                 showChildren(node);
//                //
//                File[] files = fileSystemView.getFiles(fileSystemRoot, true);
                File[] files =(File[]) BackgroundFileOperations.loadDirectoryInBackground(fileSystemRoot, progressBar, new BackgroundFileOperations.FileArrayCallback() {
                    @Override
                    public void onFilesLoaded(File[] files) {
                        setTableData(files);
                    }
                }).get();
                for (File file : files) {
                    if (file.isDirectory()) {
                        node.add(new DefaultMutableTreeNode(file));
                    }
                }
            }

            tree = new JTree(treeModel);
            tree.setRootVisible(false);
            tree.addTreeSelectionListener(treeSelectionListener);
            tree.setCellRenderer(new FileTreeCellRenderer());
            tree.expandRow(0);
            JScrollPane treeScroll = new JScrollPane(tree);

            // as per trashgod tip
            tree.setVisibleRowCount(15);

            Dimension preferredSize = treeScroll.getPreferredSize();
            Dimension widePreferred = new Dimension(200, (int) preferredSize.getHeight());
            treeScroll.setPreferredSize(widePreferred);

            // details for a File
            JPanel fileMainDetails = new JPanel(new BorderLayout(4, 2));
            fileMainDetails.setBorder(new EmptyBorder(0, 6, 0, 6));

            JPanel fileDetailsLabels = new JPanel(new GridLayout(0, 1, 2, 2));
            fileMainDetails.add(fileDetailsLabels, BorderLayout.WEST);

            JPanel fileDetailsValues = new JPanel(new GridLayout(0, 1, 2, 2));
            fileMainDetails.add(fileDetailsValues, BorderLayout.CENTER);

            fileDetailsLabels.add(new JLabel("File", JLabel.TRAILING));
            fileName = new JLabel();
            fileDetailsValues.add(fileName);
            fileDetailsLabels.add(new JLabel("Path/name", JLabel.TRAILING));
            path = new JTextField(5);
            path.setEditable(false);
            fileDetailsValues.add(path);
            fileDetailsLabels.add(new JLabel("Last Modified", JLabel.TRAILING));
            date = new JLabel();
            fileDetailsValues.add(date);
            fileDetailsLabels.add(new JLabel("File size", JLabel.TRAILING));
            size = new JLabel();
            fileDetailsValues.add(size);
            fileDetailsLabels.add(new JLabel("Type", JLabel.TRAILING));

            JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
            isDirectory = new JRadioButton("Directory");
            isDirectory.setEnabled(false);
            flags.add(isDirectory);

            isFile = new JRadioButton("File");
            isFile.setEnabled(false);
            flags.add(isFile);
            fileDetailsValues.add(flags);

            int count = fileDetailsLabels.getComponentCount();
            for (int ii = 0; ii < count; ii++) {
                fileDetailsLabels.getComponent(ii).setEnabled(false);
            }
            gui.add(topPanel(), BorderLayout.NORTH);

            JPanel fileView = new JPanel(new BorderLayout(3, 3));

            fileView.add(toolbar(), BorderLayout.NORTH);
            fileView.add(fileMainDetails, BorderLayout.CENTER);

            detailView.add(fileView, BorderLayout.SOUTH);

            JSplitPane splitPane =
                    new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, detailView);
            gui.add(splitPane, BorderLayout.CENTER);

            JPanel simpleOutput = new JPanel(new BorderLayout(3, 3));
            progressBar = new JProgressBar();
            simpleOutput.add(progressBar, BorderLayout.EAST);
            progressBar.setVisible(false);

            gui.add(simpleOutput, BorderLayout.SOUTH);
        }
        return gui;
    }

    public void showRootFile() {
        // ensure the main files are displayed
        tree.setSelectionInterval(0, 0);
    }

    public static TreePath findTreePath(File find) {
        for (int ii = 0; ii < tree.getRowCount(); ii++) {
            TreePath treePath = tree.getPathForRow(ii);
            Object object = treePath.getLastPathComponent();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) object;
            File nodeFile = (File) node.getUserObject();

            if (nodeFile.equals(find)) {
                return treePath;
            }
        }
        // not found!
        return null;
    }

    /**
     * Update the table on the EDT
     */
    private static void setTableData(final File[] files) {
        SwingUtilities.invokeLater(
                () -> {
                    if (fileTableModel == null) {
                        fileTableModel = new FileTableModel();
                        table.setModel(fileTableModel);
                    }
                    table.getSelectionModel()
                            .removeListSelectionListener(listSelectionListener);
                    fileTableModel.setFiles(files);
                    table.getSelectionModel().addListSelectionListener(listSelectionListener);
                    if (!cellSizesSet && files.length > 0) {
                        Icon icon = getCachedIcon(files[0]);

                        // size adjustment to better account for icons
                        table.setRowHeight(icon.getIconHeight() + rowIconPadding);

                        scaleColumnWidth(0, -1);
                        scaleColumnWidth(3, 60);
                        table.getColumnModel().getColumn(3).setMaxWidth(120);
                        scaleColumnWidth(4, -1);
                        scaleColumnWidth(5, -1);
                        scaleColumnWidth(6, -1);
                        scaleColumnWidth(7, -1);
                        scaleColumnWidth(8, -1);
                        scaleColumnWidth(9, -1);

                        cellSizesSet = true;
                    }
                });
    }

    private static void scaleColumnWidth(int column, int width) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (width < 0) {
            // use the preferred width of the header..
            JLabel label = new JLabel((String) tableColumn.getHeaderValue());
            Dimension preferred = label.getPreferredSize();
            // altered 10->14 as per camickr comment.
            width = (int) preferred.getWidth() + 14;
        }
        tableColumn.setPreferredWidth(width);
        tableColumn.setMaxWidth(width);
        tableColumn.setMinWidth(width);
    }

    /**
     * Add the files that are contained within the directory of this node. Thanks to Hovercraft Full
     * Of Eels.
     */
    public static void showChildren(final DefaultMutableTreeNode node) {
        File file = (File) node.getUserObject();
        if (!file.isDirectory()) {
            return; // Early exit for non-directories
        }

        tree.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<Void, File> worker =
                new SwingWorker<Void, File>() {
                    private final boolean cancelled = false;

                    @Override
                    public Void doInBackground() {
                        try {
//                            File[] files = fileSystemView.getFiles(file, true);
                            File[] files =(File[]) BackgroundFileOperations.loadDirectoryInBackground(file, progressBar, new BackgroundFileOperations.FileArrayCallback() {
                                @Override
                                public void onFilesLoaded(File[] files) {
                                    setTableData(files);
                                }
                            }).get();

                            if (cancelled) return null;

                            // Pre-cache metadata and icons for better performance
                            for (File child : files) {
                                if (cancelled) break;
                                getCachedMetadata(child); // Pre-cache metadata
                                getCachedIcon(child);     // Pre-cache icon
                            }

                            if (cancelled) return null;

                            // Only add directory nodes if this is a leaf node
                            if (node.isLeaf()) {
                                for (File child : files) {
                                    if (cancelled) break;
                                    if (child.isDirectory()) {
                                        publish(child);
                                    }
                                }
                            }

                            if (!cancelled) {
                                setTableData(files);
                            }
                        } catch (Exception e) {
                            System.err.println("Error loading directory: " + e.getMessage());
                        }
                        return null;
                    }


                    @Override
                    protected void process(List<File> chunks) {
                        if (cancelled) return;

                        // Batch add nodes to reduce tree model updates
                        for (File child : chunks) {
                            if (cancelled) break;

                            // Check if node already exists to avoid duplicates
                            boolean exists = false;
                            for (int i = 0; i < node.getChildCount(); i++) {
                                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
                                if (childNode.getUserObject().equals(child)) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                node.add(new DefaultMutableTreeNode(child));
                            }
                        }

                        // Only reload if we added new nodes
                        if (!chunks.isEmpty()) {
                            treeModel.reload(node);
                        }
                    }

                    @Override
                    protected void done() {
                        progressBar.setIndeterminate(false);
                        progressBar.setVisible(false);
                        tree.setEnabled(true);
                        try {
                            get();
                        } catch (Exception e) {
                            System.err.println("Error in showChildren: " + e.getMessage());
                        }
                    }
                };
        worker.execute();
    }

    /**
     * Update the File details view with the details of this File.
     */
    public static void setFileDetails(File file) {
        if (file == null) {
            return;
        }
        currentFile = file;
//        Icon icon = getCachedIcon(file);
        fileName.setIcon(getCachedIcon(file));
        fileName.setText(fileSystemView.getSystemDisplayName(file));
        path.setText(file.getPath());

        FileCache.FileMetadata metadata = getCachedMetadata(file);
        date.setText(new Date(metadata.lastModified).toString());
        size.setText(metadata.size + " bytes");
        readable.setSelected(metadata.canRead);
        writable.setSelected(metadata.canWrite);
        executable.setSelected(metadata.canExecute);
        isDirectory.setSelected(metadata.isDirectory);
        isFile.setSelected(metadata.isFile);

        JFrame f = (JFrame) gui.getTopLevelAncestor();
        if (f != null) {
//            f.setTitle(APP_TITLE + " :: " + fileSystemView.getSystemDisplayName(file));
            f.setTitle(APP_TITLE + " :: " + file.getPath());
        }

        gui.repaint();
    }

    private void newFile() {
        if (currentFile == null) {
            showErrorMessage("No location selected for new file.", "Select Location");
            return;
        }

        if (newFilePanel == null) {
            newFilePanel = new JPanel(new BorderLayout(3, 3));

            JPanel southRadio = new JPanel(new GridLayout(1, 0, 2, 2));
            newTypeFile = new JRadioButton("File", true);
            JRadioButton newTypeDirectory = new JRadioButton("Directory");
            ButtonGroup bg = new ButtonGroup();
            bg.add(newTypeFile);
            bg.add(newTypeDirectory);
            southRadio.add(newTypeFile);
            southRadio.add(newTypeDirectory);

            name = new JTextField(15);

            newFilePanel.add(new JLabel("Name"), BorderLayout.WEST);
            newFilePanel.add(name);
            newFilePanel.add(southRadio, BorderLayout.SOUTH);
        }

        int result = JOptionPane.showConfirmDialog(
                gui, newFilePanel, "Create File", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                boolean created;
                File parentFile = currentFile;
                if (!parentFile.isDirectory()) {
                    parentFile = parentFile.getParentFile();
                }
                File file = new File(parentFile, name.getText());
                if (newTypeFile.isSelected()) {
                    created = file.createNewFile();
                } else {
                    created = file.mkdir();
                }
                if (created) {
                    FileCache.clearCache();

                    TreePath parentPath = findTreePath(parentFile);
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                    if (file.isDirectory()) {
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(file);
                        treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());
                    }

                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + file + "' could not be created.";
                    showErrorMessage(msg, "Create Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
    }

    private void copyFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected for copying.", "Select File");
            return;
        }

        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Copy to...");
        fileChooser.setApproveButtonText("Copy");

        // Set initial directory to the current file's parent directory
        File currentDir = currentFile.getParentFile();
        if (currentDir != null) {
            fileChooser.setCurrentDirectory(currentDir);
        }

        // Suggest a default name
        String suggestedName = "Copy of " + currentFile.getName();
        fileChooser.setSelectedFile(new File(currentDir, suggestedName));

        int result = fileChooser.showSaveDialog(gui);
        if (result == JFileChooser.APPROVE_OPTION) {
            File destination = fileChooser.getSelectedFile();

            // Check if destination already exists
            if (destination.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(
                        gui,
                        "File already exists. Do you want to overwrite it?",
                        "File Exists",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // Perform copy in background
            BackgroundFileOperations.copyFileInBackground(currentFile, destination, progressBar, () -> {
                // Clear cache and refresh the parent directory
                FileCache.clearCache();
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                if (parentPath != null) {
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                    showChildren(parentNode);
                    gui.repaint();
                }

                // Show success message
                JOptionPane.showMessageDialog(
                        gui,
                        "File copied successfully!",
                        "Copy Complete",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });
        }
    }

    private void renameFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected to rename.", "Select File");
            return;
        }

        String renameTo = JOptionPane.showInputDialog(gui, "New Name");
        if (renameTo != null) {
            try {
                boolean directory = currentFile.isDirectory();
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                DefaultMutableTreeNode parentNode =
                        (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                boolean renamed =
                        currentFile.renameTo(new File(currentFile.getParentFile(), renameTo));
                if (renamed) {
                    // Clear cache for affected files
                    FileCache.clearCache();
                    if (directory) {
                        // rename the node..

                        // delete the current node..
                        TreePath currentPath = findTreePath(currentFile);
                        System.out.println(currentPath);
                        DefaultMutableTreeNode currentNode =
                                (DefaultMutableTreeNode) currentPath.getLastPathComponent();

                        treeModel.removeNodeFromParent(currentNode);

                        // add a new node..
                    }

                    showChildren(parentNode);
                    gui.repaint();
                } else {
                    String msg = "The file '" + currentFile + "' could not be renamed.";
                    showErrorMessage(msg, "Rename Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
        gui.repaint();
    }

    private void deleteFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected for deletion.", "Select File");
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                gui,
                "Are you sure you want to delete this file?",
                "Delete File",
                JOptionPane.ERROR_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            BackgroundFileOperations.deleteFileInBackground(currentFile, progressBar, () -> {
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                boolean directory = currentFile.isDirectory();
                if (directory) {
                    TreePath currentPath = findTreePath(currentFile);
                    DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
                    treeModel.removeNodeFromParent(currentNode);
                }

                showChildren(parentNode);
                FileCache.clearCache();
                gui.repaint();
            });
        }
    }

    private void refreshCurrent() {
        if (currentFile != null) {
            FileCache.clearCache(); // Clear cache to force refresh

            // Refresh tree node
            TreePath currentPath = findTreePath(currentFile);
            if (currentPath != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
                node.children();
                treeModel.reload(node);
                gui.repaint();
            }

            // Refresh table
            showChildren((DefaultMutableTreeNode) tree.getLastSelectedPathComponent());
        }
    }

    /**
     * Fast search - filter files in real-time as user types.
     * Filters the current directory's files by name (case-insensitive).
     */
    private void filterInCurrPath() {
        if (fileTableModel != null) {
            String filter = searchField.getText();
            fileTableModel.setFilter(filter);
            // Update window title to show filter status
            JFrame f = (JFrame) gui.getTopLevelAncestor();
            if (f != null && currentFile != null) {
                if (filter.isEmpty()) {
                    f.setTitle(APP_TITLE + " :: " + currentFile.getPath());
                } else {
                    f.setTitle(APP_TITLE + " :: " + currentFile.getPath() + " [Filter: " + filter + "]");
                }
            }
        }
    }

    // Double-click to go to file
    private void doubleClickToGoToFileOrFolder() {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        File selectedFile = fileTableModel.getFile(row);
                        if (selectedFile == null) {
                            return;
                        }
                        if (selectedFile.isDirectory()) {
                            // Navigate into directory - expand parents if needed
                            navigateToDirectory(selectedFile);
                        } else {
                            // Open file
                            try {
                                desktop.open(selectedFile);
                            } catch (Exception ex) {
                                showErrorMessage("Cannot open file: " + ex.getMessage(), "Error");
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Navigate to a directory, expanding parent nodes in the tree as needed.
     */
    private void navigateToDirectory(File dir) {
        if (dir == null) return;
        
        // Try to find existing path first
        TreePath path = findTreePath(dir);
        if (path != null) {
            tree.expandPath(path);
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            currentFile = dir;
            showChildren(node);
            setFileDetails(dir);
            gui.repaint();
            return;
        }
        
        // Path not found - need to expand parent first
        File parent = dir.getParentFile();
        if (parent != null) {
            // Recursively navigate to parent first
            TreePath parentPath = buildTreePath(parent);
            if (parentPath != null) {
                tree.expandPath(parentPath);
                tree.setSelectionPath(parentPath);
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                showChildren(parentNode);
                
                // Now try again to find the child
                SwingUtilities.invokeLater(() -> {
                    TreePath childPath = findTreePath(dir);
                    if (childPath != null) {
                        tree.expandPath(childPath);
                        tree.setSelectionPath(childPath);
                        tree.scrollPathToVisible(childPath);
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) childPath.getLastPathComponent();
                        currentFile = dir;
                        showChildren(node);
                        setFileDetails(dir);
                    }
                    gui.repaint();
                });
            }
        }
    }
    
    /**
     * Build a tree path by expanding parent directories recursively.
     */
    private TreePath buildTreePath(File target) {
        // Try to find existing path
        TreePath path = findTreePath(target);
        if (path != null) {
            return path;
        }
        
        // Build path from root
        File[] roots = File.listRoots();
        for (File root : roots) {
            if (target.getPath().startsWith(root.getPath())) {
                // Start from root and build path
                TreePath rootPath = findTreePath(root);
                if (rootPath == null) continue;
                
                String relativePath = target.getPath().substring(root.getPath().length());
                if (relativePath.startsWith("\\") || relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                
                String[] parts = relativePath.split("[\\\\/]");
                TreePath currentPath = rootPath;
                File currentFile = root;
                
                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    currentFile = new File(currentFile, part);
                    
                    // Expand current node to load children
                    tree.expandPath(currentPath);
                    
                    // Look for child node
                    TreePath childPath = findTreePath(currentFile);
                    if (childPath == null) {
                        // Child not loaded yet - need to show children first
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
                        if (node.isLeaf()) {
                            // Node hasn't been expanded, force load
                            showChildren(node);
                            // Try again after loading
                            childPath = findTreePath(currentFile);
                        }
                    }
                    if (childPath == null) {
                        return null; // Could not build full path
                    }
                    currentPath = childPath;
                }
                return currentPath;
            }
        }
        return null;
    }

    /**
     * Clear the search filter and show all files.
     */
    private void clearSearch() {
        searchField.setText("");
        if (fileTableModel != null) {
            fileTableModel.clearFilter();
        }
        // Reset window title
        JFrame f = (JFrame) gui.getTopLevelAncestor();
        if (f != null && currentFile != null) {
            f.setTitle(APP_TITLE + " :: " + currentFile.getPath());
        }
    }

    public static void showErrorMessage(String errorMessage, String errorTitle) {
        JOptionPane.showMessageDialog(gui, errorMessage, errorTitle, JOptionPane.ERROR_MESSAGE);
    }

    private void showThrowable(Throwable t) {
        t.printStackTrace();
        JOptionPane.showMessageDialog(gui, t.toString(), t.getMessage(), JOptionPane.ERROR_MESSAGE);
        gui.repaint();
    }

    /**
     * Shutdown cache cleanup executor.
     */
    public static void shutdown() {
        cacheCleanupExecutor.shutdown();
        BackgroundFileOperations.shutdown();
    }

    /**
     * Title of the application
     */
    public static final String APP_TITLE = "File Explorer";
    /**
     * Used to open/edit/print files.
     */
    public static Desktop desktop;
    /**
     * Provides nice icons and names for files.
     */
    private static javax.swing.filechooser.FileSystemView fileSystemView;

    private JFileChooser fileChooser;

    /**
     * currently selected File.
     */
    private static File currentFile;

    /**
     * Main GUI container
     */
    public static JPanel gui;

    /**
     * File-system tree. Built Lazily
     */
    public static JTree tree;

    private static DefaultTreeModel treeModel;

    /**
     * Directory listing
     */
    public static JTable table;

    private static JProgressBar progressBar;
    /**
     * Table model for File[].
     */
    public static FileTableModel fileTableModel;

    private static ListSelectionListener listSelectionListener;
    private static boolean cellSizesSet = false;
    private static final int rowIconPadding = 6;

    /* File controls. */
    private JButton openFile;
    private JButton printFile;
    private JButton editFile;
    private JButton deleteFile;
    private JButton newFile;
    private JButton copyFile;
    private JButton refreshButton;
    private JButton performanceMonitor;
    /* File details. */
    private static JLabel fileName;
    private static JTextField path;
    private static JLabel date;
    private static JLabel size;
    private static JCheckBox readable;
    private static JCheckBox writable;
    private static JCheckBox executable;
    private static JRadioButton isDirectory;
    private static JRadioButton isFile;

    /* GUI options/containers for new File/Directory creation.  Created lazily. */
    private JPanel newFilePanel;
    private JRadioButton newTypeFile;
    private JTextField name;

    private JTextField searchField;
    private JButton searchButton;


}