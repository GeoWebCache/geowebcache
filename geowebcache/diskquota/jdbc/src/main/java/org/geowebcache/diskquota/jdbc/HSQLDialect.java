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
 * @author Can Mehteroglu - GeoSolutions Copyright 2023
 */
package org.geowebcache.diskquota.jdbc;

import java.util.List;

/**
 * HSQL dialect for the quota store
 *
 * @author Can Mehteroglu - GeoSolutions
 */
public class HSQLDialect extends SQLDialect {
    public HSQLDialect() {

        TABLE_CREATION_MAP.put(
                "TILESET",
                List.of( //
                        """
                        CREATE CACHED TABLE ${schema}TILESET (
                          KEY VARCHAR(%d) PRIMARY KEY,
                          LAYER_NAME VARCHAR(%d),
                          GRIDSET_ID VARCHAR(%d),
                          BLOB_FORMAT VARCHAR(%d),
                          PARAMETERS_ID VARCHAR(%d),
                          BYTES NUMERIC(%d) DEFAULT 0 NOT NULL
                        )
                        """
                                .formatted(
                                        TILESET_KEY_SIZE,
                                        LAYER_NAME_SIZE,
                                        GRIDSET_ID_SIZE,
                                        BLOB_FORMAT_SIZE,
                                        PARAMETERS_ID_SIZE,
                                        BYTES_SIZE), //
                        "CREATE INDEX TILESET_LAYER ON ${schema}TILESET(LAYER_NAME)" //
                        ));

        TABLE_CREATION_MAP.put(
                "TILEPAGE",
                List.of(
                        """
                        CREATE CACHED TABLE ${schema}TILEPAGE (
                         KEY VARCHAR(%d) PRIMARY KEY,
                         TILESET_ID VARCHAR(%d) REFERENCES ${schema}TILESET(KEY) ON UPDATE CASCADE ON DELETE CASCADE,
                         PAGE_Z SMALLINT,
                         PAGE_X INTEGER,
                         PAGE_Y INTEGER,
                         CREATION_TIME_MINUTES INTEGER,
                         FREQUENCY_OF_USE FLOAT,
                         LAST_ACCESS_TIME_MINUTES INTEGER,
                         FILL_FACTOR FLOAT,
                         NUM_HITS NUMERIC(%d)
                        )"""
                                .formatted(TILEPAGE_KEY_SIZE, TILESET_KEY_SIZE, NUM_HITS_SIZE), //
                        "CREATE INDEX TILEPAGE_TILESET ON ${schema}TILEPAGE(TILESET_ID, FILL_FACTOR)",
                        "CREATE INDEX TILEPAGE_FREQUENCY ON ${schema}TILEPAGE(FREQUENCY_OF_USE DESC)",
                        "CREATE INDEX TILEPAGE_LAST_ACCESS ON ${schema}TILEPAGE(LAST_ACCESS_TIME_MINUTES DESC)"));
    }

    @Override
    public String getCreateTileSetQuery(
            String schema,
            String keyParam,
            String layerNameParam,
            String gridSetIdParam,
            String blobFormatParam,
            String paramIdParam) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET SELECT :").append(keyParam);
        sb.append(", :").append(layerNameParam);
        sb.append(", :").append(gridSetIdParam);
        sb.append(", :").append(blobFormatParam);
        sb.append(", :").append(paramIdParam);
        sb.append(", 0 ");

        // add this to try avoiding race conditions with other
        // GWC instances doing parallel insertions
        addEmtpyTableReference(sb);
        sb.append(" FROM (VALUES(1)) AS dummy WHERE NOT EXISTS(SELECT 1 FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET WHERE KEY = :").append(keyParam).append(")");

        return sb.toString();
    }

    @Override
    public String contionalTilePageInsertStatement(
            String schema,
            String keyParam,
            String tileSetIdParam,
            String zParam,
            String xParam,
            String yParam,
            String creationParam,
            String frequencyParam,
            String lastAccessParam,
            String fillFactorParam,
            String numHitsParam) {
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

        // add this to try avoiding race conditions with other
        // GWC instances doing parallel insertions
        addEmtpyTableReference(sb);
        sb.append(" FROM (VALUES(1)) AS dummy WHERE NOT EXISTS(SELECT 1 FROM ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILEPAGE WHERE KEY = :").append(keyParam).append(")");

        return sb.toString();
    }
}
