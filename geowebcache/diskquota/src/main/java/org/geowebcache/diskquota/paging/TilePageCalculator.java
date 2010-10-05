package org.geowebcache.diskquota.paging;

import java.util.ArrayList;
import java.util.Collection;
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
            PagePyramid pagePyramid = new PagePyramid(tileLayer.getName(), gs.getName(),
                    gs.getCoverages());
            pageRangesPerGridSubset.put(name, pagePyramid);
        }
    }

    public TileLayer getTileLayer() {
        return this.tileLayer;
    }

    public LayerQuota getLayerQuota() {
        return this.layerQuota;
    }

    public void removeTileInfo(long x, long y, int z, String gridSetId) {
        TilePage page = pageFor(x, y, z, gridSetId);
        page.removeTile();
    }

    public void createTileInfo(long x, long y, int z, String gridSetId) {
        TilePage page = pageFor(x, y, z, gridSetId);
        page.addTile();
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

    public List<TilePage> getPages() {
        ArrayList<TilePage> pages = new ArrayList<TilePage>();
        pagesLock.writeLock().lock();
        try {
            Collection<PagePyramid> pagePyramids = this.pageRangesPerGridSubset.values();
            for (PagePyramid srsPyramid : pagePyramids) {
                pages.addAll(srsPyramid.getPages());
            }
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
