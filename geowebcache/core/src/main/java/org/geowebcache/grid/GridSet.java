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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.geowebcache.config.Info;

/** A grid set configuration */
public class GridSet implements Info {

    private String name;

    private SRS srs;

    private int tileWidth;

    private int tileHeight;

    /**
     * Whether the y-coordinate of {@link #tileOrigin()} is at the top (true) or at the bottom
     * (false)
     */
    protected boolean yBaseToggle = false;

    /**
     * By default the coordinates are {x,y}, this flag reverses the output for WMTS getcapabilities
     */
    private boolean yCoordinateFirst = false;

    private boolean scaleWarning = false;

    private double metersPerUnit;

    private double pixelSize;

    private BoundingBox originalExtent;

    private Grid[] gridLevels;

    private String description;

    /**
     * {@code true} if the resolutions are preserved and the scaleDenominators calculated, {@code
     * false} if the resolutions are calculated based on the sacale denominators.
     */
    private boolean resolutionsPreserved;

    protected GridSet() {
        // Blank
    }

    public GridSet(GridSet g) {
        super();
        this.name = g.name;
        this.srs = g.srs;
        this.tileWidth = g.tileWidth;
        this.tileHeight = g.tileHeight;
        this.yBaseToggle = g.yBaseToggle;
        this.yCoordinateFirst = g.yCoordinateFirst;
        this.scaleWarning = g.scaleWarning;
        this.metersPerUnit = g.metersPerUnit;
        this.pixelSize = g.pixelSize;
        this.originalExtent = g.originalExtent;
        this.gridLevels = g.gridLevels;
        this.description = g.description;
        this.resolutionsPreserved = g.resolutionsPreserved;
    }

    /** @return the originalExtent */
    public BoundingBox getOriginalExtent() {
        return originalExtent;
    }

    /** @param originalExtent the originalExtent to set */
    void setOriginalExtent(BoundingBox originalExtent) {
        this.originalExtent = originalExtent;
    }

    /**
     * @return {@code true} if the resolutions are preserved and the scaleDenominators calculated,
     *     {@code false} if the resolutions are calculated based on the sacale denominators.
     */
    public boolean isResolutionsPreserved() {
        return resolutionsPreserved;
    }

    /**
     * @param resolutionsPreserved {@code true} if the resolutions are preserved and the
     *     scaleDenominators calculated, {@code false} if the resolutions are calculated based on
     *     the sacale denominators.
     */
    void setResolutionsPreserved(boolean resolutionsPreserved) {
        this.resolutionsPreserved = resolutionsPreserved;
    }

    public BoundingBox boundsFromIndex(long[] tileIndex) {
        final int tileZ = (int) tileIndex[2];
        Grid grid = getGrid(tileZ);

        final long tileX = tileIndex[0];
        final long tileY;
        if (yBaseToggle) {
            tileY = tileIndex[1] - grid.getNumTilesHigh();
        } else {
            tileY = tileIndex[1];
        }

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        final double[] tileOrigin = tileOrigin();
        BoundingBox tileBounds =
                new BoundingBox(
                        tileOrigin[0] + width * tileX,
                        tileOrigin[1] + height * (tileY),
                        tileOrigin[0] + width * (tileX + 1),
                        tileOrigin[1] + height * (tileY + 1));
        return tileBounds;
    }

    /**
     * Finds the spatial bounding box of a rectangular group of tiles.
     *
     * @param rectangleExtent the rectangle of tiles. {minx, miny, maxx, maxy} in tile coordinates
     * @return the spatial bounding box in the coordinates of the SRS used by the GridSet
     */
    protected BoundingBox boundsFromRectangle(long[] rectangleExtent) {
        Grid grid = getGrid((int) rectangleExtent[4]);

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        long bottomY = rectangleExtent[1];
        long topY = rectangleExtent[3];

        if (yBaseToggle) {
            bottomY = bottomY - grid.getNumTilesHigh();
            topY = topY - grid.getNumTilesHigh();
        }

        double[] tileOrigin = tileOrigin();
        double minx = tileOrigin[0] + width * rectangleExtent[0];
        double miny = tileOrigin[1] + height * (bottomY);
        double maxx = tileOrigin[0] + width * (rectangleExtent[2] + 1);
        double maxy = tileOrigin[1] + height * (topY + 1);
        BoundingBox rectangleBounds = new BoundingBox(minx, miny, maxx, maxy);

        return rectangleBounds;
    }

