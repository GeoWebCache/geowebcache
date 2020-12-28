/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileUtils {
    private static final File[] NO_FILES = new File[0];
    static Log log = LogFactory.getLog(org.geowebcache.util.FileUtils.class);

    public static boolean rmFileCacheDir(File path, ExtensionFileLister extfl) {
        if (path.exists()) {
            File[] files = null;

            if (extfl != null) {
                files = listFilesNullSafe(path, extfl);
            } else {
                files = listFilesNullSafe(path);
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    rmFileCacheDir(file, extfl);
                } else {
                    if (!file.delete()) {
                        log.error("Unable to delete " + file.getAbsolutePath());
                    }
                }
            }
        }
        return (path.delete());
    }

    /**
     * Traverses the directory denoted by {@code path} recursively and calls {@code filter.accept}
     * on each child, files first, subdirectories next.
     *
     * <p>For a child directory to be traversed, the {@code filter.accept(File)} method shall have
     * returned {@code true}, otherwise the child directory is skipped.
     *
     * <p>This method guarantees that {@code filter.accept} will be called first for all files in a
     * directory and then for all it's sub directories.
     *
     * <p>
     *
     * @param filter used to implement the visitor pattern. The accept method may contain any
     *     desired logic, it will be called for all files and directories inside {@code path},
     *     recursively
     */
    public static void traverseDepth(final File path, final FileFilter filter) {
        if (path == null) {
            throw new NullPointerException("path");
        }
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        if (!path.exists() || !path.isDirectory() || !path.canRead()) {
            throw new IllegalArgumentException(
                    path.getAbsolutePath()
                            + " either does not exist, or is not a readable directory");
        }
        // Use path.list() instead of path.listFiles() to avoid the simultaneous creation of
        // thousands of File objects as well as its String objects for the path name. Faster and
        // less resource intensive
        File[] files = listFilesNullSafe(path);
        List<File> subDirectories = new ArrayList<>();

        File file;
        for (File value : files) {
            file = value;
            if (file.isDirectory()) {
                subDirectories.add(file);
            }
            filter.accept(file);
        }
        if (!subDirectories.isEmpty()) {
            for (File subdir : subDirectories) {
                boolean accepted = filter.accept(subdir);
                if (accepted && subdir.isDirectory()) {
                    traverseDepth(subdir, filter);
                }
            }
        }
    }

    /**
     * Utility method for renaming Files using Java 7 {@link Files}.move() method which provides an
     * atomical file renaming. If an exception occurred during the renaming, the method will
     * fallback to the old File().renameTo() method and will log a Message.
     *
     * @return a boolean indicating if the rename operation has succeded
     */
    public static boolean renameFile(File src, File dst) {
        // Renaming result initialization
        boolean renamed = false;

        // windows needs special treatment, we cannot rename onto an existing file
        boolean win = System.getProperty("os.name").startsWith("Windows");
        if (win && dst.exists()) {
            // windows does not do atomic renames, and can not rename a file if the dest file
            // exists
            if (!dst.delete()) {
                throw new RuntimeException("Could not delete: " + dst.getPath());
            }
        }

        // 1) try with Java 7 Files.move
        Path srcPath = Paths.get(src.toURI());
        Path dstPath = Paths.get(dst.toURI());
        Path moved = null;
        try {
            // Execute renaming
            moved = Files.move(srcPath, dstPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // Exception occurred falling back to the old renameTo
            if (log.isDebugEnabled()) {
                log.debug(
                        "An error occurred when executing atomic file renaming. Falling back to the old File.renameTo() method",
                        e);
            }
        }
        // 2) Check if succeeded. If failed, falling back to old renameTo
        if (moved == null || !Files.exists(moved)) {
            renamed = src.renameTo(dst);
        } else {
            renamed = true;
        }

        return renamed;
    }

    public static class ExtensionFileLister implements FilenameFilter {
        private String prefix;

        private String extension;

        public ExtensionFileLister(String prefix, String extension) {
            this.prefix = prefix;
            this.extension = extension == null ? null : "." + extension;
        }

        public boolean accept(File directory, String filename) {
            if (prefix != null && !filename.startsWith(prefix)) {
                return false;
            }

            if (extension != null) {
                return filename.endsWith(extension);
            }

            return true;
        }
    }

    /** Prints the provided file tree structure onto the returned string */
    public static String printFileTree(File dir) {
        StringBuilder sb = new StringBuilder();
        sb.append(dir.getPath()).append("\n");
        printFileTree_(sb, "", dir);
        return sb.toString();
    }

    private static void printFileTree_(StringBuilder sb, String prefix, File dir) {
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (int i = 0; i < listFile.length; i++) {
                boolean last = i == listFile.length - 1;
                File file = listFile[i];
                String firstChar = last ? "└" : "├";
                sb.append(prefix)
                        .append(firstChar)
                        .append("──")
                        .append(file.getName())
                        .append("\n");
                if (file.isDirectory()) {
                    printFileTree_(sb, prefix + (last ? " " : "|") + "  ", file);
                }
            }
        }
    }

    /** Lists files with the given filter, never returns null */
    public static File[] listFilesNullSafe(File layerPath, FileFilter filter) {
        File[] files = layerPath.listFiles(filter);
        if (files != null) return files;
        return NO_FILES;
    }

    /** Lists files, never returns null */
    public static File[] listFilesNullSafe(File directory) {
        File[] files = directory.listFiles();
        if (files != null) return files;
        return NO_FILES;
    }

    /** Lists files with the given filter, never returns null */
    public static File[] listFilesNullSafe(File layerPath, FilenameFilter tileFinder) {
        File[] files = layerPath.listFiles(tileFinder);
        if (files != null) return files;
        return NO_FILES;
    }
}
