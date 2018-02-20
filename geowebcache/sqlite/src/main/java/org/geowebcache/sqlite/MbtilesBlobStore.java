/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.mbtiles.GeoToolsMbtilesUtils;
import org.geotools.mbtiles.MBTilesFile;
import org.geotools.mbtiles.MBTilesMetadata;
import org.geotools.mbtiles.MBTilesMetadata.t_format;
import org.geotools.mbtiles.MBTilesTile;
import org.geotools.sql.SqlUtil;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Blobstore that store the tiles in a sqlite database using the mbtiles specification.
 */
public final class MbtilesBlobStore extends SqliteBlobStore {

    private static Log LOGGER = LogFactory.getLog(MbtilesBlobStore.class);

    // pattern for matching the name of a file that contains mbtiles metadata (layerName.properties)
    private final static Pattern MBTILES_METADATA_FILE_NAME_PATTERN = Pattern.compile("(.*?)\\.properties");

    // sqlite database that will contain layers metadata
    private final File metadataFile;

    // if true we will prefer delete a file rather than delete only a few tiles (avoiding vacuum)
    private final boolean eagerDelete;

    // if false we will not care about tile create time (expiration rules will not work)
    private final boolean useCreateTime;

    private final BlobStoreListenerList listeners;

    // parsed mbtiles metadata indexed per layer
    private final Map<String, MBTilesMetadata> layersMetadata = new ConcurrentHashMap<>();

    // Executor that can be used to perform parallel operations
    private final ExecutorService executorService;

    // Apply GZIP compression to uncompressed vector tile formats.
    private final boolean gzipVector;
    
    MbtilesBlobStore(MbtilesInfo configuration) {
        // caution this constructor will create a new connection pool
        this(configuration, new SqliteConnectionManager(
                configuration.getPoolSize(), configuration.getPoolReaperIntervalMs()));
    }

