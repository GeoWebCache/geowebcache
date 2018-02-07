/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/**
 * Holder for the properties needed to configure a mbtiles blob store.
 */
public class MbtilesInfo extends SqliteInfo {

    public MbtilesInfo() {
        super();
    }

    public MbtilesInfo(String id) {
        super(id);
    }

    private String mbtilesMetadataDirectory;

    private int executorConcurrency = 5;

    private Boolean gzipVector = false;

    public String getMbtilesMetadataDirectory() {
        return mbtilesMetadataDirectory;
    }

    public void setMbtilesMetadataDirectory(String mbtilesMetadataDirectory) {
        this.mbtilesMetadataDirectory = mbtilesMetadataDirectory;
    }

    public int getExecutorConcurrency() {
        return executorConcurrency;
    }

    public void setExecutorConcurrency(int executorConcurrency) {
        this.executorConcurrency = executorConcurrency;
    }

    public boolean isGzipVector() {
        return gzipVector!=null && gzipVector;
    }

    public void setGzipVector(boolean gzipVector) {
        this.gzipVector = gzipVector;
    }

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider) throws StorageException {
        return new MbtilesBlobStore(this, super.getConnectionManager());
    }

    @Override
    public String toString() {
        return "MBTiles BlobStore";
    }
}
