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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.jdbc.support.DatabaseMetaDataCallback;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * Base class for quota store JDBC dialects, provides functionality based on SQL standards,
 * subclasses may override to take advantage of specific database features
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class SQLDialect {

    // size guesses: 128 characters should be more than enough for layer name, the gridset id
    // is normally an epsg code so 32 is way more than enough, the blob format
    // is normally a mime plus some extras, again 64 should fit, a param id is
    // a SHA-1 sum that uses 41 chars, the id is the sum of all the above plus
    // connecting chars, 320 is again more than enough
    // bytes is going to be less than a zettabyte(one million petabytes, 10^21) for the
    // foreseeable future
    
    protected static final int LAYER_NAME_SIZE = 128;
    protected static final int GRIDSET_ID_SIZE = 32;
    protected static final int BLOB_FORMAT_SIZE = 64;
    protected static final int PARAMETERS_ID_SIZE = 41;
    protected static final int BYTES_SIZE = 21;
    protected static final int NUM_HITS_SIZE = 64;
    protected static final int TILESET_KEY_SIZE = 320;
    protected static final int TILEPAGE_KEY_SIZE = TILESET_KEY_SIZE;
    
    protected final Map<String, List<String>> TABLE_CREATION_MAP = new LinkedHashMap<String, List<String>>() {
        {

            put("TILESET", Arrays.asList( //
                    "CREATE TABLE ${schema}TILESET (\n" + //
                            "  KEY VARCHAR("+TILESET_KEY_SIZE+") PRIMARY KEY,\n" + //
                            "  LAYER_NAME VARCHAR("+LAYER_NAME_SIZE+"),\n" + //
                            "  GRIDSET_ID VARCHAR("+GRIDSET_ID_SIZE+"),\n" + //
                            "  BLOB_FORMAT VARCHAR("+BLOB_FORMAT_SIZE+"),\n" + //
                            "  PARAMETERS_ID VARCHAR("+PARAMETERS_ID_SIZE+"),\n" + //
                            "  BYTES NUMERIC("+BYTES_SIZE+") NOT NULL DEFAULT 0\n" + //
                            ")", //
                    "CREATE INDEX TILESET_LAYER ON TILESET(LAYER_NAME)" //
            ));

            // this one embeds both tile page and page stats, since they are linked 1-1
            put("TILEPAGE", Arrays.asList(
                    "CREATE TABLE ${schema}TILEPAGE (\n"
                            + //
                            " KEY VARCHAR("+TILEPAGE_KEY_SIZE+") PRIMARY KEY,\n"
                            + //
                            " TILESET_ID VARCHAR("+TILESET_KEY_SIZE+") REFERENCES ${schema}TILESET(KEY) ON DELETE CASCADE,\n"
                            + //
                            " PAGE_Z SMALLINT,\n" + //
                            " PAGE_X INTEGER,\n" + //
                            " PAGE_Y INTEGER,\n" + //
                            " CREATION_TIME_MINUTES INTEGER,\n" + //
                            " FREQUENCY_OF_USE FLOAT,\n" + //
                            " LAST_ACCESS_TIME_MINUTES INTEGER,\n" + //
                            " FILL_FACTOR FLOAT,\n" + //
                            " NUM_HITS NUMERIC("+NUM_HITS_SIZE+")\n" + //
                            ")", //
                    "CREATE INDEX TILEPAGE_TILESET ON TILEPAGE(TILESET_ID, FILL_FACTOR)",
                    "CREATE INDEX TILEPAGE_FREQUENCY ON TILEPAGE(FREQUENCY_OF_USE DESC)",
                    "CREATE INDEX TILEPAGE_LAST_ACCESS ON TILEPAGE(LAST_ACCESS_TIME_MINUTES DESC)"));

        }
    };

    /**
     * Checks if the database schema is present, if missing it generates it
     * 
     * @param template
     */
    public void initializeTables(String schema, SimpleJdbcTemplate template) {
        String prefix;
        if (schema == null) {
            prefix = "";
        } else {
            prefix = schema + ".";
        }
        for (String table : TABLE_CREATION_MAP.keySet()) {
            if (!tableExists(template, schema, table)) {
                for (String command : TABLE_CREATION_MAP.get(table)) {
                    command = command.replace("${schema}", prefix);
                    template.getJdbcOperations().execute(command);
                }
            }
        }
    }

    /**
     * Checks if the specified table exists
     * 
     * @param template
     * @param tableName
     * @return
     */
    private boolean tableExists(SimpleJdbcTemplate template, final String schema,
            final String tableName) {
        try {
            DataSource ds = ((JdbcAccessor) template.getJdbcOperations()).getDataSource();
            return (Boolean) JdbcUtils.extractDatabaseMetaData(ds, new DatabaseMetaDataCallback() {

                public Object processMetaData(DatabaseMetaData dbmd) throws SQLException,
                        MetaDataAccessException {
                    ResultSet rs = null;
                    try {
                        rs = dbmd.getTables(null, schema, tableName.toLowerCase(), null);
                        boolean exists = rs.next();
                        rs.close();
                        if(exists) {
                            return true;
                        }
                        rs = dbmd.getTables(null, schema, tableName, null);
                        return rs.next();
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
            });
        } catch (MetaDataAccessException e) {
            return false;
        }
    }

    public String getAllLayersQuery(String schema) {
        StringBuilder sb = new StringBuilder("SELECT DISTINCT(LAYER_NAME) FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE KEY <> '" + JDBCQuotaStore.GLOBAL_QUOTA_NAME + "'");

        return sb.toString();
    }

    public String getLayerDeletionStatement(String schema, String layerNameParam) {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE LAYER_NAME = :").append(layerNameParam);

        return sb.toString();
    }

    public String getLayerGridDeletionStatement(String schema, String layerNameParam,
            String gridsetIdParam) {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE LAYER_NAME = :").append(layerNameParam);
        sb.append(" AND GRIDSET_ID = :").append(gridsetIdParam);

        return sb.toString();
    }

    public String getTileSetsQuery(String schema) {
        StringBuilder sb = new StringBuilder(
                "SELECT KEY, LAYER_NAME, GRIDSET_ID, BLOB_FORMAT, PARAMETERS_ID FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET");

        return sb.toString();
    }

    public String getTileSetQuery(String schema, String keyParam) {
        StringBuilder sb = new StringBuilder(
                "SELECT KEY, LAYER_NAME, GRIDSET_ID, BLOB_FORMAT, PARAMETERS_ID FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE KEY = :" + keyParam);

        return sb.toString();
    }

    public String getCreateTileSetQuery(String schema, String keyParam, String layerNameParam,
            String gridSetIdParam, String blobFormatParam, String paramIdParam) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET select :").append(keyParam);
        sb.append(", :").append(layerNameParam);
        sb.append(", :").append(gridSetIdParam);
        sb.append(", :").append(blobFormatParam);
        sb.append(", :").append(paramIdParam);
        sb.append(", 0 ");

        // add this to try avoiding race conditions with other GWC instances doing parallel
        // insertions
        addEmtpyTableReference(sb);
        sb.append(" WHERE NOT EXISTS(SELECT 1 FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE KEY = :").append(keyParam).append(")");

        return sb.toString();
    }

    /**
     * Whatever source table to use when there is not a real table to use as the source,
     * e.g., "select 1" vs "select 1 from dual". For most databases not adding anything is just fine.
     * @param sb 
     */
    protected void addEmtpyTableReference(StringBuilder sb) {
        // nothing to do        
    }

    public String getUsedQuotaByTileSetId(String schema, String keyParam) {
        StringBuilder sb = new StringBuilder("SELECT BYTES FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE KEY = :" + keyParam);
        return sb.toString();
    }
    
    public String getUsedQuotaByGridSetId(String schema, String gridsetIdParam) {
        StringBuilder sb = new StringBuilder("SELECT SUM(BYTES) FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE GRIDSET_ID = :").append(gridsetIdParam);
        return sb.toString();
    }

    public String getUsedQuotaByLayerName(String schema, String layerNameParam) {
        StringBuilder sb = new StringBuilder("SELECT SUM(BYTES) FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE TILESET.LAYER_NAME = :").append(layerNameParam);
        return sb.toString();

    }

    public String getRenameLayerStatement(String schema, String oldLayerName, String newLayerName) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET SET LAYER_NAME = :").append(newLayerName)
                .append(" WHERE LAYER_NAME = :").append(oldLayerName);

        return sb.toString();
    }

    public String getUpdateQuotaStatement(String schema, String tileSetIdParam, String bytesParam) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET SET BYTES = BYTES + (:").append(bytesParam).append(")");
        sb.append(" WHERE KEY = :").append(tileSetIdParam);

        return sb.toString();
    }

    public String getPageStats(String schema, String keyParam) {
        StringBuilder sb = new StringBuilder(
                "SELECT FREQUENCY_OF_USE, LAST_ACCESS_TIME_MINUTES, FILL_FACTOR, NUM_HITS FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE WHERE KEY = :").append(keyParam);

        return sb.toString();
    }

    public String contionalTilePageInsertStatement(String schema, String keyParam,
            String tileSetIdParam, String zParam, String xParam, String yParam,
            String creationParam, String frequencyParam, String lastAccessParam,
            String fillFactorParam, String numHitsParam) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE SELECT :").append(keyParam).append(", ");
        sb.append(":").append(tileSetIdParam).append(", ");
        sb.append(":").append(zParam).append(", ");
        sb.append(":").append(xParam).append(", ");
        sb.append(":").append(yParam).append(", ");
        sb.append(":").append(creationParam).append(", ");
        sb.append(":").append(frequencyParam).append(", ");
        sb.append(":").append(lastAccessParam).append(", ");
        sb.append(":").append(fillFactorParam).append(", ");
        sb.append(":").append(numHitsParam).append(" ");

        addEmtpyTableReference(sb);
        sb.append(" WHERE NOT EXISTS(SELECT 1 FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE WHERE KEY = :").append(keyParam).append(")");

        return sb.toString();
    }

    /**
     * Updates the fill factor in a page provided the old fill factor is still the one we read from
     * the db, otherwise updates nothing
     * 
     * @param schema
     * @param keyParam
     * @param newfillFactorParam
     * @param oldFillFactorParam
     * @return
     */
    public String conditionalUpdatePageStatsFillFactor(String schema, String keyParam,
            String newfillFactorParam, String oldFillFactorParam) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE SET FILL_FACTOR = :").append(newfillFactorParam);
        sb.append(" WHERE KEY = :").append(keyParam);
        // add this to avoid overwriting a fill factor that was updated by someone else
        sb.append(" AND FILL_FACTOR = :").append(oldFillFactorParam);

        return sb.toString();

    }

    /**
     * Forces the fill factor in a page to the desired value
     * 
     * @param schema
     * @param keyParam
     * @param newfillFactorParam
     * @param oldFillFactorParam
     * @return
     */
    public String updatePageStatsFillFactor(String schema, String keyParam,
            String newfillFactorParam) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE SET FILL_FACTOR = :").append(newfillFactorParam);
        sb.append(" WHERE KEY = :").append(keyParam);

        return sb.toString();

    }

    /**
     * Updates the fill factor in a page provided the old fill factor is still the one we read from
     * the db, otherwise updates nothing
     * 
     * @param schema
     * @param keyParam
     * @param newfillFactorParam
     * @param oldFillFactorParam
     * @return
     */
    public String updatePageStats(String schema, String keyParam, String newHitsParam,
            String oldHitsParam, String newFrequencyParam, String oldFrequencyParam,
            String newLastAccessTimeParam, String oldLastAccessTimeParam) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE SET NUM_HITS = :").append(newHitsParam);
        sb.append(", FREQUENCY_OF_USE = :").append(newFrequencyParam);
        sb.append(", LAST_ACCESS_TIME_MINUTES = :").append(newLastAccessTimeParam);
        sb.append(" WHERE KEY = :").append(keyParam);
        // add this to avoid overwriting params that were updated by another instance
        sb.append(" AND NUM_HITS = :").append(oldHitsParam);
        sb.append(" AND FREQUENCY_OF_USE = :").append(oldFrequencyParam);
        sb.append(" AND LAST_ACCESS_TIME_MINUTES = :").append(oldLastAccessTimeParam);

        return sb.toString();

    }

    public String getLeastFrequentlyUsedPage(String schema, List<String> layerParamNames) {
        StringBuilder sb = new StringBuilder(
                "SELECT TILESET_ID, PAGE_X, PAGE_Y, PAGE_Z, CREATION_TIME_MINUTES FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE WHERE FILL_FACTOR > 0 ");
        sb.append("AND TILESET_ID IN (");
        sb.append("SELECT KEY FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE LAYER_NAME IN (");
        for (int i = 0; i < layerParamNames.size(); i++) {
            sb.append(":" + layerParamNames.get(i));
            if (i < layerParamNames.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")) ");
        sb.append("ORDER BY FREQUENCY_OF_USE ASC LIMIT 1");

        return sb.toString();
    }

    public String getLeastRecentlyUsedPage(String schema, List<String> layerParamNames) {
        StringBuilder sb = new StringBuilder(
                "SELECT TILESET_ID, PAGE_X, PAGE_Y, PAGE_Z, CREATION_TIME_MINUTES FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE WHERE FILL_FACTOR > 0 ");
        sb.append("AND TILESET_ID IN (");
        sb.append("SELECT KEY FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE LAYER_NAME IN (");
        for (int i = 0; i < layerParamNames.size(); i++) {
            sb.append(":" + layerParamNames.get(i));
            if (i < layerParamNames.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")) ");
        sb.append("ORDER BY LAST_ACCESS_TIME_MINUTES ASC LIMIT 1");

        return sb.toString();
    }

}
