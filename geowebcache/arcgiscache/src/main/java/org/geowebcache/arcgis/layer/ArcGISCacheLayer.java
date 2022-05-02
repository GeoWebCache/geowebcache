/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.arcgis.layer;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.arcgis.compact.ArcGISCompactCache;
import org.geowebcache.arcgis.compact.ArcGISCompactCacheV1;
import org.geowebcache.arcgis.compact.ArcGISCompactCacheV2;
import org.geowebcache.arcgis.config.CacheInfo;
import org.geowebcache.arcgis.config.CacheInfoPersister;
import org.geowebcache.arcgis.config.CacheStorageInfo;
import org.geowebcache.arcgis.config.LODInfo;
import org.geowebcache.arcgis.config.TileCacheInfo;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/**
 * {@link org.geowebcache.layer.TileLayer} implementation for ArcGIS tile layers
 *
 * @author Gabriel Roldan
 */
public class ArcGISCacheLayer extends AbstractTileLayer {

    private static final Logger log = Logging.getLogger(ArcGISCacheLayer.class.getName());

    /*
     * configuration properties
     */

    private Boolean enabled;

    /** The location of the conf.xml tiling scheme configuration file */
    private File tilingScheme;

    /**
     * Optional, location of the actual tiles folder. If not provided defaults to the {@code
     * _alllayers} directory at the same location than the {@link #getTilingScheme() conf.xml}
     * tiling scheme.
     */
    private File tileCachePath;

    /**
     * Optional, configure whether or not the z-values should be hex-encoded or not. If not provided
     * defaults to false
     */
    private Boolean hexZoom;

    private transient CacheInfo cacheInfo;

    private transient BoundingBox layerBounds;

    private String storageFormat;

    private transient ArcGISCompactCache compactCache;

    @VisibleForTesting
    ArcGISCacheLayer(String name) {
        this.name = name;
    }

    /** @return {@code null}, this kind of layer handles its own storage. */
    @Override
    public String getBlobStoreId() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public File getTilingScheme() {
        return tilingScheme;
    }

    public void setTilingScheme(final File tilingScheme) {
        this.tilingScheme = tilingScheme;
    }

    /**
     * Returns the location of the actual tiles folder, or {@code null} if not provided, in which
     * case defaults internally to the {@code _alllayers} directory at the same location than the
     * {@link #getTilingScheme() conf.xml} tiling scheme.
     */
    public File getTileCachePath() {
        return tileCachePath;
    }

    /**
     * Options, location of the actual tiles folder. If not provided defaults to the {@code
     * _alllayers} directory at the same location than the {@link #getTilingScheme() conf.xml}
     * tiling scheme.
     */
    public void setTileCachePath(File tileCachePath) {
        this.tileCachePath = tileCachePath;
    }

    public boolean isHexZoom() {
        return hexZoom;
    }

    public void setHexZoom(boolean hexZoom) {
        this.hexZoom = hexZoom;
    }

    /**
     * @return {@code true} if success. Note this method's return type should be void. It's not
     *     checked anywhere
     * @see org.geowebcache.layer.TileLayer#initialize(org.geowebcache.grid.GridSetBroker)
     */
    @Override
    protected boolean initializeInternal(GridSetBroker gridSetBroker) {
        if (this.enabled == null) {
            this.enabled = true;
        }
        if (this.tilingScheme == null) {
            throw new IllegalStateException(
                    "tilingScheme has not been set. It should point to the ArcGIS "
                            + "cache tiling scheme file for this layer (conf.xml)");
        }
        if (tileCachePath != null) {
            if (!tileCachePath.exists()
                    || !tileCachePath.isDirectory()
                    || !tileCachePath.canRead()) {
                throw new IllegalStateException(
                        "tileCachePath property for layer '"
                                + getName()
                                + "' is set to '"
                                + tileCachePath
                                + "' but the directory either does not exist or is not readable");
            }
        }
        if (this.hexZoom == null) {
            this.hexZoom = false;
        }
        try {
            CacheInfoPersister tilingSchemeLoader = new CacheInfoPersister();
            cacheInfo = tilingSchemeLoader.load(new FileReader(tilingScheme));
            File layerBoundsFile = new File(tilingScheme.getParentFile(), "conf.cdi");
            if (!layerBoundsFile.exists()) {
                throw new RuntimeException(
                        "Layer bounds file not found: " + layerBoundsFile.getAbsolutePath());
            }
            log.info("Parsing layer bounds for " + getName());
            this.layerBounds = tilingSchemeLoader.parseLayerBounds(new FileReader(layerBoundsFile));
            log.info("Parsed layer bounds for " + getName() + ": " + layerBounds);

            storageFormat = cacheInfo.getCacheStorageInfo().getStorageFormat();
            if (storageFormat.equals(CacheStorageInfo.COMPACT_FORMAT_CODE)
                    || storageFormat.equals(CacheStorageInfo.COMPACT_FORMAT_CODE_V2)) {
                String pathToCacheRoot = tilingScheme.getParent() + "/_alllayers";
                if (tileCachePath != null) pathToCacheRoot = tileCachePath.getAbsolutePath();

                if (storageFormat.equals(CacheStorageInfo.COMPACT_FORMAT_CODE)) {
                    log.info(getName() + " uses compact format (ArcGIS 10.0 - 10.2)");
                    compactCache = new ArcGISCompactCacheV1(pathToCacheRoot);
                } else if (storageFormat.equals(CacheStorageInfo.COMPACT_FORMAT_CODE_V2)) {
                    log.info(getName() + " uses compact format (ArcGIS 10.3)");
                    compactCache = new ArcGISCompactCacheV2(pathToCacheRoot);
                }
            }
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(
                    "Tiling scheme file not found: " + tilingScheme.getAbsolutePath());
        }
        log.info(
                "Configuring layer "
                        + getName()
                        + " out of the ArcGIS tiling scheme "
                        + tilingScheme.getAbsolutePath());

        super.subSets = createGridSubsets(gridSetBroker);
        super.formats = loadMimeTypes();

        return true;
    }