    protected long[] closestIndex(BoundingBox tileBounds) throws GridMismatchException {
        double wRes = tileBounds.getWidth() / getTileWidth();

        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;
        double bestResolution = -1.0;

        for (int i = 0; i < getNumLevels(); i++) {
            Grid grid = getGrid(i);

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
        Grid grid = getGrid(level);

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        double x = (tileBounds.getMinX() - tileOrigin()[0]) / width;

        double y = (tileBounds.getMinY() - tileOrigin()[1]) / height;

        long posX = Math.round(x);

        long posY = Math.round(y);

        if (Math.abs(x - posX) > 0.1 || Math.abs(y - posY) > 0.1) {
            throw new GridAlignmentMismatchException(x, posX, y, posY);
        }

        if (yBaseToggle) {
            posY = posY + grid.getNumTilesHigh();
        }

        long[] ret = {posX, posY, level};

        return ret;
    }

    public long[] closestRectangle(BoundingBox rectangleBounds) {
        double rectWidth = rectangleBounds.getWidth();
        double rectHeight = rectangleBounds.getHeight();

        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;

        // Now we loop over the resolutions until
        for (int i = 0; i < getNumLevels(); i++) {
            Grid grid = getGrid(i);

            double countX = rectWidth / (grid.getResolution() * getTileWidth());
            double countY = rectHeight / (grid.getResolution() * getTileHeight());

            double error =
                    Math.abs(countX - Math.round(countX)) + Math.abs(countY - Math.round(countY));

            if (error < bestError) {
                bestError = error;
                bestLevel = i;
            } else if (error >= bestError) {
                break;
            }
        }

        return closestRectangle(bestLevel, rectangleBounds);
    }

    /**
     * Find the rectangle of tiles that most closely covers the given rectangle
     *
     * @param level integer zoom level to consider tiles at
     * @param rectangeBounds rectangle to match
     * @return Array of long, the rectangle of tiles in tile coordinates: {minx, miny, maxx, maxy,
     *     level}
     */
    protected long[] closestRectangle(int level, BoundingBox rectangeBounds) {
        Grid grid = getGrid(level);

        double width = grid.getResolution() * getTileWidth();
        double height = grid.getResolution() * getTileHeight();

        long minX = (long) Math.floor((rectangeBounds.getMinX() - tileOrigin()[0]) / width);
        long minY = (long) Math.floor((rectangeBounds.getMinY() - tileOrigin()[1]) / height);
        long maxX = (long) Math.ceil(((rectangeBounds.getMaxX() - tileOrigin()[0]) / width));
        long maxY = (long) Math.ceil(((rectangeBounds.getMaxY() - tileOrigin()[1]) / height));

        if (yBaseToggle) {
            minY = minY + grid.getNumTilesHigh();
            maxY = maxY + grid.getNumTilesHigh();
        }

        // We substract one, since that's the tile at that position
        long[] ret = {minX, minY, maxX - 1, maxY - 1, level};

        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GridSet)) return false;

        GridSet other = (GridSet) obj;

        if (this == other) return true;

        boolean equals =
                Objects.equals(getSrs(), other.getSrs())
                        && Objects.equals(getName(), other.getName())
                        && Objects.equals(getDescription(), other.getDescription())
                        && Objects.equals(getTileWidth(), other.getTileWidth())
                        && Objects.equals(getTileHeight(), other.getTileHeight())
                        && Objects.equals(isTopLeftAligned(), other.isTopLeftAligned())
                        && Objects.equals(isyCoordinateFirst(), other.isyCoordinateFirst())
                        && Objects.equals(getOriginalExtent(), other.getOriginalExtent())
                        && Arrays.equals(gridLevels, other.gridLevels);

