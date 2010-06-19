package org.geowebcache.diskquota.paging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.diskquota.LayerQuota;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.TileRange;

public class TilePageCalculator {

    private static final Log log = LogFactory.getLog(TilePageCalculator.class);

    /**
     * Map<gridSubsetId,{level, {pagesWide, pagesHigh}}>
     */
    final Map<String, PagePyramid> pageRangesPerGridSubset = new HashMap<String, PagePyramid>();

    private final ReadWriteLock pagesLock = new ReentrantReadWriteLock();

    private final TileLayer tileLayer;

    private final LayerQuota layerQuota;

    public TilePageCalculator(final TileLayer tileLayer, final LayerQuota layerQuota) {

        this.tileLayer = tileLayer;
        this.layerQuota = layerQuota;

        Hashtable<String, GridSubset> gridSubsets = tileLayer.getGridSubsets();

        for (GridSubset gs : gridSubsets.values()) {
            String name = gs.getName();
            PagePyramid pagePyramid = new PagePyramid(gs);
            pageRangesPerGridSubset.put(name, pagePyramid);
        }
    }

    public TileLayer getTileLayer() {
        return this.tileLayer;
    }

    public LayerQuota getLayerQuota() {
        return this.layerQuota;
    }

    public TilePage pageFor(long x, long y, int z, String gridSetId) {
        pagesLock.readLock().lock();
        try {
            PagePyramid pageRange = pageRangesPerGridSubset.get(gridSetId);
            return pageRange.pageFor(x, y, z);
        } finally {
            pagesLock.readLock().unlock();
        }
    }

    /**
     * Returns a the list of {@link TilePage}s per gridsubset at the time of calling.
     * <p>
     * Note the returned pagea are a deep copy (i.e. internal {@link TilePage}s are copies too).
     * This is so that the stats collector can continue gathering usage data on pages without
     * affecting the result of this method.
     * </p>
     * 
     * @return
     */
    public List<TilePage> getAllPages(final String gridSubset) {
        Map<int[], TilePage> allPagesMap;
        pagesLock.writeLock().lock();
        try {
            PagePyramid pageRange = this.pageRangesPerGridSubset.get(gridSubset);
            allPagesMap = pageRange.getAllPages();
        } finally {
            pagesLock.writeLock().unlock();
        }

        List<TilePage> allPages = new ArrayList<TilePage>(allPagesMap.values());
        return allPages;
    }

    public ArrayList<TilePage> getPages(String gridSetId) {
        ArrayList<TilePage> pages;
        pagesLock.writeLock().lock();
        try {
            PagePyramid pageRange = this.pageRangesPerGridSubset.get(gridSetId);
            pages = new ArrayList<TilePage>(pageRange.getPages());
        } finally {
            pagesLock.writeLock().unlock();
        }
        return pages;
    }

    public void setPages(final String gridSetId, final List<TilePage> pages) {
        PagePyramid pageRange = this.pageRangesPerGridSubset.get(gridSetId);
        pageRange.setPages(pages);
    }

    /**
     * Returns a grid subset coverage range suitable for {@link TileRange}
     * 
     * @param page
     * @param gridSetId
     * @return {@code [level][minTileX, minTileY, maxTileX, maxTileY, zoomlevel]} represented by the
     *         given tile page for the given grid subset
     */
    public long[][] toGridCoverage(final TilePage page, final String gridSetId) {

        PagePyramid pageRange = this.pageRangesPerGridSubset.get(gridSetId);

        long[][] gridCoverage = pageRange.toGridCoverage(page);
        return gridCoverage;
    }
}
