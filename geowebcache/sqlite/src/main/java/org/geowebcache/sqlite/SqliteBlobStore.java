/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.geowebcache.storage.BlobStore;

import java.io.File;

/**
 * Base class for all SQLite based blob stores.
 */
abstract class SqliteBlobStore implements BlobStore {

    private final SqliteInfo configuration;

    protected final FileManager fileManager;
    protected final SqliteConnectionManager connectionManager;

    protected SqliteBlobStore(SqliteInfo configuration, SqliteConnectionManager connectionManager) {
        this.configuration = configuration;
        this.fileManager = new FileManager(configuration.getRootDirectoryFile(), configuration.getTemplatePath(),
                configuration.getRowRangeCount(), configuration.getColumnRangeCount());
        this.connectionManager = connectionManager;
    }

    SqliteInfo getConfiguration() {
        return configuration;
    }

    /**
     * Will move the new file to is destination.
     */
    void replace(File newFile, String destination) {
        // we ask the connection manager to replace the files
        File destinationFile = new File(configuration.getRootDirectoryFile(), destination);
        connectionManager.replace(destinationFile, newFile);
    }

    /**
     * Merging the given directory with this blob store directory.
     */
    void replace(File directory) {
        // we may have more that one top files
        File[] files = directory.listFiles();
        if (files == null) {
            // no files, nothing to do
            return;
        }
        for (File file : files) {
            // walk the file tree of each top file
            walkDirectoryTreeAndReplace(file, "");
        }
    }

    /**
     * Helper method that will recursively perform a replace operation using the directory files.
     */
    private void walkDirectoryTreeAndReplace(File current, String relativePath) {
        String newRelativePath = relativePath + File.separator + current.getName();
        if (!current.isDirectory()) {
            // we have a file let's perform the replace operation
            File destinationFile = new File(configuration.getRootDirectoryFile(), newRelativePath);
            connectionManager.replace(destinationFile, current);
            return;
        }
        // is a directory let's handle all the existing files
        File[] files = current.listFiles();
        if (files == null) {
            // no files, nothing to do
            return;
        }
        for (File file : files) {
            walkDirectoryTreeAndReplace(file, newRelativePath);
        }
    }

    @Override
    public void destroy() {

    }
}
