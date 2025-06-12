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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.filter.request;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.TileLayer;

/**
 * A raster filter allows to optimize data loading by avoiding the generation of requests and the caching of empty tiles
 * for tiles that are inside the definition area of the layer but that are known (via external information) to contain
 * no data
 *
 * <p>To conserve memory, the layer bounds are used.
 *
 * <p>The raster must match the dimensions of the zoomlevel and use 0x000000 for tiles that are valid.
 */
public abstract class RasterFilter extends RequestFilter {

    @Serial
    private static final long serialVersionUID = -5695649347572928323L;

    private static Logger log = Logging.getLogger(RasterFilter.class.getName());

    private Integer zoomStart;

    private Integer zoomStop;

    private Boolean resample;

    private Boolean preload;

    private Boolean debug;

    public transient Map<String, BufferedImage[]> matrices;

    public RasterFilter() {}

    /** @return the zoomStart */
    public Integer getZoomStart() {
        return zoomStart;
    }

    /** @param zoomStart the zoomStart to set */
    public void setZoomStart(Integer zoomStart) {
        this.zoomStart = zoomStart;
    }

    /** @return the zoomStop */
    public Integer getZoomStop() {
        return zoomStop;
    }

    /** @param zoomStop the zoomStop to set */
    public void setZoomStop(Integer zoomStop) {
        this.zoomStop = zoomStop;
    }

    /** @return the resample */
    public Boolean getResample() {
        return resample;
    }

    /** @param resample the resample to set */
    public void setResample(Boolean resample) {
        this.resample = resample;
    }

    /** @return the preload */
    public Boolean getPreload() {
        return preload;
    }

    /** @param preload the preload to set */
    public void setPreload(Boolean preload) {
        this.preload = preload;
    }

    /** @return the debug */
    public Boolean getDebug() {
        return debug;
    }

    /** @param debug the debug to set */
    public void setDebug(Boolean debug) {
        this.debug = debug;
    }

    @Override
    public void apply(ConveyorTile convTile) throws RequestFilterException {
        long[] idx = convTile.getTileIndex().clone();
        String gridSetId = convTile.getGridSetId();

        // Basic bounds test first
        try {
            convTile.getGridSubset().checkCoverage(idx);
        } catch (OutsideCoverageException oce) {
            throw new BlankTileException(this);
        }

        int zoomDiff = 0;

        // Three scenarios below:
        // 1. z is too low , upsample if resampling is enabled
        // 2. z is within range, downsample one level and apply
        // 3. z is too large , downsample
        if (zoomStart != null && idx[2] < zoomStart) {
            if (resample == null || !resample) {
                // Filter does not apply, zoomlevel is too low
                return;
            } else {
                // Upsample
                zoomDiff = (int) (idx[2] - zoomStart);
                idx[0] = idx[0] << (-1 * zoomDiff);
                idx[1] = idx[1] << (-1 * zoomDiff);
                idx[2] = zoomStart;
            }
        } else if (idx[2] < zoomStop) {
            // Sample one level higher
            idx[0] = idx[0] * 2;
            idx[1] = idx[1] * 2;
            idx[2] = idx[2] + 1;
        } else {
            // Reduce to highest supported resolution
            zoomDiff = (int) (idx[2] - zoomStop);
            idx[0] = idx[0] >> zoomDiff;
            idx[1] = idx[1] >> zoomDiff;
            idx[2] = zoomStop;
        }

        if (matrices == null || matrices.get(gridSetId) == null || matrices.get(gridSetId)[(int) idx[2]] == null) {
            try {
                setMatrix(convTile.getLayer(), gridSetId, (int) idx[2], false);
            } catch (Exception e) {
                log.log(
                        Level.SEVERE,
                        "Failed to load matrix for "
                                + this.getName()
                                + ", "
                                + gridSetId
                                + ", "
                                + idx[2]
                                + " : "
                                + e.getMessage());
                throw new RequestFilterException(
                        this, 500, "Failed while trying to load filter for " + idx[2] + ", please check the logs");
            }
        }

        if (zoomDiff == 0) {
            if (!lookup(convTile.getGridSubset(), idx)) {
                if (debug != null && debug) {
                    throw new GreenTileException(this);
                } else {
                    throw new BlankTileException(this);
                }
            }
        } else if (zoomDiff > 0) {
            if (!lookupQuad(convTile.getGridSubset(), idx)) {
                if (debug != null && debug) {
                    throw new GreenTileException(this);
                } else {
                    throw new BlankTileException(this);
                }
            }
        } else if (zoomDiff < 0) {
            if (!lookupSubsample(convTile.getGridSubset(), idx, zoomDiff)) {
                if (debug != null && debug) {
                    throw new GreenTileException(this);
                } else {
                    throw new BlankTileException(this);
                }
            }
        }
    }

    /** Loops over all the zoom levels and initializes the lookup images. */
    @Override
    public void initialize(TileLayer layer) throws GeoWebCacheException {
        if (preload != null && preload) {
            for (String gridSetId : layer.getGridSubsets()) {
                GridSubset grid = layer.getGridSubset(gridSetId);

                for (int i = 0; i <= zoomStop; i++) {
                    try {
                        setMatrix(layer, grid.getName(), i, false);
                    } catch (Exception e) {
                        log.log(
                                Level.SEVERE,
                                "Failed to load matrix for "
                                        + this.getName()
                                        + ", "
                                        + grid.getName()
                                        + ", "
                                        + i
                                        + " : "
                                        + e.getMessage());
                    }
                }
            }
        }
    }