    public MbtilesBlobStore(MbtilesInfo configuration, SqliteConnectionManager connectionManager) {
        super(configuration, connectionManager);
        metadataFile = new File(configuration.getRootDirectoryFile(), "metadata.sqlite");
        eagerDelete = configuration.eagerDelete();
        useCreateTime = configuration.useCreateTime();
        executorService = Executors.newFixedThreadPool(configuration.getExecutorConcurrency());
        listeners = new BlobStoreListenerList();
        gzipVector = configuration.isGzipVector();
        initMbtilesLayersMetadata(configuration.getMbtilesMetadataDirectory());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("MBTiles blob store initiated: [eagerDelete='%b', useCreateTime='%b'.", eagerDelete, useCreateTime));
        }
    }
    
    private boolean tileIsGzipped(TileObject tile) throws MimeException {
        return gzipVector && MimeType.createFromFormat(tile.getBlobFormat()).isVector();
    }
    
    @Override
    public void put(TileObject tile) throws StorageException {
        File file = fileManager.getFile(tile);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Tile '%s' mapped to file '%s'.", tile, file));
        }
        initDatabaseFileIfNeeded(file, tile.getLayerName(), tile.getBlobFormat());
        // do work in write mode
        connectionManager.doWork(file, false, connection -> {
            // instantiating geotools needed objects
            MBTilesFile mbtiles = GeoToolsMbtilesUtils.getMBTilesFile(connection, file);
            MBTilesTile gtTile = new MBTilesTile(tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]);
            try {
                final boolean gzipped = tileIsGzipped(tile);
                
                byte[] bytes;
                if (gzipped) {
                    try (
                            ByteArrayOutputStream byteStream  = new ByteArrayOutputStream();
                            GZIPOutputStream gzOut = new GZIPOutputStream(byteStream);
                    ) {
                        bytes = byteStream.toByteArray();
                    }
                } else {
                    bytes = Utils.resourceToByteArray(tile.getBlob());
                }
                gtTile.setData(bytes);
                
                // if necessary getting old data size for listeners
                byte[] olData = null;
                if (!listeners.isEmpty()) {
                    olData = mbtiles.loadTile(tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]).getData();
                }
                // saving the tile
                mbtiles.saveTile(gtTile);
                if (useCreateTime) {
                    // we need to store this tile create time
                    putTileCreateTime(connection, tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1], System.currentTimeMillis());
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Tile '%s' saved in file '%s'.", tile, file));
                }
                if (listeners.isEmpty()) {
                    // no listeners to update we are done
                    return;
                }
                if (olData == null) {
                    // this was new tile
                    listeners.sendTileStored(tile);
                } else {
                    // this an update
                    listeners.sendTileUpdated(tile, olData.length);
                }
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error saving tile '%s' in file '%s'.", tile, file);
            }
        });
        
        persistParameterMap(tile);
    }

    
    @Override
    public boolean get(final TileObject tile) throws StorageException {
        File file = fileManager.getFile(tile);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Tile '%s' mapped to file '%s'.", tile, file));
        }
        initDatabaseFileIfNeeded(file, tile.getLayerName(), tile.getBlobFormat());
        // do work in readonly mode
        boolean exists = connectionManager.doWork(file, true, connection -> {
            // instantiating geotools mbtiles reader
            MBTilesFile mbtiles = GeoToolsMbtilesUtils.getMBTilesFile(connection, file);
            try {
                
                final boolean gzipped = tileIsGzipped(tile);
                
                // loading the tile using geotools reader
                MBTilesTile gtTile = mbtiles.loadTile(tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]);
                
                byte[] bytes = gtTile.getData();
                if (gtTile.getData() != null) {
                    if (gzipped) {
                        try (
                                ByteArrayOutputStream byteOut  = new ByteArrayOutputStream();
                                ByteArrayInputStream byteIn  = new ByteArrayInputStream(gtTile.getData());
                                GZIPInputStream gzIn = new GZIPInputStream(byteIn);
                        ) {
                            IOUtils.copy(gzIn, byteOut);
                            bytes = byteOut.toByteArray();
                        }
                    } else {
                        bytes = gtTile.getData();
                    }
                    tile.setBlob(Utils.byteArrayToResource(bytes));
                    
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(String.format("Tile '%s' found on file '%s'.", tile, file));
                    }
                    return true;
                }
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error loading tile '%s' from MBTiles file '%s'.", tile, file);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Tile '%s' not found on file '%s'.", tile, file));
            }
            return false;
        });
        if (exists && useCreateTime) {
            // the tile exists and we need to set the create time in the tile object
            Long createdTime = getTileCreateTime(file, tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]);
            if (createdTime == null) {
                // no create time associated with this tile let's assume the last modified time
                createdTime = file.lastModified();
                // update the create time
                putTileCreateTime(file, tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1], createdTime);
            }
            tile.setCreated(createdTime);
        } else if (exists) {
            // we don't care about the create time, tile will never expire
            tile.setCreated(System.currentTimeMillis());
        }
        return exists;
    }

    @Override
    public boolean delete(TileObject tile) throws StorageException {
        File file = fileManager.getFile(tile);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Tile '%s' mapped to file '%s'.", tile, file));
        }
        if (!file.exists()) {
            // database file doesn't exists so nothing to do
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Containing file '%s' for tile '%s' doesn't exists.", file, tile));
            }
            return false;
        }
        // do work on write mode
        return connectionManager.doWork(file, false, connection -> {
            // instantiating geotools objects without setting the tile data (this way geotools will try to remove the tile)
            MBTilesFile mbtiles = GeoToolsMbtilesUtils.getMBTilesFile(connection, file);
            MBTilesTile gtTile = new MBTilesTile(tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]);
            try {
                // getting tile old data and checking if the tile exists
                byte[] olData = mbtiles.loadTile(tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]).getData();
                if (olData != null) {
                    // tile exists so let's remove the tile
                    tile.setBlobSize(olData.length);
                    mbtiles.saveTile(gtTile);
                    // updating the listener if any
                    listeners.sendTileDeleted(tile);
                    if (useCreateTime) {
                        // we care about the create time so let's remove it
                        deleteTileCreateTime(connection, tile.getXYZ()[2], tile.getXYZ()[0], tile.getXYZ()[1]);
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(String.format("Tile '%s' deleted from file '%s'.", tile, file));
                    }
                    return true;
                }
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error deleting tile '%s' from MBTiles file '%s'.", tile, file);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Tile '%s' not found on file '%s'.", tile, file));
            }
            return false;
        });
    }

    @Override
    public synchronized void putLayerMetadata(String layerName, String key, String value) {
        // storing metadata associated with a layer in the metadata file
        connectionManager.executeSql(metadataFile,
                "CREATE TABLE IF NOT EXISTS metadata (layerName text, key text, value text, PRIMARY KEY(layerName, key));");
        connectionManager.executeSql(metadataFile,
                "INSERT OR REPLACE INTO metadata VALUES (?, ?, ?);", layerName, key, value);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Metadata for layer '%s' for key '%s' inserted or updated on file '%s'.",
                    layerName, key, metadataFile));
        }
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        try {
            return connectionManager.executeQuery(metadataFile, resultSet -> {
                        try {
                            if (resultSet.next()) {
                                // metadata value is available
                                String value = resultSet.getString(1);
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(String.format("Metadata for layer '%s' with key '%s' found '%s'.",
                                            layerName, key, value));
                                }
                                return value;
                            }
                            // metadata value not found
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug(String.format("Metadata for layer '%s' with key '%s' not found.", layerName, key));
                            }
                            return null;
                        } catch (Exception exception) {
                            throw Utils.exception(exception, "Error reading result set.");
                        }
                    },
                    "SELECT value FROM metadata WHERE layerName = ? AND key = ?;", layerName, key);
        } catch (Exception exception) {
            // probably because the metadata table doesn't exists
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(String.format("Error getting metadata from file '%s'.", metadataFile), exception);
            }
            return null;
        }
    }

    @Override
    public boolean layerExists(String layerName) {
        // a layer exists if there is at least one file associated to it
        return !fileManager.getFiles(layerName).isEmpty();
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        boolean deleted = deleteFiles(fileManager.getFiles(layerName));
        listeners.sendLayerDeleted(layerName);
        return deleted;
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        boolean deleted = deleteFiles(fileManager.getFiles(layerName, gridSetId));
        listeners.sendGridSubsetDeleted(layerName, gridSetId);
        return deleted;
    }
    
    @Override
    public boolean deleteByParametersId(String layerName, String parametersId) throws StorageException {
        boolean deleted = deleteFiles(fileManager.getParametersFiles(layerName, parametersId));
        listeners.sendParametersDeleted(layerName, parametersId);
        return deleted;
    }

    @Override
    public boolean delete(TileRange tileRange) throws StorageException {
        // getting the files associated with this tile range
        Map<File, List<long[]>> files = fileManager.getFiles(tileRange);
        if (files.isEmpty()) {
            // no files so nothing to do
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Nothing to do.");
            }
            return false;
        }
        // let's delete the tiles
        CompletionService completionService = new ExecutorCompletionService(executorService);
        int tasks = 0;
        for (Map.Entry<File, List<long[]>> entry : files.entrySet()) {
            // FIXME: should we tell something to the listeners ?
            File file = entry.getKey();
            if (!file.exists()) {
                // this database file doesn't exists, so nothing to do
                continue;
            }
            if (eagerDelete) {
                // we delete the whole file avoiding fragmentation on the database
                completionService.submit(() -> connectionManager.delete(file), true);
            } else {
                // we need to delete all tiles that belong to the tiles range and are stored in the current file
                for (long[] range : entry.getValue()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(String.format("Deleting tiles range [minx=%d, miny=%d, maxx=%d, maxxy=%d, zoom=%d] in file '%s'.",
                                range[0], range[1], range[2], range[3], range[4], file));
                    }
                    completionService.submit(() -> connectionManager.executeSql(file,
                            "DELETE FROM tiles WHERE zoom_level = ? AND tile_column BETWEEN ? AND ? AND tile_row BETWEEN ? AND ?;",
                            range[4], range[0], range[2], range[1], range[3]), true);
                }
            }
            tasks++;
        }
        // let's wait for the tasks to finish
        for (int i = 0; i < tasks; i++) {
            try {
                completionService.take().get();
            } catch (Exception exception) {
                throw Utils.exception(exception, "Something bad happen when deleting tile range.");
            }
        }
        return true;
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        List<File> files = fileManager.getFiles(oldLayerName);
        if (files.isEmpty()) {
            return false;
        }
        for (File currentFile : files) {
            String normalizedLayerName = FileManager.normalizePathValue(newLayerName);
            File newFile = new File(currentFile.getPath().replace(oldLayerName, normalizedLayerName));
            connectionManager.rename(currentFile, newFile);
        }
        listeners.sendLayerRenamed(oldLayerName, newLayerName);
        return true;
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return listeners.removeListener(listener);
    }

    @Override
    public void clear() throws StorageException {
        connectionManager.reapAllConnections();
    }

    @Override
    public void destroy() {
        connectionManager.reapAllConnections();
        connectionManager.stopPoolReaper();
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error when waiting for executor task to finish.", exception);
            }
        }
    }

    /**
     * Helper method that delete the provided files.
     */
    private boolean deleteFiles(List<File> files) throws StorageException {
        if (files.isEmpty()) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("No files to delete.");
            }
            return false;
        }
        // asking the connection manager to remove the database files
        CompletionService completionService = new ExecutorCompletionService(executorService);
        int tasks = 0;
        for (File file : files) {
            completionService.submit(() -> connectionManager.delete(file), true);
            tasks++;
        }
        // let's wait for the tasks to finish
        for (int i = 0; i < tasks; i++) {
            try {
                completionService.take().get();
            } catch (Exception exception) {
                throw Utils.exception(exception, "Something bad happen when deleting files.");
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Files deleted.");
        }
        return true;
    }

    /**
     * Helper method that deletes the create time of a tile.
     */
    private void deleteTileCreateTime(Connection connection, long z, long x, long y) throws StorageException {
        try {
            connectionManager.executeSql(connection, "DELETE FROM tiles_metadata " +
                    "WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?", z, x, y);
        } catch (Exception exception) {
            // probably the table doesn't exists
            if(LOGGER.isErrorEnabled()) {
                LOGGER.error(String.format("Something bad happen when deleting create time for tile '%d-%d-%d'.", x, y, z), exception);
            }
        }
    }

    /**
     * Helper method that retrieves the create time of a tile.
     */
    private Long getTileCreateTime(File file, long z, long x, long y) throws StorageException {
        String query = "SELECT create_time FROM tiles_metadata WHERE zoom_level = ? " +
                "AND tile_column = ? AND tile_row = ?";
        try {
            return connectionManager.executeQuery(file, resultSet -> {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
                return null;
            }, query, z, x, y);
        } catch (Exception exception) {
            // probably the table doesn't exists
            if(LOGGER.isErrorEnabled()) {
                LOGGER.error(String.format("Something bad happen when querying create time for tile '%d-%d-%d'.", x, y, z), exception);
            }
        }
        return null;
    }

    /**
     * Helper method that puts the create time of a tile opening a connection for it.
     */
    private void putTileCreateTime(File file, long z, long x, long y, long createTime) {
        connectionManager.doWork(file, false, connection -> {
            putTileCreateTime(connection, z, x, y, createTime);
        });
    }

    /**
     * Helper method that puts the create time of a tile using the provided connection.
     */
    private void putTileCreateTime(Connection connection, long z, long x, long y, long createTime) {
        createTilesMetadataTable(connection);
        connectionManager.executeSql(connection,
                "INSERT OR REPLACE INTO tiles_metadata VALUES (?, ?, ?, ?);", z, x, y, createTime);
    }

    private void createTilesMetadataTable(Connection connection) {
        connectionManager.executeSql(connection,
                "CREATE TABLE IF NOT EXISTS tiles_metadata (zoom_level integer, tile_column integer, " +
                        "tile_row integer, create_time integer, " +
                        "CONSTRAINT pk_tiles PRIMARY KEY(zoom_level, tile_column,tile_row));");
    }

    /**
     * Init database file if it doesn't exists.
     */
    void initDatabaseFileIfNeeded(File file, String layerName, String format) {
        if (file.exists()) {
            // database file exists
            return;
        }
        // initiating the database file
        connectionManager.doWork(file, false, (connection) -> {
            try {
                // creating mbtiles tables
                SqlUtil.runScript(getClass().getResourceAsStream("/org/geotools/mbtiles/mbtiles.sql"), connection);
                // create tiles metadata table for storing the create time if needed
                createTilesMetadataTable(connection);
                // insert mbtiles metadata for this layer
                insertMbtilesLayerMetadata(file, connection, layerName, format);
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error running geotools mbtiles sql script.");
            }
        });
    }

    /**
     * Store the mbtiles metadata associated with a file.
     */
    private void insertMbtilesLayerMetadata(File file, Connection connection, String layerName, String format) {
        MBTilesMetadata gtMetadata = new MBTilesMetadata();
        gtMetadata.setName(layerName);
        // checking if we have a mbtiles supported format, otherwise we don't insert anything
        if (format.contains("png")) {
            gtMetadata.setFormat(MBTilesMetadata.t_format.PNG);
        } else if (format.contains("jpeg")) {
            gtMetadata.setFormat(MBTilesMetadata.t_format.JPEG);
        } else if (format.contains("protobuf")) {
            gtMetadata.setFormat(MBTilesMetadata.t_format.PBF);
        }
        MBTilesMetadata existingMetadata = layersMetadata.get(FileManager.normalizePathValue(layerName));
        if (existingMetadata != null) {
            // we have some user provided metadata let's use it
            gtMetadata.setName(layerName);
            gtMetadata.setAttribution(existingMetadata.getAttribution());
            gtMetadata.setBounds(existingMetadata.getBounds());
            gtMetadata.setDescription(existingMetadata.getDescription());
            gtMetadata.setMaxZoom(existingMetadata.getMaxZoom());
            gtMetadata.setMinZoom(existingMetadata.getMinZoom());
            gtMetadata.setType(existingMetadata.getType());
            gtMetadata.setVersion(existingMetadata.getVersion());
        }
        MBTilesFile mbtiles = GeoToolsMbtilesUtils.getMBTilesFile(connection, file);
        try {
            mbtiles.saveMetaData(gtMetadata);
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error storing metadata on file '%s'.", file);
        }
    }

    /**
     * Reads user provided mbtiles metadata for a layer.
     */
    private void initMbtilesLayersMetadata(String mbtilesMetadataDirectoryPath) {
        if (mbtilesMetadataDirectoryPath == null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Mbtiles metadata directory path is NULL, no mbtiles metadata will be parsed.");
            }
            return;
        }
        File mbtilesMetadataDirectory = new File(mbtilesMetadataDirectoryPath);
        if (!mbtilesMetadataDirectory.exists()) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format("Mbtiles metadata directory '%s' doesn't exists, no mbtiles metadata will be parsed.",
                        mbtilesMetadataDirectoryPath));
            }
            return;
        }
        File[] files = mbtilesMetadataDirectory.listFiles();
        if (files == null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format("No files present in mbtiles metadata directory '%s', no mbtiles metadata will be parsed.",
                        mbtilesMetadataDirectoryPath));
            }
            return;
        }
        for (File file : files) {
            Matcher matcher = MBTILES_METADATA_FILE_NAME_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                // extracting the layer name
                String layerName = matcher.group(1);
                // parsing mbtiles metadata properties
                Properties metadata = new Properties();
                try (InputStream input = new FileInputStream(file)) {
                    metadata.load(input);
                } catch (Exception exception) {
                    throw Utils.exception(exception, "Error reading mbtiles metadata file '%s'.", file);
                }
                // creating geotools mbtiles metadata file
                MBTilesMetadata gtMetadata = new MBTilesMetadata();
                gtMetadata.setAttribution(metadata.getProperty("attribution"));
                gtMetadata.setBoundsStr(metadata.getProperty("bounds"));
                gtMetadata.setDescription(metadata.getProperty("description"));
                gtMetadata.setMaxZoomStr(metadata.getProperty("maxZoom"));
                gtMetadata.setMinZoomStr(metadata.getProperty("minZoom"));
                gtMetadata.setTypeStr(metadata.getProperty("type"));
                gtMetadata.setVersion(metadata.getProperty("version"));
                // index the parsed mbtiles metadata
                layersMetadata.put(layerName, gtMetadata);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Parsed mbtiles metadata for layer '%s'.", layerName));
                }
            }
        }
    }
    
    public Map<String,Optional<Map<String, String>>> getParametersMapping(String layerName) {
        try {
            return (Map<String,Optional<Map<String, String>>>)connectionManager.executeQuery(metadataFile, resultSet -> {
                        try {
                            Map<String, Optional<Map<String, String>>> result = new HashMap<>();
                            while(resultSet.next()) {
                                Map<String, String> params = ParametersUtils.getMap(resultSet.getString(1));
                                result.put(ParametersUtils.getId(params), Optional.of(params));
                            }
                            return result;
                        } catch (Exception exception) {
                            throw Utils.exception(exception, "Error reading result set.");
                        }
                    },
                    "SELECT value FROM metadata WHERE layerName = ? AND key like 'parameters.%';", layerName);
        } catch (Exception exception) {
            // probably because the metadata table doesn't exists
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(String.format("Error getting metadata from file '%s'.", metadataFile), exception);
            }
            return Collections.emptyMap();
        }
    }
    
    protected void persistParameterMap(TileObject stObj) {
        if(Objects.nonNull(stObj.getParametersId())) {
            putLayerMetadata(
                    stObj.getLayerName(), 
                    "parameters."+stObj.getParametersId(), 
                    ParametersUtils.getKvp(stObj.getParameters()));
        }
    }

}
