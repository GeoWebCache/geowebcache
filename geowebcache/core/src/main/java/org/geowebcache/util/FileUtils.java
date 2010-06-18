/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileUtils {
    private static Log log = LogFactory.getLog(org.geowebcache.util.FileUtils.class);

    static public boolean rmFileCacheDir(File path, ExtensionFileLister extfl) {
        if (path.exists()) {
            File[] files = null;

            if (extfl != null) {
                files = path.listFiles(extfl);
            } else {
                files = path.listFiles();
            }

            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    rmFileCacheDir(files[i], extfl);
                } else {
                    if (!files[i].delete()) {
                        log.error("Unable to delete " + files[i].getAbsolutePath());
                    }
                }
            }
        }
        return (path.delete());
    }

    /**
     * Traverses the directory denoted by {@code path} recursively and calls {@code filter.accept}
     * on each child, files first, subdirectories next.
     * <p>
     * For a child directory to be traversed, the {@code filter.accept(File)} method shall have
     * returned {@code true}, otherwise the child directory is skipped.
     * </p>
     * <p>
     * This method guarantees that {@code filter.accept} will be called first for all files in a
     * directory and then for all it's sub directories.
     * <p>
     * 
     * @param path
     * @param filter
     *            used to implement the visitor pattern. The accept method may contain any desired
     *            logic, it will be called for all files and directories inside {@code path},
     *            recursively
     */
    public static void traverseDepth(final File path, final FileFilter filter) {
        if (path == null) {
            throw new NullPointerException("path");
        }
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        if (!path.exists() || !path.isDirectory() || !path.canRead()) {
            throw new IllegalArgumentException(path.getAbsolutePath()
                    + " either does not exist, or is not a readable directory");
        }
        // Use path.list() instead of path.listFiles() to avoid the simultaneous creation of
        // thousands of File objects as well as its String objects for the path name. Faster and
        // less resource intensive
        String[] fileNames = path.list();
        List<File> subDirectories = new ArrayList<File>();
        
        File file;
        for (int i = 0; i < fileNames.length; i++) {
            file = new File(path, fileNames[i]);
            if(file.isDirectory()){
                subDirectories.add(file);
            }
            filter.accept(file);
        }
        if (subDirectories.size() > 0) {
            for (File subdir : subDirectories) {
                boolean accepted = filter.accept(subdir);
                if (accepted && subdir.isDirectory()) {
                    traverseDepth(subdir, filter);
                }
            }
        }
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
}