    /** Performs a lookup against an internal raster. */
    private boolean lookup(GridSubset grid, long[] idx) {
        BufferedImage mat = matrices.get(grid.getName())[(int) idx[2]];

        long[] gridCoverage = grid.getCoverage((int) idx[2]);

        // Changing index to top left hand origin
        long x = idx[0] - gridCoverage[0];
        long y = gridCoverage[3] - idx[1];

        return (mat.getRaster().getSample((int) x, (int) y, 0) == 0);
    }

    /**
     * Performs a lookup against an internal raster. The sampling is actually done against 4 pixels, idx should already
     * have been modified to use one level higher than strictly necessary.
     */
    private boolean lookupQuad(GridSubset grid, long[] idx) {
        BufferedImage mat = matrices.get(grid.getName())[(int) idx[2]];

        long[] gridCoverage = grid.getCoverage((int) idx[2]);

        // Changing index to top left hand origin
        int baseX = (int) (idx[0] - gridCoverage[0]);
        int baseY = (int) (gridCoverage[3] - idx[1]);

        int width = mat.getWidth();
        int height = mat.getHeight();

        int x = baseX;
        int y = baseY;

        // We're checking 4 samples. The base is bottom left hand corner
        boolean hasData = false;

        // BL, BR, TL, TR
        int[] xOffsets = {0, 1, 0, 1};
        int[] yOffsets = {0, 0, 1, 1};

        // Lock, in case someone wants to replace the matrix
        synchronized (mat) {
            try {
                for (int i = 0; i < 4 && !hasData; i++) {
                    x = baseX + xOffsets[i];
                    y = baseY - yOffsets[i];

                    if (x > -1 && x < width && y > -1 && y < height) {
                        if (mat.getRaster().getSample(x, y, 0) == 0) {
                            hasData = true;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException aioob) {
                log.log(Level.SEVERE, "x:" + x + "  y:" + y + " (" + mat.getWidth() + " " + mat.getHeight() + ")");
            }
        }

        return hasData;
    }

    private boolean lookupSubsample(GridSubset grid, long[] idx, int zoomDiff) {
        BufferedImage mat = matrices.get(grid.getName())[(int) idx[2]];

        int sampleChange = 1 << (-1 * zoomDiff);

        long[] gridCoverage = grid.getCoverage((int) idx[2]);

        // Changing index to top left hand origin
        int baseX = (int) (idx[0] - gridCoverage[0]);
        int baseY = (int) (gridCoverage[3] - idx[1]);

        int width = mat.getWidth();
        int height = mat.getHeight();

        int startX = Math.max(0, baseX);
        int stopX = Math.min(width, baseX + sampleChange);
        int startY = Math.min(baseY, height - 1);
        int stopY = Math.max(0, baseY - sampleChange);

        int x = -1;
        int y = -1;

        // Lock, in case someone wants to replace the matrix
        synchronized (mat) {
            try {
                // Try center and edges first
                x = (stopX + startX) / 2;
                y = (startY + stopY) / 2;
                if (mat.getRaster().getSample(x, y, 0) == 0
                        || mat.getRaster().getSample(stopX - 1, stopY + 1, 0) == 0
                        || mat.getRaster().getSample(stopX - 1, startY, 0) == 0
                        || mat.getRaster().getSample(startX, stopY + 1, 0) == 0) {
                    return true;
                }

                // Do the hard work, loop over all pixels
                x = startX;
                y = startY;

                // Left to right
                while (x < stopX) {
                    // Bottom to top
                    while (y > stopY) {
                        if (mat.getRaster().getSample(x, y, 0) == 0) {
                            return true;
                        }
                        y--;
                    }
                    x++;
                    y = startY;
                }
            } catch (ArrayIndexOutOfBoundsException aioob) {
                log.log(Level.SEVERE, "x:" + x + "  y:" + y + " (" + mat.getWidth() + " " + mat.getHeight() + ")");
            }
        }

        return false;
    }

    /**
     * This function will load the matrix from the appropriate source.
     *
     * @param layer Access to the layer, to make the object simpler
     * @param z (zoom level)
     * @param replace Whether to update if a matrix exists
     */
    public synchronized void setMatrix(TileLayer layer, String gridSetId, int z, boolean replace)
            throws IOException, GeoWebCacheException {

        if (matrices == null) {
            matrices = new HashMap<>();
        }

        if (matrices.get(gridSetId) == null) {
            matrices.put(gridSetId, new BufferedImage[zoomStop + 1]);
        }

        if (matrices.get(gridSetId)[z] == null) {
            matrices.get(gridSetId)[z] = loadMatrix(layer, gridSetId, z);

        } else if (replace) {
            BufferedImage oldImg = matrices.get(gridSetId)[z];
            BufferedImage[] matArray = matrices.get(gridSetId);

            // Get the replacement
            BufferedImage newImg = loadMatrix(layer, gridSetId, z);

            // We need to lock it
            synchronized (oldImg) {
                matArray[z] = newImg;
            }
        }
    }

    /** Helper function for calculating width and height */
    protected int[] calculateWidthHeight(GridSubset grid, int z) throws GeoWebCacheException {
        long[] bounds = grid.getCoverage(z);

        int[] widthHeight = new int[2];
        widthHeight[0] = (int) (bounds[2] - bounds[0] + 1);
        widthHeight[1] = (int) (bounds[3] - bounds[1] + 1);

        return widthHeight;
    }

    protected abstract BufferedImage loadMatrix(TileLayer layer, String gridSetId, int zoomLevel)
            throws IOException, GeoWebCacheException;
}