    private List<MimeType> loadMimeTypes() {
        String cacheTileFormat = this.cacheInfo.getTileImageInfo().getCacheTileFormat();
        if ("mixed".equalsIgnoreCase(cacheTileFormat) || "jpg".equalsIgnoreCase(cacheTileFormat)) {
            cacheTileFormat = "JPEG";
        } else if (cacheTileFormat.toLowerCase().startsWith("png")) {
            cacheTileFormat = "png";
        }
        cacheTileFormat = "image/" + cacheTileFormat.toLowerCase();
        MimeType format;
        try {
            format = MimeType.createFromFormat(cacheTileFormat);
        } catch (MimeException e) {
            throw new RuntimeException(e);
        }
        return Collections.singletonList(format);
    }

    private HashMap<String, GridSubset> createGridSubsets(final GridSetBroker gridSetBroker) {

        final CacheInfo info = this.cacheInfo;
        final TileCacheInfo tileCacheInfo = info.getTileCacheInfo();

        final String layerName = getName();
        final GridSetBuilder gsBuilder = new GridSetBuilder();
        GridSet gridSet = gsBuilder.buildGridset(layerName, info, layerBounds);

        gridSetBroker.put(gridSet);

        final List<LODInfo> lodInfos = tileCacheInfo.getLodInfos();
        Integer zoomStart = lodInfos.get(0).getLevelID();
        Integer zoomStop = lodInfos.get(lodInfos.size() - 1).getLevelID();

        GridSubset subSet =
                GridSubsetFactory.createGridSubSet(gridSet, this.layerBounds, zoomStart, zoomStop);

        HashMap<String, GridSubset> subsets = new HashMap<>();
        subsets.put(gridSet.getName(), subSet);
        return subsets;
    }

