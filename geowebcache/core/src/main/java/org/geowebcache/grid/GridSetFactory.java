/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GridSetFactory {
    private static Log log = LogFactory.getLog(GridSetFactory.class);

    /**
     * Default pixel size in meters, producing a default of 90.7 DPI
     * 
     * @see GridSubset#getDotsPerInch()
     */
    public static final double DEFAULT_PIXEL_SIZE_METER = 0.00028;

    public static int DEFAULT_LEVELS = 31;

    public final static double EPSG4326_TO_METERS = 6378137.0 * 2.0 * Math.PI / 360.0;

    public final static double EPSG3857_TO_METERS = 1;

    private static GridSet baseGridSet(String name, SRS srs, int tileWidth, int tileHeight) {
        GridSet gridSet = new GridSet();

        gridSet.setName(name);
        gridSet.setSrs(srs);

        gridSet.setTileWidth(tileWidth);
        gridSet.setTileHeight(tileHeight);

        return gridSet;
    }

    /**
     * Note that you should provide EITHER resolutions or scales. Providing both will cause scales
     * to be overwritten
     * 
     * @param name
     * @param srs
     * @param extent
     * @param resolutions
     * @param scales
     * @param tileWidth
     * @param tileHeight
     * @param pixelSize
     * @param yCoordinateFirst
     * @return
     */
    public static GridSet createGridSet(String name, SRS srs, BoundingBox extent,
            boolean alignTopLeft, double[] resolutions, double[] scaleDenoms, Double metersPerUnit,
            double pixelSize, String[] scaleNames, int tileWidth, int tileHeight,
            boolean yCoordinateFirst) {

        GridSet gridSet = baseGridSet(name, srs, tileWidth, tileHeight);

        gridSet.setPixelSize(pixelSize);

        gridSet.setOriginalExtent(extent);
        gridSet.yBaseToggle = alignTopLeft;

        gridSet.setyCoordinateFirst(yCoordinateFirst);

        if (metersPerUnit == null) {
            if (srs.equals(SRS.getEPSG4326())) {
                gridSet.setMetersPerUnit(EPSG4326_TO_METERS);
            } else if (srs.equals(SRS.getEPSG3857())) {
                gridSet.setMetersPerUnit(EPSG3857_TO_METERS);
            } else {
                if (resolutions == null) {
                    log.warn("GridSet " + name
                            + " was defined without metersPerUnit, assuming 1m/unit."
                            + " All scales will be off if this is incorrect.");
                } else {
                    log.warn("GridSet " + name + " was defined without metersPerUnit. "
                            + "Assuming 1m per SRS unit for WMTS scale output.");

                    gridSet.setScaleWarning(true);
                }
                gridSet.setMetersPerUnit(1.0);
            }
        } else {
            gridSet.setMetersPerUnit(metersPerUnit);
        }

        if (resolutions == null) {
            gridSet.setGridLevels(new Grid[scaleDenoms.length]);
        } else {
            gridSet.setGridLevels(new Grid[resolutions.length]);
        }

        for (int i = 0; i < gridSet.getGridLevels().length; i++) {
            Grid curGrid = new Grid();

            if (scaleDenoms != null) {
                curGrid.setScaleDenom(scaleDenoms[i]);
                curGrid.setResolution(pixelSize * (scaleDenoms[i] / gridSet.getMetersPerUnit()));
            } else {
                curGrid.setResolution(resolutions[i]);
                curGrid.setScaleDenom((resolutions[i] * gridSet.getMetersPerUnit())
                        / DEFAULT_PIXEL_SIZE_METER);
            }

            double mapUnitWidth = tileWidth * curGrid.getResolution();
            double mapUnitHeight = tileHeight * curGrid.getResolution();

            long tilesWide = (long) Math.ceil((extent.getWidth() - mapUnitWidth * 0.01)
                    / mapUnitWidth);
            long tilesHigh = (long) Math.ceil((extent.getHeight() - mapUnitHeight * 0.01)
                    / mapUnitHeight);

            curGrid.setNumTilesWide(tilesWide);
            curGrid.setNumTilesHigh(tilesHigh);

            if (scaleNames == null || scaleNames[i] == null) {
                curGrid.setName(gridSet.getName() + ":" + i);
            } else {
                curGrid.setName(scaleNames[i]);
            }

            gridSet.getGridLevels()[i] = curGrid;
        }

        return gridSet;
    }

    /**
     * This covers the case where a number of zoom levels has been specified, but no resolutions /
     * scale
     * 
     * @param pixelSize
     * @param yCoordinateFirst
     */
    public static GridSet createGridSet(String name, SRS srs, BoundingBox extent,
            boolean alignTopLeft, int levels, Double metersPerUnit, double pixelSize,
            int tileWidth, int tileHeight, boolean yCoordinateFirst) {

        double[] resolutions = new double[levels];

        double relWidth = extent.getWidth() / tileWidth;
        double relHeight = extent.getHeight() / tileHeight;

        double ratio = relWidth / relHeight;
        double roundedRatio = Math.round(ratio);
        double ratioDiff = ratio - roundedRatio;

        // Cut 2.5% slack throughout
        if (Math.abs(ratioDiff) < 0.025) {
            // All good
            resolutions[0] = relWidth / roundedRatio;

        } else if (ratio < roundedRatio) {
            // Increase the width
            if (ratioDiff < 0) {
                ratio = roundedRatio;
            } else {
                ratio = roundedRatio + 1;
            }
            relWidth += (ratio * relHeight - relWidth);

            extent.setMaxX((relWidth * tileWidth) + extent.getMinX());

            resolutions[0] = (extent.getWidth() / ratio) / tileWidth;
        } else {
            // Increase the height
            if (ratioDiff > 0) {
                ratio = roundedRatio;
            } else {
                ratio = roundedRatio + 1;
            }
            relHeight += ((relWidth / ratio) - relHeight);

            // Do we keep the top or the bottom fixed?
            if (alignTopLeft) {
                extent.setMinY(extent.getMaxY() - (relHeight * tileHeight));
            } else {
                extent.setMaxY((relHeight * tileHeight) + extent.getMinY());
            }

            resolutions[0] = (extent.getWidth() / ratio) / tileWidth;
        }

        for (int i = 1; i < levels; i++) {
            resolutions[i] = resolutions[i - 1] / 2;
        }

        return createGridSet(name, srs, extent, alignTopLeft, resolutions, null, metersPerUnit,
                pixelSize, null, tileWidth, tileHeight, yCoordinateFirst);
    }
}
