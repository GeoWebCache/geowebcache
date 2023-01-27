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
 * <p>Copyright 2021
 */
package org.geowebcache.mbtiles.layer;

import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.geotools.mbtiles.MBTilesFile;
import org.geotools.mbtiles.MBTilesMetadata;
import org.geotools.mbtiles.MBTilesTile;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.EmptyTileException;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.TileJSONProvider;
import org.geowebcache.layer.meta.TileJSON;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/**
 * {@link org.geowebcache.layer.TileLayer} implementation for MBtiles layers
 *
 * @author Daniele Romagnoli
 */
public class MBTilesLayer extends AbstractTileLayer implements TileJSONProvider {

    private static final String UNZIP_CONTENT_KEY = "gwc.mbtiles.pbf.unzip.debug";

    private static final boolean UNZIP_CONTENT =
            Boolean.valueOf(System.getProperty(UNZIP_CONTENT_KEY, "true"));

    private static final int TILE_SIZE_256 = 256;

    private static final int TILE_SIZE_512 = 512;

    private static final int DEFAULT_TILE_SIZE = TILE_SIZE_256;

    private static final Logger log = Logging.getLogger(MBTilesLayer.class.getName());

    /*
     * configuration properties
     */
    private Boolean enabled;

    private File tilesPath;

    private int tileSize = DEFAULT_TILE_SIZE;

    private transient MBTilesInfo tilesInfo;

    private transient BoundingBox layerBounds;

    private MimeType mimeType;

    private MBTilesFile mbTilesFile;

    private boolean vectorTiles;

    @VisibleForTesting
    MBTilesLayer(String name) {
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

    /** Returns the location of the actual tiles set. */
    public File getTilesPath() {
        return tilesPath;
    }

    public void setTilesPath(File tilesPath) {
        this.tilesPath = tilesPath;
    }

    public int getTileSize() {
        return tileSize;
    }

    public void setTileSize(int tileSize) {
        this.tileSize = tileSize;
    }

    public boolean isVectorTiles() {
        return vectorTiles;
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
        String specifiedName = getName();
        if (tilesPath != null) {
            if (!tilesPath.exists() || !tilesPath.canRead()) {
                throw new IllegalStateException(
                        "tilesPath property for this layer "
                                + (specifiedName != null ? specifiedName : "")
                                + " is set to '"
                                + tilesPath
                                + "' but the file either does not exist or is not readable");
            }
        }
        try {
            mbTilesFile = new MBTilesFile(tilesPath);
            tilesInfo = new MBTilesInfo(mbTilesFile);
            layerBounds = tilesInfo.getBounds();
            if (StringUtils.isEmpty(specifiedName)) {
                name = tilesInfo.getMetadataName();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open the Provided MBTile: " + tilesPath);
        }

        super.subSets = createGridSubsets(gridSetBroker);
        super.formats = loadMimeTypes();
        return true;
    }

    private List<MimeType> loadMimeTypes() {
        MBTilesMetadata.t_format metadataFormat = tilesInfo.getFormat();

        switch (metadataFormat) {
            case PNG:
                mimeType = ImageMime.png;
                break;
            case JPEG:
            case JPG:
                mimeType = ImageMime.jpeg;
                break;
            case PBF:
                mimeType = ApplicationMime.mapboxVector;
                vectorTiles = true;
                break;
        }
        return Collections.singletonList(mimeType);
    }

    private HashMap<String, GridSubset> createGridSubsets(final GridSetBroker gridSetBroker) {
        GridSet gridSet;

        DefaultGridsets defaultGridSet = new DefaultGridsets(true, true);

        if (tileSize <= 0) {
            tileSize = DEFAULT_TILE_SIZE;
        }

        switch (tileSize) {
            case TILE_SIZE_256:
                gridSet = defaultGridSet.worldEpsg3857();
                break;
            case TILE_SIZE_512:
                gridSet = defaultGridSet.worldEpsg3857x2();
                break;
            default:
                throw new IllegalArgumentException("Unsupported tileSize: " + tileSize);
        }
        Integer minZoom = tilesInfo.getMinZoom();
        Integer maxZoom = tilesInfo.getMaxZoom();

        GridSubset subSet =
                GridSubsetFactory.createGridSubSet(gridSet, this.layerBounds, minZoom, maxZoom);

        HashMap<String, GridSubset> subsets = new HashMap<>();
        subsets.put(gridSet.getName(), subSet);
        return subsets;
    }

    /** @see org.geowebcache.layer.TileLayer#getTile(org.geowebcache.conveyor.ConveyorTile) */
    @Override
    public ConveyorTile getTile(final ConveyorTile tile) throws IOException, GeoWebCacheException {

        long[] tileIndex = tile.getTileIndex();

        // check request is within coverage
        String tileGridSetId = tile.getGridSetId();
        GridSubset gridSubset = getGridSubset(tileGridSetId);
        gridSubset.checkCoverage(tileIndex);

        int zl = (int) tileIndex[2];
        int row = (int) tileIndex[1];
        int column = (int) tileIndex[0];
        MBTilesTile loadedTile = mbTilesFile.loadTile(zl, column, row);
        byte[] content = loadedTile.getData();
        if (content != null) {
            if (tilesInfo.getFormat() == MBTilesMetadata.t_format.PBF) {
                content = getPbfFromTile(content);
            }

            tile.setBlob(new ByteArrayResource(content));
            tile.setCacheResult(CacheResult.HIT);
        } else {
            // not in the file, but still within the coverage
            tile.setCacheResult(CacheResult.MISS);
            throw new EmptyTileException(getLayerMime());
        }

        saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));

        return tile;
    }

