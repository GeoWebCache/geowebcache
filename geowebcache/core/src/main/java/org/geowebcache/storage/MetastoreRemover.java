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
 * <p>Copyright 2019
 */
package org.geowebcache.storage;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.appendFiltered;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.blobstore.file.DefaultFilePathGenerator;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;
import org.geowebcache.storage.blobstore.file.FilePathUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Upgrades a 1.3.x GWC cache directory to the 1.4.x metastore-less style.
 *
 * @author Andrea Aime - GeoSolutions
 */
public class MetastoreRemover {

    private static Logger log = Logging.getLogger(MetastoreRemover.class.getName());

    private DefaultStorageFinder storageFinder;

    private boolean defaultLocation;

    public MetastoreRemover(DefaultStorageFinder finder) throws Exception {
        this.storageFinder = finder;
        File root = new File(storageFinder.getDefaultPath());
        try (Connection conn = getMetaStoreConnection(root)) {
            if (conn != null) {
                log.info("Migrating the old metastore to filesystem storage");
                @SuppressWarnings("PMD.CloseResource")
                SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, false);
                JdbcTemplate template = new JdbcTemplate(ds);

                // maybe we should make this optional?
                boolean migrateCreationDates = Boolean.getBoolean("MIGRATE_CREATION_DATES");
                if (migrateCreationDates) {
                    migrateTileDates(template, new DefaultFilePathGenerator(root.getPath()));
                }
                migrateParameters(template, root);
                // remove all the tiles from storage to avoid further migration attempts
                // in the future, but only if the old metastore was external to the data dir
                if (!defaultLocation) {
                    removeTiles(template);
                }
            }
        }

        // wipe out the entire database if the db location is the default one
        if (defaultLocation) {
            File dbFile = getDefaultH2Path(root).getParentFile();
            if (dbFile.exists()) {
                log.info("Cleaning up the old H2 database");
                FileUtils.deleteDirectory(dbFile);
            }
        }

