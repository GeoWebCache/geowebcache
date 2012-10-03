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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for quota store JDBC dialects, provides functionality based on SQL standards,
 * subclasses may override to take advantage of specific database features
 * 
 * @author Andrea Aime - GeoSolutions
 * 
 */
public class SQLDialect {

    protected final Map<String, List<String>> TABLE_CREATION_MAP = new LinkedHashMap<String, List<String>>() {
        {
            // size guesses: layer name cannot be larger than 64 chars, the gridset id
            // is normally an epsg code so 32 is way more than enough, the blob format
            // is normally a mime plus some extras, again 64 should fit, a param id is
            // a SHA-1 sum that uses 41 chars, the id is the sum of all the above plus
            // connecting chars, 256 is again more than enough
            put("TILESET", Arrays.asList( //
                    "CREATE TABLE ${schema}TILESET (\n" + //
                            "  KEY VARCHAR(256) PRIMARY KEY,\n" + //
                            "  LAYER_NAME VARCHAR(64),\n" + //
                            "  GRIDSET_ID VARCHAR(32),\n" + //
                            "  BLOB_FORMAT VARCHAR(64),\n" + //
                            "  PARAMETERS_ID VARCHAR(41)\n" + //
                            ")", //
                    "CREATE INDEX TILESET_LAYER ON TILESET(LAYER_NAME)" //
            ));

            // this one embeds both tile page and page stats, since they are linked 1-1
            put("TILEPAGE", Arrays.asList(
                    "CREATE TABLE ${schema}TILEPAGE (\n" + //
                            " KEY VARCHAR(256) PRIMARY KEY,\n" + //
                            " TILESET_ID VARCHAR(256) REFERENCES ${schema}TILESET(KEY),\n" + //
                            " PAGE_Z SMALLINT,\n" + //
                            " PAGE_X INTEGER,\n" + //
                            " PAGE_Y INTEGER,\n" + //
                            " CREATION_TIME_MINUTES INTEGER,\n" + //
                            " FREQUENCY_OF_USE FLOAT,\n" + //
                            " LAST_ACCESS_TIME_MINUTES INTEGER,\n" + //
                            " FILL_FACTOR FLOAT,\n" + //
                            " NUM_HITS NUMBER(64)\n" + //
                            ")", //
                    "CREATE INDEX TILEPAGE_TILESET ON TILEPAGE(TILESET_ID)",
                    "CREATE INDEX TILEPAGE_FREQUENCY ON TILEPAGE(FREQUENCY_OF_USE)",
                    "CREATE INDEX TILEPAGE_LAST_ACCESS ON TILEPAGE(LAST_ACCESS_TIME_MINUTES)",
                    "CREATE INDEX TILEPAGE_FILL_FACTOR ON TILEPAGE(FILL_FACTOR)" //
            ));

            // size guesses: the size in bytes is going to be less than a zettabyte
            // (one million petabytes, 10^21) for the foreseeable future
            put("QUOTA", Arrays.asList( //
                    "CREATE TABLE ${schema}QUOTA (\n" + //
                            "  ID INTEGER PRIMARY KEY,\n" + //
                            "  TILESET_ID VARCHAR(256) REFERENCES ${schema}TILESET(KEY),\n" + //
                            "  BYTES NUMERIC(21),\n" + //
                            ")", //
                    "CREATE INDEX QUOTA_TILESET ON QUOTA(TILESET_ID)" //
            ));

        }
    };

    /**
     * Checks if the database schema is present, if missing it generates it
     * 
     * @param template
     */
    public void initializeTables(String schema, JdbcTemplate template) {
        String prefix;
        if(schema == null) {
            prefix = "";
        } else {
            prefix = schema + ".";
        }
        for (String table : TABLE_CREATION_MAP.keySet()) {
            if (!tableExists(template, table)) {
                for (String command : TABLE_CREATION_MAP.get(table)) {
                    command = command.replace("${schema}", prefix);
                    template.execute(command);
                }
            }
        }
    }

    /**
     * Checks if the specified table exists
     * @param template
     * @param tableName
     * @return
     */
    private boolean tableExists(JdbcTemplate template, String tableName) {
        try {
            // execute a query returning no data just to check if the table is there
            template.execute("SELECT * FROM " + tableName + " WHERE 1 = 0");
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

}
