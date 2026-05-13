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
 * @author Andrea Aime - GeoSolutions Copyright 2012
 */
package org.geowebcache.diskquota.jdbc;

import java.util.List;

/**
 * Oracle dialect for the quota store, compared to the basic one creates index organized tables and uses the Oracle
 * specific syntax to get the pages with the oldest access time and lowest access frequency
 *
 * @author Andrea Aime - GeoSolutions
 */
public class OracleDialect extends SQLDialect {

    // Oracle's Number type has a maximum precision of 38
    static final int MAX_NUMBER_PRECISION = 38;

    static int numberPrecision(int n) {
        return Math.min(n, MAX_NUMBER_PRECISION);
    }

    public OracleDialect() {
        TABLE_CREATION_MAP.put(
                "TILESET",
                List.of( //
                        """
                        CREATE TABLE ${schema}TILESET (
                          KEY VARCHAR(%d) PRIMARY KEY,
                          LAYER_NAME VARCHAR(%d),
                          GRIDSET_ID VARCHAR(%d),
                          BLOB_FORMAT VARCHAR(%d),
                          PARAMETERS_ID VARCHAR(%d),
                          BYTES NUMBER(%d) DEFAULT 0 NOT NULL
                        ) ORGANIZATION INDEX
                        """
                                .formatted(
                                        TILESET_KEY_SIZE,
                                        LAYER_NAME_SIZE,
                                        GRIDSET_ID_SIZE,
                                        BLOB_FORMAT_SIZE,
                                        PARAMETERS_ID_SIZE,
                                        numberPrecision(BYTES_SIZE)), //
                        "CREATE INDEX TILESET_LAYER ON TILESET(LAYER_NAME)" //
                        ));

        TABLE_CREATION_MAP.put(
                "TILEPAGE",
                List.of(
                        """
                        CREATE TABLE ${schema}TILEPAGE (
                         KEY VARCHAR(%d) PRIMARY KEY,
                         TILESET_ID VARCHAR(%d) REFERENCES ${schema}TILESET(KEY) ON DELETE CASCADE,
                         PAGE_Z SMALLINT,
                         PAGE_X INTEGER,
                         PAGE_Y INTEGER,
                         CREATION_TIME_MINUTES INTEGER,
                         FREQUENCY_OF_USE FLOAT,
                         LAST_ACCESS_TIME_MINUTES INTEGER,
                         FILL_FACTOR FLOAT,
                         NUM_HITS NUMBER(%d)
                        ) ORGANIZATION INDEX
                        """
                                .formatted(TILEPAGE_KEY_SIZE, TILESET_KEY_SIZE, numberPrecision(NUM_HITS_SIZE)), //
                        "CREATE INDEX TILEPAGE_TILESET ON TILEPAGE(TILESET_ID)",
                        "CREATE INDEX TILEPAGE_FILL_FACTOR ON TILEPAGE(FILL_FACTOR)",
                        "CREATE INDEX TILEPAGE_FREQUENCY ON TILEPAGE(FREQUENCY_OF_USE DESC)",
                        "CREATE INDEX TILEPAGE_LAST_ACCESS ON TILEPAGE(LAST_ACCESS_TIME_MINUTES DESC)"));
    }

    @Override
    protected void addEmtpyTableReference(StringBuilder sb) {
        sb.append("FROM DUAL");
    }

    /**
     * No-op: Oracle does not support {@code ON UPDATE CASCADE} on foreign keys, so there is nothing portable to
     * migrate. Companion to {@link #getRenameLayerStatement(String, String, String)}, which preserves the legacy
     * LAYER_NAME-only behavior on this dialect.
     */
    @Override
    public void migrateForeignKeys(String schema, SimpleJdbcTemplate template) {
        // intentional no-op
    }

    /**
     * Oracle does not support {@code ON UPDATE CASCADE} on foreign keys, so the {@code TILEPAGE.TILESET_ID -> TILESET
     * .KEY} FK declared above only cascades on delete. As a result this dialect cannot safely rewrite {@code TILESET
     * .KEY} during a rename without first dealing with the dangling {@code TILEPAGE} rows.
     *
     * <p>For now Oracle keeps the legacy behavior of only updating {@code LAYER_NAME}; lookups by id against the
     * renamed layer will continue to miss the row and cause {@code getOrCreateTileSet} to insert duplicates. Fixing
     * this on Oracle (e.g. via {@code DEFERRABLE INITIALLY DEFERRED} constraints, or by disabling the FK around the
     * rename) is tracked separately.
     */
    @Override
    public String getRenameLayerStatement(String schema, String oldLayerName, String newLayerName) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        if (schema != null) {
            sb.append(schema).append(".");
        }
        sb.append("TILESET SET LAYER_NAME = :")
                .append(newLayerName)
                .append(" WHERE LAYER_NAME = :")
                .append(oldLayerName);

        return sb.toString();
    }

    @Override
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

    @Override
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