        return equals;
    }

    @Override
    public int hashCode() {
        int hashCode = HashCodeBuilder.reflectionHashCode(this);
        return hashCode;
    }

    public BoundingBox getBounds() {
        int i;
        long tilesWide, tilesHigh;

        for (i = (getNumLevels() - 1); i > 0; i--) {
            tilesWide = getGrid(i).getNumTilesWide();
            tilesHigh = getGrid(i).getNumTilesHigh();

            if (tilesWide == 1 && tilesHigh == 0) {
                break;
            }
        }

        tilesWide = getGrid(i).getNumTilesWide();
        tilesHigh = getGrid(i).getNumTilesHigh();
        long[] ret = {0, 0, tilesWide - 1, tilesHigh - 1, i};

        return boundsFromRectangle(ret);
    }

    /**
     * Returns the top left corner of the grid in the order used by the coordinate system. (Bad
     * idea)
     *
     * <p>Used for WMTS GetCapabilities
     */
    public double[] getOrderedTopLeftCorner(int gridIndex) {
        // First we will find the x,y pair, then we'll flip it if necessary
        double[] leftTop = new double[2];

        if (yBaseToggle) {
            leftTop[0] = tileOrigin()[0];
            leftTop[1] = tileOrigin()[1];
        } else {
            // We don't actually store the top coordinate, need to calculate it
            Grid grid = getGrid(gridIndex);

            double dTileHeight = getTileHeight();
            double dGridExtent = grid.getNumTilesHigh();

            double top = tileOrigin()[1] + dTileHeight * grid.getResolution() * dGridExtent;

            // Round off if we are within 0.5% of an integer value
            if (Math.abs(top - Math.round(top)) < (top / 200)) {
                top = Math.round(top);
            }

            leftTop[0] = tileOrigin()[0];
            leftTop[1] = top;
        }

        // Y coordinate first?
        if (isyCoordinateFirst()) {
            double[] ret = {leftTop[1], leftTop[0]};
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

    public int getNumLevels() {
        return gridLevels.length;
    }

    public Grid getGrid(final int zLevel) {
        return gridLevels[zLevel];
    }

    public void setGrid(final int zLevel, final Grid grid) {
        gridLevels[zLevel] = grid;
    }

    /** @param gridLevels the gridLevels to set */
    void setGridLevels(Grid[] gridLevels) {
        this.gridLevels = gridLevels;
    }

    /**
     * The base cordinates in x/y order, used to map tile indexes to coordinate bounding boxes.
     * These can either be top left or bottom left, so must be kept private.
     *
     * <p>This is a derived property of {@link #getOriginalExtent()} and {@link
     * #isTopLeftAligned()}.
     */
    public double[] tileOrigin() {
        BoundingBox extent = getOriginalExtent();
        double[] tileOrigin = {extent.getMinX(), yBaseToggle ? extent.getMaxY() : extent.getMinY()};
        return tileOrigin;
    }

    /** @return the yCoordinateFirst */
    public boolean isyCoordinateFirst() {
        return yCoordinateFirst;
    }

    /** @param yCoordinateFirst the yCoordinateFirst to set */
    void setyCoordinateFirst(boolean yCoordinateFirst) {
        this.yCoordinateFirst = yCoordinateFirst;
    }

    /** @return the scaleWarning */
    public boolean isScaleWarning() {
        return scaleWarning;
    }

    /** @param scaleWarning the scaleWarning to set */
    void setScaleWarning(boolean scaleWarning) {
        this.scaleWarning = scaleWarning;
    }

    /** @return the metersPerUnit */
    public double getMetersPerUnit() {
        return metersPerUnit;
    }

    /** @param metersPerUnit the metersPerUnit to set */
    void setMetersPerUnit(double metersPerUnit) {
        this.metersPerUnit = metersPerUnit;
    }

    /** @return the pixelSize */
    public double getPixelSize() {
        return pixelSize;
    }

    /** @param pixelSize the pixelSize to set */
    void setPixelSize(double pixelSize) {
        this.pixelSize = pixelSize;
    }

    /** @return the name */
    @Override
    public String getName() {
        return name;
    }

    /** @param name the name to set */
    void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** @return the srs */
    public SRS getSrs() {
        return srs;
    }

    /** @param srs the srs to set */
    void setSrs(SRS srs) {
        this.srs = srs;
    }

    /** @return the tileWidth */
    public int getTileWidth() {
        return tileWidth;
    }

    /** @param tileWidth the tileWidth to set */
    void setTileWidth(int tileWidth) {
        this.tileWidth = tileWidth;
    }

    /** @return the tileHeight */
    public int getTileHeight() {
        return tileHeight;
    }

    /** @param tileHeight the tileHeight to set */
    void setTileHeight(int tileHeight) {
        this.tileHeight = tileHeight;
    }

    /**
     * Evaluates wheter this GridSet is different enough from {@code another} so that if this
     * GridSet were replaced by {@code another} all layers referencing this GridSet should be
     * truncated.
     *
     * <p>The rule is, if any of the following properties differ: {@link #getBounds()}, {@link
     * #isTopLeftAligned()}, {@link #getTileHeight()}, {@link #getTileWidth()}, {@link #getSrs()},
     * OR none of the previously mentiond properties differ and the grid levels are different,
     * except if both the grids of {@code another} are a superset of the grids of this gridset (i.e.
     * they are all the same but {@code another} just has more zoom levels}.
     *
     * @return {@code true} if
     */
    public boolean shouldTruncateIfChanged(final GridSet another) {
        boolean needsTruncate = !getBounds().equals(another.getBounds());
        needsTruncate |= isTopLeftAligned() != another.isTopLeftAligned();
        needsTruncate |= getTileWidth() != another.getTileWidth();
        needsTruncate |= getTileHeight() != another.getTileHeight();
        needsTruncate |= !getSrs().equals(another.getSrs());

        if (needsTruncate) {
            return true;
        }
        // now check the zoom levels
        if (getNumLevels() > another.getNumLevels()) {
            return true;
        }
        for (int i = 0; i < getNumLevels(); i++) {
            if (!getGrid(i).equals(another.getGrid(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
