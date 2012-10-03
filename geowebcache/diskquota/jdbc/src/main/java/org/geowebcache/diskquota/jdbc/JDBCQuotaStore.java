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
 * @author Andrea Aime - GeoSolutions
 */
package org.geowebcache.diskquota.jdbc;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.diskquota.storage.TileSetVisitor;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An abstract quota store based on a JDBC reachable database, and configurable via a dialect class
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class JDBCQuotaStore implements QuotaStore {

    /**
     * The dialect accounting for database specific differences
     */
    SQLDialect dialect;

    /**
     * The template used to execute commands
     */
    JdbcTemplate template;

    /**
     * The database schema (optional)
     */
    String schema;

    /**
     * The storage finder, used to locate the data directory
     */
    DefaultStorageFinder finder;

    /**
     * The tile page calculator, serving as the source or layers, tile sets, tile pages
     */
    TilePageCalculator calculator;

    public JDBCQuotaStore(DefaultStorageFinder finder, TilePageCalculator tilePageCalculator) {
        this.finder = finder;
        this.calculator = tilePageCalculator;
    }

    /**
     * Gets the SQL dialect used by this quota store
     * 
     * @return
     */
    public SQLDialect getDialect() {
        return dialect;
    }

    /**
     * Returns the SQL dialect used by this quota store
     * 
     * @return
     */
    public void setDialect(SQLDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Returns he database shema used by this store
     * 
     * @return
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Sets the database schema used by this store
     * 
     * @param schema
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * Sets the connection pool provider and initializes the tables in the dbms if missing
     */
    public void setDataSource(DataSource dataSource) {
        this.template = new JdbcTemplate(dataSource);
    }

    /**
     * 
     */
    public void initialize() {
        if (dialect == null || template == null) {
            throw new IllegalStateException("Please provide both the sql dialect and the data "
                    + "source before calling inizialize");
        }
        dialect.initializeTables(schema, template);
    }

    public void createLayer(String layerName) throws InterruptedException {
        // TODO Auto-generated method stub

    }

    public Quota getGloballyUsedQuota() throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public Quota getUsedQuotaByTileSetId(String tileSetId) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public void deleteLayer(String layerName) {
        // TODO Auto-generated method stub

    }

    public void renameLayer(String oldLayerName, String newLayerName) throws InterruptedException {
        // TODO Auto-generated method stub

    }

    public Quota getUsedQuotaByLayerName(String layerName) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public long[][] getTilesForPage(TilePage page) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<TileSet> getTileSets() {
        // TODO Auto-generated method stub
        return null;
    }

    public TileSet getTileSetById(String tileSetId) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public void accept(TileSetVisitor visitor) {
        // TODO Auto-generated method stub

    }

    public TilePageCalculator getTilePageCalculator() {
        // TODO Auto-generated method stub
        return null;
    }

    public void addToQuotaAndTileCounts(TileSet tileSet, Quota quotaDiff,
            Collection<PageStatsPayload> tileCountDiffs) throws InterruptedException {
        // TODO Auto-generated method stub

    }

    public Future<List<PageStats>> addHitsAndSetAccesTime(Collection<PageStatsPayload> statsUpdates) {
        // TODO Auto-generated method stub
        return null;
    }

    public TilePage getLeastFrequentlyUsedPage(Set<String> layerNames) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public TilePage getLeastRecentlyUsedPage(Set<String> layerNames) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public PageStats setTruncated(TilePage tilePage) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    public void deleteGridSubset(String layerName, String gridSetId) {
        // TODO Auto-generated method stub

    }

}
