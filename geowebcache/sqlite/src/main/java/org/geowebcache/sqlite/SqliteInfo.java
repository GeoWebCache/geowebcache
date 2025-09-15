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

import java.io.File;
import java.io.Serial;
import java.util.UUID;
import org.geowebcache.config.BlobStoreInfo;

/** Holder for the common properties needed to configure a sqlite based blob store. */
public abstract class SqliteInfo extends BlobStoreInfo {
    @Serial
    private static final long serialVersionUID = 2300159159094621077L;

    public SqliteInfo() {
        this(UUID.randomUUID().toString());
    }

    public SqliteInfo(String id) {
        super(id);
    }

    private transient SqliteConnectionManager connectionManager;

    private String rootDirectory;

    private String templatePath = Utils.buildPath("{layer}", "{grid}{format}{params}", "{z}_{x}_{y}.sqlite");

    private long poolSize = 1000;

    private long poolReaperIntervalMs = 500;

    private long rowRangeCount = 250;

    private long columnRangeCount = 250;

    private boolean eagerDelete = false;

    private boolean useCreateTime = true;

    public File getRootDirectoryFile() {
        File file = new File(rootDirectory);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public long getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(long poolSize) {
        this.poolSize = poolSize;
    }

    public long getPoolReaperIntervalMs() {
        return poolReaperIntervalMs;
    }

    public void setPoolReaperIntervalMs(long poolReaperIntervalMs) {
        this.poolReaperIntervalMs = poolReaperIntervalMs;
    }

    public long getRowRangeCount() {
        return rowRangeCount;
    }

    public void setRowRangeCount(long rowRangeCount) {
        this.rowRangeCount = rowRangeCount;
    }

    public long getColumnRangeCount() {
        return columnRangeCount;
    }

    public void setColumnRangeCount(long columnRangeCount) {
        this.columnRangeCount = columnRangeCount;
    }

    public boolean eagerDelete() {
        return eagerDelete;
    }

    public void setEagerDelete(boolean eagerDelete) {
        this.eagerDelete = eagerDelete;
    }

    public boolean useCreateTime() {
        return useCreateTime;
    }

    public void setUseCreateTime(boolean useCreateTime) {
        this.useCreateTime = useCreateTime;
    }

    @Override
    public String getLocation() {
        return rootDirectory;
    }

    protected synchronized SqliteConnectionManager getConnectionManager() {
        if (connectionManager == null) {
            connectionManager = new SqliteConnectionManager(this);
        }
        return connectionManager;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (int) (columnRangeCount ^ (columnRangeCount >>> 32));
        result = prime * result + (eagerDelete ? 1231 : 1237);
        result = prime * result + (int) (poolReaperIntervalMs ^ (poolReaperIntervalMs >>> 32));
        result = prime * result + (int) (poolSize ^ (poolSize >>> 32));
        result = prime * result + ((rootDirectory == null) ? 0 : rootDirectory.hashCode());
        result = prime * result + (int) (rowRangeCount ^ (rowRangeCount >>> 32));
        result = prime * result + ((templatePath == null) ? 0 : templatePath.hashCode());
        result = prime * result + (useCreateTime ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        SqliteInfo other = (SqliteInfo) obj;
        if (columnRangeCount != other.columnRangeCount) return false;
        if (eagerDelete != other.eagerDelete) return false;
        if (poolReaperIntervalMs != other.poolReaperIntervalMs) return false;
        if (poolSize != other.poolSize) return false;
        if (rootDirectory == null) {
            if (other.rootDirectory != null) return false;
        } else if (!rootDirectory.equals(other.rootDirectory)) return false;
        if (rowRangeCount != other.rowRangeCount) return false;
        if (templatePath == null) {
            if (other.templatePath != null) return false;
        } else if (!templatePath.equals(other.templatePath)) return false;
        if (useCreateTime != other.useCreateTime) return false;
        return true;
    }
}
