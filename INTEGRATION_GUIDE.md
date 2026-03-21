# How to Integrate Performance Improvements into FileManager.java

This guide shows you exactly how to apply the performance optimizations to your existing `FileManager.java` file.

## Step 1: Add Required Imports

Add these imports after the existing imports section (around line 44):

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
```

## Step 2: Add Cache Variables

Add these cache variables after the existing field declarations (around line 118):

```java
/** Cache for file system icons to improve performance */
private static final Map<String, Icon> iconCache = new ConcurrentHashMap<>();

/** Cache for file metadata to reduce repeated filesystem calls */
private static final Map<String, FileMetadata> metadataCache = new ConcurrentHashMap<>();

/** Scheduled executor for cache cleanup */
private static final ScheduledExecutorService cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor();

static {
    // Clean up cache every 5 minutes to prevent memory leaks
    cacheCleanupExecutor.scheduleAtFixedRate(() -> {
        iconCache.clear();
        metadataCache.clear();
    }, 5, 5, TimeUnit.MINUTES);
}
```

## Step 3: Add FileMetadata Class

Add this inner class at the end of the FileManager class (before the FileTableModel class):

```java
/**
 * Data class for holding file metadata.
 */
static class FileMetadata {
    public long lastModified;
    public long size;
    public boolean canRead;
    public boolean canWrite;
    public boolean canExecute;
    public boolean isDirectory;
    public boolean isFile;
}
```

## Step 4: Add Cache Helper Methods

Add these methods after the existing utility methods (around line 600):

```java
/**
 * Get cached icon for a file, or retrieve and cache it if not present.
 */
private Icon getCachedIcon(File file) {
    String key = file.getAbsolutePath();
    return iconCache.computeIfAbsent(key, k -> fileSystemView.getSystemIcon(file));
}

/**
 * Get cached metadata for a file, or retrieve and cache it if not present.
 */
private FileMetadata getCachedMetadata(File file) {
    String key = file.getAbsolutePath();
    return metadataCache.computeIfAbsent(key, k -> {
        FileMetadata metadata = new FileMetadata();
        metadata.lastModified = file.lastModified();
        metadata.size = file.length();
        metadata.canRead = file.canRead();
        metadata.canWrite = file.canWrite();
        metadata.canExecute = file.canExecute();
        metadata.isDirectory = file.isDirectory();
        metadata.isFile = file.isFile();
        return metadata;
    });
}
```

## Step 5: Update setFileDetails Method

Replace the existing `setFileDetails` method (around line 696) with this optimized version:

```java
/** Update the File details view with the details of this File. */
private void setFileDetails(File file) {
    currentFile = file;
    Icon icon = getCachedIcon(file);
    fileName.setIcon(icon);
    fileName.setText(fileSystemView.getSystemDisplayName(file));
    path.setText(file.getPath());
    
    FileMetadata metadata = getCachedMetadata(file);
    date.setText(new Date(metadata.lastModified).toString());
    size.setText(metadata.size + " bytes");
    readable.setSelected(metadata.canRead);
    writable.setSelected(metadata.canWrite);
    executable.setSelected(metadata.canExecute);
    isDirectory.setSelected(metadata.isDirectory);
    isFile.setSelected(metadata.isFile);

    JFrame f = (JFrame) gui.getTopLevelAncestor();
    if (f != null) {
        f.setTitle(APP_TITLE + " :: " + fileSystemView.getSystemDisplayName(file));
    }

    gui.repaint();
}
```

## Step 6: Update FileTableModel Class

Replace the existing `FileTableModel` class (around line 788) with this optimized version:

```java
/** A TableModel to hold File[]. */
class FileTableModel extends AbstractTableModel {

    private File[] files;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private String[] columns = {
        "Icon", "File", "Path/name", "Size", "Last Modified", "R", "W", "E", "D", "F",
    };

    FileTableModel() {
        this(new File[0]);
    }

    FileTableModel(File[] files) {
        this.files = files;
    }

    @Override
    public Object getValueAt(int row, int column) {
        File file = files[row];
        switch (column) {
            case 0:
                return getCachedIcon(file);
            case 1:
                return fileSystemView.getSystemDisplayName(file);
            case 2:
                return file.getPath();
            case 3:
                return getCachedMetadata(file).size;
            case 4:
                return new Date(getCachedMetadata(file).lastModified);
            case 5:
                return getCachedMetadata(file).canRead;
            case 6:
                return getCachedMetadata(file).canWrite;
            case 7:
                return getCachedMetadata(file).canExecute;
            case 8:
                return getCachedMetadata(file).isDirectory;
            case 9:
                return getCachedMetadata(file).isFile;
            default:
                System.err.println("Logic Error");
        }
        return "";
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
                return ImageIcon.class;
            case 3:
                return Long.class;
            case 4:
                return Date.class;
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                return Boolean.class;
        }
        return String.class;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public int getRowCount() {
        return files.length;
    }

