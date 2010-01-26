package org.geowebcache.georss;

import java.io.IOException;

import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Consumes a GeoRSS feed and creates a tile range filter based on the feed's geometries for the
 * given tiled layer.
 * <p>
 * I'm not sure yet where the georss seeding will be launched from. In any case, whether it is a
 * user call or a triggered by a configuration option every X time, it should use this class.
 * </p>
 */
class GeoRSSTileRangeBuilder {

    private final TileLayer layer;

    private final String gridSetId;

    private final int maxMaskLevel;

    /**
     * 
     * @param layer
     *            The layer to create the mask of affected tiles for
     * @param gridSetId
     *            the gridset identifier of the layer
     * @param maxMaskLevel
     *            index of the maximum zoom level for which to create a tile matrix for, meaning
     *            greater levels will be downsampled to save memory
     */
    public GeoRSSTileRangeBuilder(final TileLayer layer, final String gridSetId,
            final int maxMaskLevel) {
        if (layer == null) {
            throw new NullPointerException("layer");
        }
        if (gridSetId == null) {
            throw new NullPointerException("griSetId");
        }
        if (maxMaskLevel < 0) {
            throw new IllegalArgumentException("maxMaskLevel shall be >= 0: " + maxMaskLevel);
        }
        this.layer = layer;
        this.gridSetId = gridSetId;
        this.maxMaskLevel = maxMaskLevel;

        final GridSubset gridSubset = layer.getGridSubset(gridSetId);
        if (gridSubset == null) {
            throw new IllegalArgumentException("no grid subset " + gridSetId + " at "
                    + layer.getName());
        }

        // final String srs = "EPSG:" + gridSubset.getSRS().getNumber();
        // we're assuming the feed sends geometries in the appropriate crs here in order not to
        // carry over the epsg database
        // try {
        // layerCrs = CRS.decode(srs);
        // } catch (NoSuchAuthorityCodeException e) {
        // throw new IllegalArgumentException("Can't get CRS for " + srs, e);
        // } catch (FactoryException e) {
        // throw new IllegalArgumentException("Can't get CRS for " + srs, e);
        // }
    }

    public TileGridFilterMatrix buildTileRangeMask(final GeoRSSReader reader) throws IOException {

        final GridSubset gridSubset = layer.getGridSubset(gridSetId);
        TileGridFilterMatrix matrix = new TileGridFilterMatrix(gridSubset, maxMaskLevel);

        Entry entry;
        Geometry geom;

        matrix.createGraphics();
        try {
            while ((entry = reader.nextEntry()) != null) {
                //crs = entry.getCRS();
                geom = entry.getWhere();
                matrix.setMasksForGeometry(geom);
            }
        } finally {
            matrix.disposeGraphics();
        }

        return matrix;
    }
}
