package org.geowebcache.diskquota.storage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.diskquota.storage.PagePyramid.PageLevelInfo;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileRange;
import org.springframework.util.Assert;

public class TilePageCalculator {

    private TileLayerDispatcher tld;

    public TilePageCalculator(final TileLayerDispatcher tld) {
        this.tld = tld;
    }

    public TilePage pageForTile(final TileSet tileSet, final long[] tileIndex) {
        int[] pageIndexForTile = new int[3];
        pageIndexForTile(tileSet, tileIndex, null);
        String tileSetId = tileSet.getId();
        int pageX = pageIndexForTile[0];
        int pageY = pageIndexForTile[1];
        byte zoomLevel = (byte) pageIndexForTile[2];
        return new TilePage(tileSetId, pageX, pageY, zoomLevel);
    }

    public int[] pageIndexForTile(final TileSet tileSet, final long[] tileIndex,
            int[] pageIndexTarget) {

        Assert.notNull(tileSet);
        Assert.notNull(tileIndex);
        Assert.isTrue(pageIndexTarget != null && pageIndexTarget.length > 2);

        PagePyramid pagePyramid = getPagePyramid(tileSet);
        pagePyramid.pageIndexForTile(tileIndex[0], tileIndex[1], (int) tileIndex[2],
                pageIndexTarget);
        return pageIndexTarget;
    }

    private PagePyramid getPagePyramid(TileSet tileSet) {
        PagePyramid pagePyramid = newPagePyramid(tileSet);
        return pagePyramid;
    }

    public BigInteger getTilesPerPage(TileSet tileSet, int zoomLevel) {
        PagePyramid pagePyramid = getPagePyramid(tileSet);
        PageLevelInfo pageInfo = pagePyramid.getPageInfo(zoomLevel);
        BigInteger tilesPerPage = pageInfo.tilesPerPage;
        return tilesPerPage;
    }

    /**
     * Returns a grid subset coverage range suitable for {@link TileRange}
     * 
     * @param page
     * @return {@code [minTileX, minTileY, maxTileX, maxTileY, zoomlevel]}
     */
    public long[][] toGridCoverage(TileSet tileSet, TilePage page) {
        PagePyramid pagePyramid = getPagePyramid(tileSet);
        int pageX = page.getPageX();
        int pageY = page.getPageY();
        int level = page.getZoomLevel();
        long[][] gridCoverage = pagePyramid.toGridCoverage(pageX, pageY, level);
        return gridCoverage;
    }

    public Set<String> getLayerNames() {
        return tld.getLayerNames();
    }

    public Set<TileSet> getTileSetsFor(final String layerName) {

        TileLayer tileLayer;
        try {
            tileLayer = tld.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new IllegalArgumentException(e);
        }

        final Collection<GridSubset> gridSubSets = new ArrayList<GridSubset>();
        for (String gridSetId : tileLayer.getGridSubsets()) {
            GridSubset subset = tileLayer.getGridSubset(gridSetId);
            gridSubSets.add(subset);
        }
        final List<MimeType> mimeTypes = tileLayer.getMimeTypes();

        return getTileSetsFor(layerName, gridSubSets, mimeTypes);
    }

    public Set<TileSet> getTileSetsFor(final String layerName,
            final Collection<GridSubset> gridSubSets, final List<MimeType> mimeTypes) {
        Set<TileSet> layerTileSets = new HashSet<TileSet>();

        String gridsetId;
        String blobFormat;
        String parametersId = null;
        // TODO: create one TileSet per parametersId (somehow...)

        TileSet tileSet;
        for (GridSubset gridSubset : gridSubSets) {
            gridsetId = gridSubset.getName();

            for (MimeType mime : mimeTypes) {
                blobFormat = mime.getFormat();

                tileSet = new TileSet(layerName, gridsetId, blobFormat, parametersId);
                layerTileSets.add(tileSet);
            }
        }

        return layerTileSets;
    }

    private PagePyramid newPagePyramid(final TileSet tileSet) {
        final String layerName = tileSet.getLayerName();
        final TileLayer tileLayer;
        try {
            tileLayer = tld.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new IllegalArgumentException(e);
        }

        final String gridsetId = tileSet.getGridsetId();
        final GridSubset gridSubset = tileLayer.getGridSubset(gridsetId);
        return newPagePyramid(gridSubset);
    }

    PagePyramid newPagePyramid(final GridSubset gridSubset) {
        int zoomStart = gridSubset.getZoomStart();
        int zoomStop = gridSubset.getZoomStop();
        final long[][] coverages = gridSubset.getCoverages();

        PagePyramid pagePyramid = new PagePyramid(coverages, zoomStart, zoomStop);
        return pagePyramid;
    }
}