    private MimeType getLayerMime() {
        switch (tilesInfo.getFormat()) {
            case PNG:
                return ImageMime.png;
            case JPG:
            case JPEG:
                return ImageMime.jpeg;
            case PBF:
                return ApplicationMime.mapboxVector;
            default:
                // unknown format
                return null;
        }
    }

    private byte[] getPbfFromTile(byte[] raw) throws IOException {
        if (!UNZIP_CONTENT) {
            return raw;
        }
        // GZIP magic number check
        byte[] byteArray = raw;
        if (raw != null && raw.length >= 2 && raw[0] == (byte) 0x1F && raw[1] == (byte) 0x8b) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ByteArrayInputStream bin = new ByteArrayInputStream(raw);
                    InflaterInputStream in = new GZIPInputStream(bin)) {
                byte[] buffer = new byte[1024];
                int noRead;
                while ((noRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, noRead);
                }
                byteArray = out.toByteArray();
            }
        }
        return byteArray;
    }

    protected void saveExpirationInformation(int backendExpire) {
        this.saveExpirationHeaders = false;

        try {
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireCacheList.set(0, new ExpirationRule(0, 7200));
                    if (log.isLoggable(Level.FINE)) {
                        log.fine(
                                "Layer profile wants MaxAge from backend,"
                                        + " but backend does not provide this. Setting to 7200 seconds.");
                    }
                } else {
                    this.expireCacheList.set(backendExpire, new ExpirationRule(0, 7200));
                }
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Setting expireCache to: " + expireCache);
                }
            }
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireClientsList.set(0, new ExpirationRule(0, 7200));
                    if (log.isLoggable(Level.FINE)) {
                        log.log(
                                Level.SEVERE,
                                "Layer profile wants MaxAge from backend,"
                                        + " but backend does not provide this. Setting to 7200 seconds.");
                    }
                } else {
                    this.expireClientsList.set(0, new ExpirationRule(0, backendExpire));
                    if (log.isLoggable(Level.FINER)) {
                        log.finer("Setting expireClients to: " + expireClients);
                    }
                }
            }
        } catch (Exception e) {
            // Sometimes this doesn't work (network conditions?),
            // and it's really not worth getting caught up on it.
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, e.getMessage(), e);
            }
        }
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

    @Override
    public boolean supportsTileJSON() {
        return true;
    }

    @Override
    public TileJSON getTileJSON() {
        TileJSON tileJSON = new TileJSON();
        tileJSON.setName(name);
        if (metaInformation != null) {
            tileJSON.setDescription(metaInformation.getDescription());
        }
        tilesInfo.decorateTileJSON(tileJSON);
        return tileJSON;
    }
}
