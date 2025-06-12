/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import java.io.Serial;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/** Holder for the properties needed to configure a mbtiles blob store. */
public class MbtilesInfo extends SqliteInfo {
    @Serial
    private static final long serialVersionUID = -6618985107587790155L;

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
        return gzipVector != null && gzipVector;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + executorConcurrency;
        result = prime * result + ((gzipVector == null) ? 0 : gzipVector.hashCode());
        result = prime * result + ((mbtilesMetadataDirectory == null) ? 0 : mbtilesMetadataDirectory.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        MbtilesInfo other = (MbtilesInfo) obj;
        if (executorConcurrency != other.executorConcurrency) return false;
        if (gzipVector == null) {
            if (other.gzipVector != null) return false;
        } else if (!gzipVector.equals(other.gzipVector)) return false;
        if (mbtilesMetadataDirectory == null) {
            if (other.mbtilesMetadataDirectory != null) return false;
        } else if (!mbtilesMetadataDirectory.equals(other.mbtilesMetadataDirectory)) return false;
        return true;
    }
}
