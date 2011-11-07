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

import org.apache.commons.lang.builder.HashCodeBuilder;

public class GridSet {

    private Grid[] gridLevels;

    /**
     * The base cordinates, used to map tile indexes to coordinate bounding boxes. These can either
     * be top left or bottom left, so must be kept private
     */
    private double[] baseCoords;

    private BoundingBox originalExtent;

    /**
     * Whether the y-coordinate of baseCoords is at the top (true) or at the bottom (false)
     */
    protected boolean yBaseToggle = false;

    /**
     * By default the coordinates are {x,y}, this flag reverses the output for WMTS getcapabilities
     */
    private boolean yCoordinateFirst = false;

    private boolean scaleWarning = false;

    private double metersPerUnit;

    private double pixelSize;

    private String name;

    private SRS srs;

    private int tileWidth;

    private int tileHeight;

    protected GridSet() {
        // Blank
    }

    /**
     * @return the originalExtent
     */
    public BoundingBox getOriginalExtent() {
        return originalExtent;
    }

    /**
     * @param originalExtent
     *            the originalExtent to set
     */
    void setOriginalExtent(BoundingBox originalExtent) {
        this.originalExtent = originalExtent;
    }

    protected BoundingBox boundsFromIndex(long[] tileIndex) {
        Grid grid = getGridLevels()[(int) tileIndex[2]];

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        long y = tileIndex[1];
        if (yBaseToggle) {
            y = y - grid.getNumTilesHigh();
        }

        BoundingBox tileBounds = new BoundingBox(getBaseCoords()[0] + width * tileIndex[0],
                getBaseCoords()[1] + height * (y), getBaseCoords()[0] + width * (tileIndex[0] + 1),
                getBaseCoords()[1] + height * (y + 1));
        return tileBounds;
    }

    protected BoundingBox boundsFromRectangle(long[] rectangleExtent) {
        Grid grid = getGridLevels()[(int) rectangleExtent[4]];

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        long bottomY = rectangleExtent[1];
        long topY = rectangleExtent[3];

        if (yBaseToggle) {
            bottomY = bottomY - grid.getNumTilesHigh();
            topY = topY - grid.getNumTilesHigh();
        }

        BoundingBox rectangleBounds = new BoundingBox(getBaseCoords()[0] + width
                * rectangleExtent[0], getBaseCoords()[1] + height * (bottomY), getBaseCoords()[0]
                + width * (rectangleExtent[2] + 1), getBaseCoords()[1] + height * (topY + 1));

        return rectangleBounds;
    }

    protected long[] closestIndex(BoundingBox tileBounds) throws GridMismatchException {
        double wRes = tileBounds.getWidth() / getTileWidth();

        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;
        double bestResolution = -1.0;

        for (int i = 0; i < getGridLevels().length; i++) {
            Grid grid = getGridLevels()[i];

            double error = Math.abs(wRes - grid.getResolution());

            if (error < bestError) {
                bestError = error;
                bestResolution = grid.getResolution();
                bestLevel = i;
            } else {
                break;
            }
        }

        if (Math.abs(wRes - bestResolution) > (0.1 * wRes)) {
            throw new ResolutionMismatchException(wRes, bestResolution);
        }

        return closestIndex(bestLevel, tileBounds);
    }

    protected long[] closestIndex(int level, BoundingBox tileBounds)
            throws GridAlignmentMismatchException {
        Grid grid = getGridLevels()[level];

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        double x = (tileBounds.getMinX() - getBaseCoords()[0]) / width;

        double y = (tileBounds.getMinY() - getBaseCoords()[1]) / height;

        long posX = (long) Math.round(x);

        long posY = (long) Math.round(y);

        if (x - posX > 0.1 || y - posY > 0.1) {
            throw new GridAlignmentMismatchException(x, posX, y, posY);
        }

        if (yBaseToggle) {
            posY = posY + grid.getNumTilesHigh();
        }

        long[] ret = { posX, posY, level };

        return ret;
    }

