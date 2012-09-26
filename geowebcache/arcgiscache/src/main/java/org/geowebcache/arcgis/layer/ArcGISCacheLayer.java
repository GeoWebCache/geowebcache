package org.geowebcache.arcgis.layer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.arcgis.config.CacheInfo;
import org.geowebcache.arcgis.config.CacheInfoPersister;
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
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;

/**
 * 
 * @author Gabriel Roldan
 * 
 */
public class ArcGISCacheLayer extends AbstractTileLayer {

    private static final Log log = LogFactory.getLog(ArcGISCacheLayer.class);

    /*
     * configuration properties
     */

    private Boolean enabled;

    /**
     * The location of the conf.xml tiling scheme configuration file
     */
    private File tilingScheme;

    /**
     * Optional, location of the actual tiles folder. If not provided defaults to the
     * {@code _alllayers} directory at the same location than the {@link #getTilingScheme()
     * conf.xml} tiling scheme.
     */
    private File tileCachePath;

    private transient CacheInfo cacheInfo;

    private transient BoundingBox layerBounds;

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
     * Options, location of the actual tiles folder. If not provided defaults to the
     * {@code _alllayers} directory at the same location than the {@link #getTilingScheme()
     * conf.xml} tiling scheme.
     */
    public void setTileCachePath(File tileCachePath) {
        this.tileCachePath = tileCachePath;
    }

    /**
     * @see org.geowebcache.layer.TileLayer#initialize(org.geowebcache.grid.GridSetBroker)
     * @return {@code true} if success. Note this method's return type should be void. It's not
     *         checked anywhere
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
            if (!tileCachePath.exists() || !tileCachePath.isDirectory() || !tileCachePath.canRead()) {
                throw new IllegalStateException("tileCachePath property for layer '" + getName()
                        + "' is set to '" + tileCachePath
                        + "' but the directory either does not exist or is not readable");
            }
        }
        try {
            CacheInfoPersister tilingSchemeLoader = new CacheInfoPersister();
            cacheInfo = tilingSchemeLoader.load(new FileReader(tilingScheme));
            File layerBoundsFile = new File(tilingScheme.getParentFile(), "conf.cdi");
            if (!layerBoundsFile.exists()) {
                throw new RuntimeException("Layer bounds file not found: "
                        + layerBoundsFile.getAbsolutePath());
            }
            log.info("Parsing layer bounds for " + getName());
            this.layerBounds = tilingSchemeLoader.parseLayerBounds(new FileReader(layerBoundsFile));
            log.info("Parsed layer bounds for " + getName() + ": " + layerBounds);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Tiling scheme file not found: "
                    + tilingScheme.getAbsolutePath());
        }
        log.info("Configuring layer " + getName() + " out of the ArcGIS tiling scheme "
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

    private Hashtable<String, GridSubset> createGridSubsets(final GridSetBroker gridSetBroker) {

        final CacheInfo info = this.cacheInfo;
        final TileCacheInfo tileCacheInfo = info.getTileCacheInfo();

        final String layerName = getName();
        final GridSetBuilder gsBuilder = new GridSetBuilder();
        GridSet gridSet = gsBuilder.buildGridset(layerName, info, layerBounds);

        gridSetBroker.put(gridSet);

        final List<LODInfo> lodInfos = tileCacheInfo.getLodInfos();
        Integer zoomStart = lodInfos.get(0).getLevelID();
        Integer zoomStop = lodInfos.get(lodInfos.size() - 1).getLevelID();

        GridSubset subSet = GridSubsetFactory.createGridSubSet(gridSet, this.layerBounds,
                zoomStart, zoomStop);

        Hashtable<String, GridSubset> subsets = new Hashtable<String, GridSubset>();
        subsets.put(gridSet.getName(), subSet);
        return subsets;
    }

    /**
     * 
     * @see org.geowebcache.layer.TileLayer#getTile(org.geowebcache.conveyor.ConveyorTile)
     */
    @Override
    public ConveyorTile getTile(final ConveyorTile tile) throws GeoWebCacheException, IOException,
            OutsideCoverageException {

        String path = getTilePath(tile);
        File tileFile = new File(path);

        if (tileFile.exists()) {
            Resource tileContent = readFile(tileFile);
            tile.setCacheResult(CacheResult.HIT);
            tile.setBlob(tileContent);
        } else {
            tile.setCacheResult(CacheResult.MISS);
            if (!setLayerBlankTile(tile)) {
                throw new OutsideCoverageException(tile.getTileIndex(), 0, 0);
            }
        }
        return tile;
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

        Grid grid = gridSet.getGridLevels()[z];

        // long[] coverage = gridSubset.getCoverage(z);
        // long coverageMinY = coverage[1];
        long coverageMaxY = grid.getNumTilesHigh() - 1;

        final long x = tileIndex[0];
        // invert the order of the requested Y ordinate, since ArcGIS caches are top-left to
        // bottom-right, and GWC computes tiles in bottom-left to top-right order
        final long y = (coverageMaxY - tileIndex[1]);

        String level = Integer.toHexString(z);
        level = zeroPadder(level, 2);

        String row = Long.toHexString(y);
        row = zeroPadder(row, 8);

        String col = Long.toHexString(x);
        col = zeroPadder(col, 8);

        StringBuilder path = getLayerPath();

        path.append(File.separatorChar).append('L').append(level).append(File.separatorChar)
                .append('R').append(row).append(File.separatorChar).append('C').append(col);

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

    private Resource readFile(File fh)  {
        if (!fh.exists()) {
            return null;
        }
        Resource res = new FileResource(fh);
        return res;
    }

    /**
     * 
     * @see org.geowebcache.layer.TileLayer#getNoncachedTile(org.geowebcache.conveyor.ConveyorTile)
     */
    @Override
    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        throw new UnsupportedOperationException();
    }

    /**
     * 
     * @see org.geowebcache.layer.TileLayer#seedTile(org.geowebcache.conveyor.ConveyorTile, boolean)
     */
    @Override
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 
     * @see org.geowebcache.layer.TileLayer#doNonMetatilingRequest(org.geowebcache.conveyor.ConveyorTile)
     */
    @Override
    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        throw new UnsupportedOperationException();
    }

    /**
     * 
     * @see org.geowebcache.layer.TileLayer#getStyles()
     */
    @Override
    public String getStyles() {
        return null;
    }

    /**
     * 
     * @see org.geowebcache.layer.TileLayer#setExpirationHeader(javax.servlet.http.HttpServletResponse,
     *      int)
     */
    @Override
    public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {
        /*
         * NOTE: this method doesn't seem like belonging to TileLayer, but to GeoWebCacheDispatcher
         * itself
         */
        return;
    }

}