    /** @see org.geowebcache.layer.TileLayer#getTile(org.geowebcache.conveyor.ConveyorTile) */
    @Override
    public ConveyorTile getTile(final ConveyorTile tile)
            throws GeoWebCacheException, IOException, OutsideCoverageException {

        Resource tileContent = null;

        if (storageFormat.equals(CacheStorageInfo.COMPACT_FORMAT_CODE)
                || storageFormat.equals(CacheStorageInfo.COMPACT_FORMAT_CODE_V2)) {
            final long[] tileIndex = tile.getTileIndex();
            final String gridSetId = tile.getGridSetId();
            final GridSubset gridSubset = this.getGridSubset(gridSetId);

            GridSet gridSet = gridSubset.getGridSet();
            final int zoom = (int) tileIndex[2];

            Grid grid = gridSet.getGrid(zoom);
            long coverageMaxY = grid.getNumTilesHigh() - 1;

            final int col = (int) tileIndex[0];
            final int row = (int) (coverageMaxY - tileIndex[1]);

            tileContent = compactCache.getBundleFileResource(zoom, row, col);

        } else if (storageFormat.equals(CacheStorageInfo.EXPLODED_FORMAT_CODE)) {
            String path = getTilePath(tile);
            File tileFile = new File(path);

            if (tileFile.exists()) {
                tileContent = readFile(tileFile);
            }
        }

        if (tileContent != null) {
            tile.setCacheResult(CacheResult.HIT);
            tile.setBlob(tileContent);
        } else {
            tile.setCacheResult(CacheResult.MISS);
            if (!setLayerBlankTile(tile)) {
                throw new OutsideCoverageException(tile.getTileIndex(), 0, 0);
            }
        }

        // TODO Add here
        saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));

        return tile;
    }

    protected void saveExpirationInformation(int backendExpire) {
        this.saveExpirationHeaders = false;

        try {
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireCacheList.set(0, new ExpirationRule(0, 7200));
                    log.log(
                            Level.SEVERE,
                            "Layer profile wants MaxAge from backend,"
                                    + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireCacheList.set(backendExpire, new ExpirationRule(0, 7200));
                }
                log.finer("Setting expireCache to: " + expireCache);
            }
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireClientsList.set(0, new ExpirationRule(0, 7200));
                    log.log(
                            Level.SEVERE,
                            "Layer profile wants MaxAge from backend,"
                                    + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireClientsList.set(0, new ExpirationRule(0, backendExpire));
                    log.finer("Setting expireClients to: " + expireClients);
                }
            }
        } catch (Exception e) {
            // Sometimes this doesn't work (network conditions?),
            // and it's really not worth getting caught up on it.
            log.log(Level.FINE, e.getMessage(), e);
        }
    }

    private boolean setLayerBlankTile(ConveyorTile tile) {
        // TODO cache result
        String layerPath = getLayerPath().append(File.separatorChar).toString();
        File png = new File(layerPath + "blank.png");
        Resource blank = null;
        try {
            if (png.exists()) {
                blank = readFile(png);
                tile.setBlob(blank);
                tile.setMimeType(MimeType.createFromFormat("image/png"));
            } else {
                File jpeg = new File(layerPath + "missing.jpg");
                if (jpeg.exists()) {
                    blank = readFile(jpeg);
                    tile.setBlob(blank);
                    tile.setMimeType(MimeType.createFromFormat("image/jpeg"));
                }
            }
        } catch (Exception e) {
            return false;
        }
        return blank != null;
    }

    private String getTilePath(final ConveyorTile tile) {

        final MimeType mimeType = tile.getMimeType();
        final long[] tileIndex = tile.getTileIndex();
        final String gridSetId = tile.getGridSetId();
        final GridSubset gridSubset = this.getGridSubset(gridSetId);

        GridSet gridSet = gridSubset.getGridSet();
        final int z = (int) tileIndex[2];

        Grid grid = gridSet.getGrid(z);

        // long[] coverage = gridSubset.getCoverage(z);
        // long coverageMinY = coverage[1];
        long coverageMaxY = grid.getNumTilesHigh() - 1;

        final long x = tileIndex[0];
        // invert the order of the requested Y ordinate, since ArcGIS caches are top-left to
        // bottom-right, and GWC computes tiles in bottom-left to top-right order
        final long y = (coverageMaxY - tileIndex[1]);

        String level = (this.hexZoom) ? Integer.toHexString(z) : Integer.toString(z);
        level = zeroPadder(level, 2);

        String row = Long.toHexString(y);
        row = zeroPadder(row, 8);

        String col = Long.toHexString(x);
        col = zeroPadder(col, 8);

        StringBuilder path = getLayerPath();

        path.append(File.separatorChar)
                .append('L')
                .append(level)
                .append(File.separatorChar)
                .append('R')
                .append(row)
                .append(File.separatorChar)
                .append('C')
                .append(col);

        String fileExtension = mimeType.getFileExtension();
        if ("jpeg".equalsIgnoreCase(fileExtension)) {
            fileExtension = "jpg";
        }
        path.append('.').append(fileExtension);

        return path.toString();
    }

    private StringBuilder getLayerPath() {
        StringBuilder path;
        if (tileCachePath == null) {
            path = new StringBuilder(this.tilingScheme.getParent());
            // note we're assuming it's a "fused" tile cache. When it comes to support multiple
            // layers
            // tile caches we'll need to parametrize the layer's cache directory
            path.append(File.separatorChar).append("_alllayers");
        } else {
            path = new StringBuilder(tileCachePath.getAbsolutePath());
        }
        return path;
    }

    private String zeroPadder(String s, int order) {
        if (s.length() >= order) {
            return s;
        }
        char[] data = new char[order];
        Arrays.fill(data, '0');

        for (int i = s.length() - 1, j = order - 1; i >= 0; i--, j--) {
            data[j] = s.charAt(i);
        }
        return String.valueOf(data);
    }

    private Resource readFile(File fh) {
        if (!fh.exists()) {
            return null;
        }
        Resource res = new FileResource(fh);
        return res;
    }

    /**
     * @see org.geowebcache.layer.TileLayer#getNoncachedTile(org.geowebcache.conveyor.ConveyorTile)
     */
    @Override
    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        throw new UnsupportedOperationException();
    }

    /**
     * @see org.geowebcache.layer.TileLayer#seedTile(org.geowebcache.conveyor.ConveyorTile, boolean)
     */
    @Override
    public void seedTile(ConveyorTile tile, boolean tryCache)
            throws GeoWebCacheException, IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @see
     *     org.geowebcache.layer.TileLayer#doNonMetatilingRequest(org.geowebcache.conveyor.ConveyorTile)
     */
    @Override
    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        throw new UnsupportedOperationException();
    }

    /** @see org.geowebcache.layer.TileLayer#getStyles() */
    @Override
    public String getStyles() {
        return null;
    }

    /**
     * @see
     *     org.geowebcache.layer.TileLayer#setExpirationHeader(javax.servlet.http.HttpServletResponse,
     *     int)
     */
    @Override
    public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {
        /*
         * NOTE: this method doesn't seem like belonging to TileLayer, but to GeoWebCacheDispatcher
         * itself
         */
    }
}
