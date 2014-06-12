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
import java.util.List;

/**
 * Oracle dialect for the quota store, compared to the basic one creates index organized tables
 * and uses the Oracle specific syntax to get the pages with the oldest access time and lowest
 * access frequency
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class OracleDialect extends SQLDialect {

    public OracleDialect() {
        TABLE_CREATION_MAP.put("TILESET", Arrays.asList( //
                "CREATE TABLE ${schema}TILESET (\n" + //
                        "  KEY VARCHAR("+TILESET_KEY_SIZE+") PRIMARY KEY,\n" + //
                        "  LAYER_NAME VARCHAR("+LAYER_NAME_SIZE+"),\n" + //
                        "  GRIDSET_ID VARCHAR("+GRIDSET_ID_SIZE+"),\n" + //
                        "  BLOB_FORMAT VARCHAR("+BLOB_FORMAT_SIZE+"),\n" + //
                        "  PARAMETERS_ID VARCHAR("+PARAMETERS_ID_SIZE+"),\n" + //
                        "  BYTES NUMBER("+BYTES_SIZE+") DEFAULT 0 NOT NULL\n" + //
                        ") ORGANIZATION INDEX", //
                "CREATE INDEX TILESET_LAYER ON TILESET(LAYER_NAME)" //
        ));

        TABLE_CREATION_MAP.put("TILEPAGE", Arrays.asList(
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
                        " NUM_HITS NUMBER("+NUM_HITS_SIZE+")\n" + //
                        ") ORGANIZATION INDEX", //
                "CREATE INDEX TILEPAGE_TILESET ON TILEPAGE(TILESET_ID)",
                "CREATE INDEX TILEPAGE_FILL_FACTOR ON TILEPAGE(FILL_FACTOR)",
                "CREATE INDEX TILEPAGE_FREQUENCY ON TILEPAGE(FREQUENCY_OF_USE DESC)",
                "CREATE INDEX TILEPAGE_LAST_ACCESS ON TILEPAGE(LAST_ACCESS_TIME_MINUTES DESC)"));
    }
    
    @Override
    protected void addEmtpyTableReference(StringBuilder sb) {
        sb.append("FROM DUAL");
    }
    
    public String getLeastFrequentlyUsedPage(String schema, List<String> layerParamNames) {
        StringBuilder sb = new StringBuilder("SELECT * FROM (");
        sb.append("SELECT TILESET_ID, PAGE_X, PAGE_Y, PAGE_Z, CREATION_TIME_MINUTES FROM ");
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
        sb.append(")) ORDER BY FREQUENCY_OF_USE ASC");
        sb.append(") WHERE ROWNUM <= 1");
        
        return sb.toString();
    }

    public String getLeastRecentlyUsedPage(String schema, List<String> layerParamNames) {
        StringBuilder sb = new StringBuilder("SELECT * FROM (");
        sb.append("SELECT TILESET_ID, PAGE_X, PAGE_Y, PAGE_Z, CREATION_TIME_MINUTES FROM ");
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
        sb.append(")) ORDER BY LAST_ACCESS_TIME_MINUTES ASC");
        sb.append(") WHERE ROWNUM <= 1");

        return sb.toString();
    }
}