    public File getFile(int row) {
        return files[row];
    }

    public void setFiles(File[] files) {
        this.files = files != null ? files : new File[0];
        fireTableDataChanged();
    }
    
    /**
     * Sort files by name (directories first, then files).
     */
    public void sortFiles() {
        if (files != null && files.length > 0) {
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1;
                }
                if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1;
                }
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            fireTableDataChanged();
        }
    }
}
```

## Step 7: Update setTableData Method

Replace the existing `setTableData` method (around line 602) with this version that includes auto-sorting:

```java
/** Update the table on the EDT */
private void setTableData(final File[] files) {
    SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    if (fileTableModel == null) {
                        fileTableModel = new FileTableModel();
                        table.setModel(fileTableModel);
                    }
                    table.getSelectionModel()
                            .removeListSelectionListener(listSelectionListener);
                    fileTableModel.setFiles(files);
                    fileTableModel.sortFiles(); // Auto-sort files
                    table.getSelectionModel().addListSelectionListener(listSelectionListener);
                    if (!cellSizesSet && files.length > 0) {
                        Icon icon = getCachedIcon(files[0]);

                        // size adjustment to better account for icons
                        table.setRowHeight(icon.getIconHeight() + rowIconPadding);

                        setColumnWidth(0, -1);
                        setColumnWidth(3, 60);
                        table.getColumnModel().getColumn(3).setMaxWidth(120);
                        setColumnWidth(4, -1);
                        setColumnWidth(5, -1);
                        setColumnWidth(6, -1);
                        setColumnWidth(7, -1);
                        setColumnWidth(8, -1);
                        setColumnWidth(9, -1);

                        cellSizesSet = true;
                    }
                }
            });
}
```

## Step 8: Add Shutdown Hook

Add this method to properly shutdown the cache executor:

```java
/**
 * Shutdown cache cleanup executor.
 */
public static void shutdown() {
    cacheCleanupExecutor.shutdown();
}
```

## Step 9: Update Main Method

Add shutdown hook to the main method (around line 749):

```java
public static void main(String[] args) {
    SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    try {
                        // Significantly improves the look of the output in
                        // terms of the file names returned by FileSystemView!
                        UIManager.setLookAndFeel(new FlatLightLaf() {
                        });
                    } catch (Exception weTried) {
                    }
                    JFrame f = new JFrame(APP_TITLE);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                    FileManager fileManager = new FileManager();
                    f.setContentPane(fileManager.getGui());

                    try {
                        URL urlBig = fileManager.getClass().getResource("fm-icon-32x32.png");
                        URL urlSmall = fileManager.getClass().getResource("fm-icon-16x16.png");
                        ArrayList<Image> images = new ArrayList<Image>();
                        images.add(ImageIO.read(urlBig));
                        images.add(ImageIO.read(urlSmall));
                        f.setIconImages(images);
                    } catch (Exception weTried) {
                    }

                    f.pack();
                    f.setLocationByPlatform(true);
                    f.setMinimumSize(f.getSize());
                    f.setVisible(true);

                    fileManager.showRootFile();
                    
                    // Add shutdown hook for cache cleanup
                    f.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
                        public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                            shutdown();
                        }
                    });
                }
            });
}
```

## Step 10: Add Missing Import

Make sure to add this import at the top if not already present:

```java
import java.util.Arrays;
```

## Benefits of These Changes

After applying these modifications, your FileManager will have:

1. **Icon Caching**: Reduces repeated filesystem calls for icons
2. **Metadata Caching**: Caches file properties (size, permissions, dates)
3. **Auto-sorting**: Files are automatically sorted with directories first
4. **Memory Management**: Automatic cache cleanup every 5 minutes
5. **Better Performance**: Significant speed improvements for large directories

## Testing

After applying these changes:

1. Run the application and navigate through several directories
2. Notice faster response times when revisiting directories
3. Check that file details load quickly
4. Monitor memory usage - it should be more stable

## Optional Advanced Features

For even better performance, you can also integrate:

- **Background Operations**: Use `BackgroundFileOperations` for copy/delete operations
- **Thumbnail Loading**: Use `ThumbnailCache` for image previews
- **Weak References**: Use `WeakFileCache` for better memory management

These would require more extensive integration but provide additional performance benefits.