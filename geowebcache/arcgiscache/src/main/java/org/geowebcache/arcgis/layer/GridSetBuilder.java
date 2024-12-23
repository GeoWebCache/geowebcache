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
package org.geowebcache.arcgis.layer;

import java.util.List;
import org.geowebcache.arcgis.config.CacheInfo;
import org.geowebcache.arcgis.config.LODInfo;
import org.geowebcache.arcgis.config.SpatialReference;
import org.geowebcache.arcgis.config.TileCacheInfo;
import org.geowebcache.arcgis.config.TileOrigin;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.springframework.util.Assert;

/**
 * Utility to crate a {@link GridSet} out of a ArcGIS tiling scheme
 *
 * @author Gabriel Roldan
 */
class GridSetBuilder {

    /** Creates a {@link GridSet} out of a ArcGIS tiling scheme */
    public GridSet buildGridset(final String layerName, final CacheInfo info, final BoundingBox layerBounds) {

        Assert.notNull(layerName, "Layer name must be non null");
        Assert.notNull(info, "Layer name must be non null");
        Assert.notNull(layerBounds, "Layer name must be non null");

        final TileCacheInfo tileCacheInfo = info.getTileCacheInfo();
        final SpatialReference spatialReference = tileCacheInfo.getSpatialReference();

        final SRS srs;
        final BoundingBox gridSetExtent;

        final boolean alignTopLeft = true;
        final double[] resolutions;
        /*
         * let scale denoms be null so GridSetFactory computes them based on resolutions. The
         * resulting values will be pretty close to the ones defined in the ArcGIS tiling scheme
         */
        final double[] scaleDenominators = null;
        final Double metersPerUnit;
        final String[] scaleNames = null;
        final int tileWidth = tileCacheInfo.getTileCols();
        final int tileHeight = tileCacheInfo.getTileRows();
        final boolean yCoordinateFirst = false;
        final double pixelSize = 0.0254 / tileCacheInfo.getDPI(); // see GridSubset.getDotsPerInch()
        {
            int epsgNumber = spatialReference.getWKID();
            srs = SRS.getSRS(epsgNumber);
        }
        {
            final List<LODInfo> lodInfos = tileCacheInfo.getLodInfos();
            double[][] resAndScales = getResolutions(lodInfos);

            resolutions = resAndScales[0];

            double[] scales = resAndScales[1];
            // TODO: check whether pixelSize computed above should be used instead
            metersPerUnit = (GridSetFactory.DEFAULT_PIXEL_SIZE_METER * scales[0]) / resolutions[0];
        }
        {
            // See "How to calculate the -x parameter used in the examples above" at
            // http://resources.arcgis.com/content/kbase?q=content/kbase&fa=articleShow&d=15558&print=true
            // double XOrigin = spatialReference.getXOrigin();
            // double YOrigin = spatialReference.getYOrigin();
            // XYScale = 40075017 / 360 = ~111319, where 40075017 is the circumference of the earth
            // at the ecuator and 360 the map units at the ecuator
            // final double xyScale = spatialReference.getXYScale();

            final TileOrigin tileOrigin = tileCacheInfo.getTileOrigin(); // top left coordinate

            double xmin = tileOrigin.getX();
            double ymax = tileOrigin.getY();
            double ymin = layerBounds.getMinY();
            double xmax = layerBounds.getMaxX();

            // make it so the gridset height matches an integer number of tiles in order for
            // clients (OpenLayers) assuming the tile origin is the lower left corner instead of
            // the upper right to compute tile bounding boxes right
            final double resolution = resolutions[resolutions.length - 1];
            double width = resolution * tileWidth;
            double height = resolution * tileHeight;

            long numTilesWide = (long) Math.ceil((xmax - xmin) / width);
            long numTilesHigh = (long) Math.ceil((ymax - ymin) / height);

            xmax = xmin + (numTilesWide * width);
            ymin = ymax - (numTilesHigh * height);
            gridSetExtent = new BoundingBox(xmin, ymin, xmax, ymax);
        }

        String gridsetName = srs.toString() + "_" + layerName;
        GridSet layerGridset = GridSetFactory.createGridSet(
                gridsetName,
                srs,
                gridSetExtent,
                alignTopLeft,
                resolutions,
                scaleDenominators,
                metersPerUnit,
                pixelSize,
                scaleNames,
                tileWidth,
                tileHeight,
                yCoordinateFirst);

        return layerGridset;
    }

    private double[][] getResolutions(List<LODInfo> lodInfos) {
        final int numLevelsOfDetail = lodInfos.size();
        double[][] resolutionsAndScales = new double[2][numLevelsOfDetail];
        LODInfo lodInfo;
        for (int i = 0; i < numLevelsOfDetail; i++) {
            lodInfo = lodInfos.get(i);
            resolutionsAndScales[0][i] = lodInfo.getResolution();
            resolutionsAndScales[1][i] = lodInfo.getScale();
        }
        return resolutionsAndScales;
    }
}
