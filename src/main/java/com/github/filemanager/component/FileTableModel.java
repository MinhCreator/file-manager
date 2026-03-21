package com.github.filemanager.component;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.Arrays;
import java.util.Date;

import static com.github.filemanager.service.FileCache.getCachedIcon;
import static com.github.filemanager.service.FileCache.getCachedMetadata;

/**
 * A TableModel to hold File[].
 */
public class FileTableModel extends AbstractTableModel {

    private File[] files;
    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final String[] columns = {
            "Icon", "File", "Path/name", "Size", "Last Modified", "R", "W", "E", "D", "F",
    };
    private int[] rowToIndex;
    // Sort state
    private int sortColumn = -1;
    private boolean sortAscending = true;


    public FileTableModel() {
        this(new File[0]);
    }

    FileTableModel(File[] files) {
        this.files = files;
    }

    @Override
    public Object getValueAt(int row, int column) {
        if (rowToIndex != null && row >= 0 && row < rowToIndex.length) {
            row = rowToIndex[row];
        }
        if (row < 0 || row >= files.length) {
            return "";
        }
        File file = files[row];
        switch (column) {
            case 0:
                return getCachedIcon(file);
            case 1:
                return getCachedMetadata(file).name;
            case 2:
                return getCachedMetadata(file).path;
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

    public String getColumnName(int column) {
        if (column == sortColumn) {
            return columns[column] + (sortAscending ? " ↑" : " ↓");
        }
        return columns[column];
    }

    public int getRowCount() {
        return files.length;
    }

    public File getFile(int row) {
        if (rowToIndex != null && row >= 0 && row < rowToIndex.length) {
            row = rowToIndex[row];
        }
        return row >= 0 && row < files.length ? files[row] : null;
    }

    public void setFiles(File[] files) {
        this.files = files != null ? files : new File[0];
        this.rowToIndex = new int[this.files.length];
        for (int i = 0; i < this.files.length; i++) {
            this.rowToIndex[i] = i;
        }
        // Re-apply current sort if any
        if (sortColumn >= 0) {
            sortByColumn(sortColumn, sortAscending);
        } else {
            fireTableDataChanged();
        }
    }

    public void sortByColumn(int column) {
        if (column == sortColumn) {
            sortAscending = !sortAscending;
        } else {
            sortColumn = column;
            sortAscending = true;
        }
        sortByColumn(column, sortAscending);
    }

    /**
     * Sort by specified column with specified direction.
     */
    public void sortByColumn(int column, boolean ascending) {
        if (files == null || files.length <= 1) {
            return;
        }

        // Create array of indices
        Integer[] indices = new Integer[files.length];
        for (int i = 0; i < files.length; i++) {
            indices[i] = i;
        }

        // Sort based on column
        Arrays.sort(indices, (i1, i2) -> {
            File f1 = files[i1];
            File f2 = files[i2];
            int result = compareFiles(f1, f2, column);
            return ascending ? result : -result;
        });

        // Update rowToIndex
        for (int i = 0; i < files.length; i++) {
            rowToIndex[i] = indices[i];
        }

        fireTableDataChanged();
    }

    private int compareFiles(File f1, File f2, int column) {
        // Directories first when sorting by name
        if (column == 1) {
            if (f1.isDirectory() && !f2.isDirectory()) {
                return -1;
            }
            if (!f1.isDirectory() && f2.isDirectory()) {
                return 1;
            }
        }

        switch (column) {
            case 0: // Icon - sort by file type
            case 8: // Is Directory
            case 9: // Is File
                return Boolean.compare(getCachedMetadata(f2).isDirectory, getCachedMetadata(f1).isDirectory);

            case 1: // File name
                return getCachedMetadata(f1).name.compareToIgnoreCase(getCachedMetadata(f2).name);

            case 2: // Path
                return getCachedMetadata(f1).path.compareToIgnoreCase(getCachedMetadata(f2).path);

            case 3: // Size
                return Long.compare(getCachedMetadata(f1).size, getCachedMetadata(f2).size);

            case 4: // Last Modified
                return Long.compare(getCachedMetadata(f1).lastModified, getCachedMetadata(f2).lastModified);

            case 5: // Can Read
                return Boolean.compare(getCachedMetadata(f1).canRead, getCachedMetadata(f2).canRead);

            case 6: // Can Write
                return Boolean.compare(getCachedMetadata(f1).canWrite, getCachedMetadata(f2).canWrite);

            case 7: // Can Execute
                return Boolean.compare(getCachedMetadata(f1).canExecute, getCachedMetadata(f2).canExecute);

            default:
                return getCachedMetadata(f1).name.compareToIgnoreCase(getCachedMetadata(f2).name);
        }
    }

    public int getSortColumn() {
        return sortColumn;
    }

    public boolean isSortAscending() {
        return sortAscending;
    }

    public void clearSort() {
        sortColumn = -1;
        sortAscending = true;
        setFiles(files); // Reset to original order
    }
}