        // remove disk quota if necessary (this we have to do regardless as we changed the
        // structure of the params from int to string)
        String path = root.getPath() + File.separator + "diskquota_page_store";
        File quotaRoot = new File(path);
        if (quotaRoot.exists()) {
            File version = new File(quotaRoot, "version.txt");
            if (!version.exists()) {
                log.warning("Old style DiskQuota database found, removing it.");
                FileUtils.deleteDirectory(quotaRoot);
            }
        }
    }

    /** Drop all the tiles to prevent a future migration */
    private void removeTiles(JdbcTemplate template) {
        template.execute("delete from tiles");
    }

    private void migrateParameters(JdbcTemplate template, final File root) {
        // find all possible combinations of layer, zoom level, gridset and parameter id
        String query =
                """
                select layers.value as layer, gridsets.value as gridset, tiles.z, parameters.value as parameters, parameters_id
                from tiles join layers on layers.id = tiles.layer_id\s
                     join gridsets on gridsets.id = tiles.gridset_id
                     join parameters on parameters.id = tiles.parameters_id
                group by layer, gridset, z, parameters, parameters_id""";

        final long total = Optional.ofNullable(
                        template.queryForObject("select count(*) from (" + query + ")", Long.class))
                .orElse(0l);
        log.info("Migrating " + total + " parameters from the metastore to the file system");
        template.query(query, new RowCallbackHandler() {

            long count = 0;

            @Override
            public void processRow(ResultSet rs) throws SQLException {
                String layer = rs.getString(1);
                String gridset = rs.getString(2);
                int z = rs.getInt(3);
                String paramsKvp = rs.getString(4);
                String paramsId = rs.getString(5);

                String sha = getParamsSha1(paramsKvp);

                // move the folders containing params
                File origin = new File(buildFolderPath(root, layer, gridset, z, paramsId));
                File destination = new File(buildFolderPath(root, layer, gridset, z, sha));
                org.geowebcache.util.FileUtils.renameFile(origin, destination);

                count++;
                if (count % 1000 == 0 || count >= total) {
                    log.info("Migrated " + count + "/" + total + " parameters from the metastore to the file system");
                }
            }

            private String buildFolderPath(final File root, String layer, String gridset, int z, String paramsId) {
                // build the old path
                StringBuilder path = new StringBuilder();
                path.append(root.getPath());
                path.append(File.separatorChar);
                appendFiltered(layer, path);
                path.append(File.separatorChar);
                FilePathUtils.appendGridsetZoomLevelDir(gridset, z, path);
                path.append('_');
                path.append(paramsId);
                path.append(File.separatorChar);

                return path.toString();
            }

            private String getParamsSha1(String paramsKvp) {
                Map<String, String> params = toMap(paramsKvp);
                return ParametersUtils.getId(params);
            }

            /**
             * Parses the param list stored in the db to a parameter list (since this is coming from the database the
             * assumption is that the contents are sane)
             */
            private Map<String, String> toMap(String paramsKvp) {
                // TODO: wondering, shall we URL decode the values??
                Map<String, String> result = new HashMap<>();
                String[] kvps = paramsKvp.split("&");
                for (String kvp : kvps) {
                    if (kvp != null && !"".equals(kvp)) {
                        String[] kv = kvp.split("=");
                        result.put(kv[0], kv[1]);
                    }
                }

                return result;
            }
        });
    }

    private void migrateTileDates(JdbcTemplate template, final FilePathGenerator generator) {
        String query =
                """
                select layers.value as layer, gridsets.value as gridset, \
                tiles.parameters_id, tiles.z, tiles.x, tiles.y, created, formats.value as format\s
                from tiles join layers on layers.id = tiles.layer_id\s
                join gridsets on gridsets.id = tiles.gridset_id\s
                join formats on formats.id = tiles.format_id\s
                order by layer_id, parameters_id, gridset, z, x, y""";

        final long total = Optional.ofNullable(
                        template.queryForObject("select count(*) from (" + query + ")", Long.class))
                .orElse(0l);
        log.info("Migrating " + total + " tile creation dates from the metastore to the file system");

        template.query(query, new RowCallbackHandler() {

            int count = 0;

            @Override
            public void processRow(ResultSet rs) throws SQLException {
                // read the result set
                String layer = rs.getString(1);
                String gridset = rs.getString(2);
                String paramsId = rs.getString(3);
                long z = rs.getLong(4);
                long x = rs.getLong(5);
                long y = rs.getLong(6);
                long created = rs.getLong(7);
                String format = rs.getString(8);

                // create the tile and thus the tile path
                TileObject tile =
                        TileObject.createCompleteTileObject(layer, new long[] {x, y, z}, gridset, format, null, null);
                tile.setParametersId(paramsId);
                try {
                    File file = generator.tilePath(tile, MimeType.createFromFormat(format));

                    // update the last modified according to the date
                    if (file.exists()) {
                        file.setLastModified(created);
                    }
                } catch (MimeException e) {
                    log.log(
                            Level.SEVERE,
                            "Failed to locate mime type for format '" + format + "', this should never happen!");
                } catch (GeoWebCacheException e) {
                    log.log(Level.SEVERE, "Failed to compute tile path", e);
                }

                count++;
                if (count % 10000 == 0 || count >= total) {
                    log.info("Migrated "
                            + count
                            + "/"
                            + total
                            + " tile creation dates from the metastore to the file system");
                }
            }
        });
    }

    private String getVariable(String variable, String defaultValue) {
        String value = storageFinder.findEnvVar(DefaultStorageFinder.GWC_METASTORE_USERNAME);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    private Connection getMetaStoreConnection(File root) throws ClassNotFoundException, SQLException {
        try {
            String username = getVariable(DefaultStorageFinder.GWC_METASTORE_USERNAME, "sa");
            String password = getVariable(DefaultStorageFinder.GWC_METASTORE_PASSWORD, "");
            String defaultJDBCURL = getDefaultJDBCURL(root);
            String jdbcString = getVariable(DefaultStorageFinder.GWC_METASTORE_JDBC_URL, defaultJDBCURL);
            String driver = getVariable(DefaultStorageFinder.GWC_METASTORE_DRIVER_CLASS, "org.h2.Driver");

            if (defaultJDBCURL.equals(jdbcString)) {
                defaultLocation = true;
            }

            // load the driver
            Class.forName(driver);

            // if we are going against a H2 metastore open it only if it exists,
            // otherwise the only way to know if the metastore is still there
            // is to actually try to open it
            if ("org.h2.Driver".equals(driver) && jdbcString.equals(defaultJDBCURL)) {
                File dbPath = getDefaultH2Path(root);
                if (!dbPath.exists()) {
                    return null;
                }
            }

            // grab the connection
            return DriverManager.getConnection(jdbcString, username, password);
        } catch (ClassNotFoundException e) {
            Level logLevel = Level.WARNING;
            if (getVariable(DefaultStorageFinder.GWC_METASTORE_DRIVER_CLASS, null) == null) logLevel = Level.FINE;
            log.log(logLevel, "Could not find the metastore driver, skipping migration", e);
            return null;
        } catch (SQLException e) {
            log.log(Level.WARNING, "Failed to connect to the legacy metastore, skipping migration", e);
            return null;
        }
    }

    private File getDefaultH2Path(File root) {
        String path = root.getPath() + File.separator + "meta_jdbc_h2";
        File dbPath = new File(path + File.separator + "gwc_metastore.data.db");
        return dbPath;
    }

    private String getDefaultJDBCURL(File root) {
        String path = root.getPath() + File.separator + "meta_jdbc_h2";
        String jdbcString = "jdbc:h2:file:" + path + File.separator + "gwc_metastore" + ";TRACE_LEVEL_FILE=0";
        return jdbcString;
    }
}