    public long[] closestRectangle(BoundingBox rectangleBounds) {
        double rectWidth = rectangleBounds.getWidth();
        double rectHeight = rectangleBounds.getHeight();

        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;

        // Now we loop over the resolutions until
        for (int i = 0; i < getGridLevels().length; i++) {
            Grid grid = getGridLevels()[i];

            double countX = rectWidth / (grid.getResolution() * getTileWidth());
            double countY = rectHeight / (grid.getResolution() * getTileHeight());

            double error = Math.abs(countX - Math.round(countX))
                    + Math.abs(countY - Math.round(countY));

            if (error < bestError) {
                bestError = error;
                bestLevel = i;
            } else if (error >= bestError) {
                break;
            }
        }

        return closestRectangle(bestLevel, rectangleBounds);
    }

    protected long[] closestRectangle(int level, BoundingBox rectangeBounds) {
        Grid grid = getGridLevels()[level];

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        long minX = (long) Math.floor((rectangeBounds.getMinX() - getBaseCoords()[0]) / width);
        long minY = (long) Math.floor((rectangeBounds.getMinY() - getBaseCoords()[1]) / height);
        long maxX = (long) Math.ceil(((rectangeBounds.getMaxX() - getBaseCoords()[0]) / width));
        long maxY = (long) Math.ceil(((rectangeBounds.getMaxY() - getBaseCoords()[1]) / height));

        if (yBaseToggle) {
            minY = minY + grid.getNumTilesHigh();
            maxY = maxY + grid.getNumTilesHigh();
        }

        // We substract one, since that's the tile at that position
        long[] ret = { minX, minY, maxX - 1, maxY - 1, level };

        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GridSet))
            return false;

        GridSet other = (GridSet) obj;

        if (this == other)
            return true;

        if (!other.getSrs().equals(getSrs()))
            return false;

        if (!other.getName().equals(getName()))
            return false;

        if (getTileWidth() != other.getTileWidth() || getTileHeight() != other.getTileHeight())
            return false;

        if (getGridLevels().length != other.getGridLevels().length)
            return false;

        for (int i = 0; i < getGridLevels().length; i++) {
            if (!getGridLevels()[i].equals(other.getGridLevels()[i]))
                return false;
        }

        if (yBaseToggle != other.yBaseToggle)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = HashCodeBuilder.reflectionHashCode(this);
        return hashCode;
    }

    public BoundingBox getBounds() {
        int i;
        long tilesWide, tilesHigh;

        for (i = (getGridLevels().length - 1); i > 0; i--) {
            tilesWide = getGridLevels()[i].getNumTilesWide();
            tilesHigh = getGridLevels()[i].getNumTilesHigh();

            if (tilesWide == 1 && tilesHigh == 0) {
                break;
            }
        }

        tilesWide = getGridLevels()[i].getNumTilesWide();
        tilesHigh = getGridLevels()[i].getNumTilesHigh();
        long[] ret = { 0, 0, tilesWide - 1, tilesHigh - 1, i };

        return boundsFromRectangle(ret);
    }

    public Grid[] getGrids() {
        return getGridLevels();
    }

    /**
     * Returns the top left corner of the grid in the order used by the coordinate system. (Bad
     * idea)
     * 
     * Used for WMTS GetCapabilities
     * 
     * @param gridIndex
     * @return
     */
    public double[] getOrderedTopLeftCorner(int gridIndex) {
        // First we will find the x,y pair, then we'll flip it if necessary
        double[] leftTop = new double[2];

        if (yBaseToggle) {
            leftTop[0] = getBaseCoords()[0];
            leftTop[1] = getBaseCoords()[1];
        } else {
            // We don't actually store the top coordinate, need to calculate it
            Grid grid = getGridLevels()[gridIndex];

            double dTileHeight = getTileHeight();
            double dGridExtent = grid.getNumTilesHigh();

            double top = getBaseCoords()[1] + dTileHeight * grid.getResolution() * dGridExtent;

            // Round off if we are within 0.5% of an integer value
            if (Math.abs(top - Math.round(top)) < (top / 200)) {
                top = Math.round(top);
            }

            leftTop[0] = getBaseCoords()[0];
            leftTop[1] = top;
        }

        // Y coordinate first?
        if (isyCoordinateFirst()) {
            double[] ret = { leftTop[1], leftTop[0] };
            return ret;
        }

        return leftTop;
    }

    public String guessMapUnits() {
        if (113000 > getMetersPerUnit() && getMetersPerUnit() > 110000) {
            return "degrees";
        } else if (1100 > getMetersPerUnit() && getMetersPerUnit() > 900) {
            return "kilometers";
        } else if (1.1 > getMetersPerUnit() && getMetersPerUnit() > 0.9) {
            return "meters";
        } else if (0.4 > getMetersPerUnit() && getMetersPerUnit() > 0.28) {
            return "feet";
        } else if (0.03 > getMetersPerUnit() && getMetersPerUnit() > 0.02) {
            return "inches";
        } else if (0.02 > getMetersPerUnit() && getMetersPerUnit() > 0.005) {
            return "centimeters";
        } else if (0.002 > getMetersPerUnit() && getMetersPerUnit() > 0.0005) {
            return "millimeters";
        } else {
            return "unknown";
        }
    }

    public boolean isTopLeftAligned() {
        return this.yBaseToggle;
    }

    void setTopLeftAligned(boolean yBaseToggle) {
        this.yBaseToggle = yBaseToggle;
    }

    /**
     * @return the gridLevels
     */
    public Grid[] getGridLevels() {
        return gridLevels;
    }

    /**
     * @param gridLevels
     *            the gridLevels to set
     */
    public void setGridLevels(Grid[] gridLevels) {
        this.gridLevels = gridLevels;
    }

    /**
     * @return the baseCoords
     */
    public double[] getBaseCoords() {
        return baseCoords;
    }

    /**
     * @param baseCoords
     *            the baseCoords to set
     */
    public void setBaseCoords(double[] baseCoords) {
        this.baseCoords = baseCoords;
    }

    /**
     * @return the yCoordinateFirst
     */
    public boolean isyCoordinateFirst() {
        return yCoordinateFirst;
    }

    /**
     * @param yCoordinateFirst
     *            the yCoordinateFirst to set
     */
    public void setyCoordinateFirst(boolean yCoordinateFirst) {
        this.yCoordinateFirst = yCoordinateFirst;
    }

    /**
     * @return the scaleWarning
     */
    public boolean isScaleWarning() {
        return scaleWarning;
    }

    /**
     * @param scaleWarning
     *            the scaleWarning to set
     */
    public void setScaleWarning(boolean scaleWarning) {
        this.scaleWarning = scaleWarning;
    }

    /**
     * @return the metersPerUnit
     */
    public double getMetersPerUnit() {
        return metersPerUnit;
    }

    /**
     * @param metersPerUnit
     *            the metersPerUnit to set
     */
    public void setMetersPerUnit(double metersPerUnit) {
        this.metersPerUnit = metersPerUnit;
    }

    /**
     * @return the pixelSize
     */
    public double getPixelSize() {
        return pixelSize;
    }

    /**
     * @param pixelSize
     *            the pixelSize to set
     */
    public void setPixelSize(double pixelSize) {
        this.pixelSize = pixelSize;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the srs
     */
    public SRS getSrs() {
        return srs;
    }

    /**
     * @param srs
     *            the srs to set
     */
    public void setSrs(SRS srs) {
        this.srs = srs;
    }

    /**
     * @return the tileWidth
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * @param tileWidth
     *            the tileWidth to set
     */
    public void setTileWidth(int tileWidth) {
        this.tileWidth = tileWidth;
    }

    /**
     * @return the tileHeight
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * @param tileHeight
     *            the tileHeight to set
     */
    public void setTileHeight(int tileHeight) {
        this.tileHeight = tileHeight;
    }
}
