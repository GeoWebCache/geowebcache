/* (c) 2023 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geowebcache.diskquota.jdbc;

import java.util.Arrays;

/**
 * HSQL dialect for the quota store
 *
 * @author Can Mehteroglu - GeoSolutions
 */
public class HSQLDialect extends SQLDialect {
    public HSQLDialect() {

        TABLE_CREATION_MAP.put(
                "TILESET",
                Arrays.asList( //
                        "CREATE TABLE ${schema}TILESET (\n"
                                + //
                                "  KEY VARCHAR("
                                + TILESET_KEY_SIZE
                                + ") PRIMARY KEY,\n"
                                + //
                                "  LAYER_NAME VARCHAR("
                                + LAYER_NAME_SIZE
                                + "),\n"
                                + //
                                "  GRIDSET_ID VARCHAR("
                                + GRIDSET_ID_SIZE
                                + "),\n"
                                + //
                                "  BLOB_FORMAT VARCHAR("
                                + BLOB_FORMAT_SIZE
                                + "),\n"
                                + //
                                "  PARAMETERS_ID VARCHAR("
                                + PARAMETERS_ID_SIZE
                                + "),\n"
                                + //
                                "  BYTES NUMERIC("
                                + BYTES_SIZE
                                + ") DEFAULT 0 NOT NULL\n"
                                + //
                                ")", //
                        "CREATE INDEX TILESET_LAYER ON ${schema}TILESET(LAYER_NAME)" //
                        ));

        TABLE_CREATION_MAP.put(
                "TILEPAGE",
                Arrays.asList(
                        "CREATE TABLE ${schema}TILEPAGE (\n"
                                + //
                                " KEY VARCHAR("
                                + TILEPAGE_KEY_SIZE
                                + ") PRIMARY KEY,\n"
                                + //
                                " TILESET_ID VARCHAR("
                                + TILESET_KEY_SIZE
                                + ") REFERENCES ${schema}TILESET(KEY) ON DELETE CASCADE,\n"
                                + //
                                " PAGE_Z SMALLINT,\n"
                                + //
                                " PAGE_X INTEGER,\n"
                                + //
                                " PAGE_Y INTEGER,\n"
                                + //
                                " CREATION_TIME_MINUTES INTEGER,\n"
                                + //
                                " FREQUENCY_OF_USE FLOAT,\n"
                                + //
                                " LAST_ACCESS_TIME_MINUTES INTEGER,\n"
                                + //
                                " FILL_FACTOR FLOAT,\n"
                                + //
                                " NUM_HITS NUMERIC("
                                + NUM_HITS_SIZE
                                + ")\n"
                                + //
                                ")", //
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
