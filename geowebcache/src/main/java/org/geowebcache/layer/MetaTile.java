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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import org.geowebcache.grid.GridSubSet;
import org.geowebcache.grid.SRS;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;

public abstract class MetaTile implements TileResponseReceiver {

    // minx,miny,maxx,maxy,zoomlevel
    protected long[] metaGridCov = null; 

    // the grid positions of the individual tiles
    protected long[][] tilesGridPositions = null; 

    // X metatiling factor, after adjusting to bounds
    protected int metaX; 

    // Y metatiling factor, after adjusting to bounds
    protected int metaY; 

    protected GridSubSet gridSubSet;

    protected long status = -1;

    protected boolean error = false;

    protected String errorMessage;

    protected long expiresHeader = -1;

    protected MimeType responseFormat;
    
    protected FormatModifier formatModifier;

    /**
     * The the request format is the format used for the request to the backend. 
     * 
     * The response format is what the tiles are actually saved as. The primary
     * example is to use image/png or image/tiff for backend requests, and then
     * save the resulting tiles to JPEG to avoid loss of quality.
     * 
     * @param srs
     * @param responseFormat
     * @param requestFormat
     * @param tileGridPosition
     * @param metaX
     * @param metaY
     */
    protected MetaTile(GridSubSet gridSubSet, MimeType responseFormat, FormatModifier formatModifier, 
            long[] tileGridPosition, int metaX, int metaY) {
        this.gridSubSet = gridSubSet;
        this.responseFormat = responseFormat;
        this.formatModifier = formatModifier;
        this.metaX = metaX;
        this.metaY = metaY;

        metaGridCov = calculateMetaTileGridBounds(gridSubSet.getCoverage((int) tileGridPosition[2]), tileGridPosition);
        tilesGridPositions = calculateTilesGridPositions();
    }

    public int getStatus() {
        return (int) status;
    }

    public void setStatus(int status) {
        this.status = (long) status;
    }

    public boolean getError() {
        return this.error;
    }

    public void setError() {
        this.error = true;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getExpiresHeader() {
        return this.expiresHeader;
    }
    

    public void setExpiresHeader(long seconds) {
        this.expiresHeader = seconds;
    }

    /**
     * Figures out the bounds of the metatile, in terms of the gridposition of
     * all contained tiles. To get the BBOX you need to add one tilewidth to the
     * top and right.
     * 
     * It also updates metaX and metaY to the actual metatiling factors
     * 
     * @param gridBounds
     * @param tileGridPosition
     * @return
     */
    private long[] calculateMetaTileGridBounds(long[] coverage, long[] tileIdx) {
        long[] metaGridCov = new long[5];
        metaGridCov[0] = tileIdx[0] - (tileIdx[0] % metaX);
        metaGridCov[1] = tileIdx[1] - (tileIdx[1] % metaY);
        metaGridCov[2] = Math.min(metaGridCov[0] + metaX - 1, coverage[2]);
        metaGridCov[3] = Math.min(metaGridCov[1] + metaY - 1, coverage[3]);
        metaGridCov[4] = tileIdx[2];

        // Save the actual metatiling factor, important at the boundaries
        metaX = (int) (metaGridCov[2] - metaGridCov[0] + 1);
        metaY = (int) (metaGridCov[3] - metaGridCov[1] + 1);

        return metaGridCov;
    }

    /**
     * Creates an array with all the grid positions, used for cache keys
     */
    private long[][] calculateTilesGridPositions() {
        if (metaX < 0 || metaY < 0) {
            return null;
        }
        
        long[][] tilesGridPos = new long[metaX * metaY][3];

        for (int y = 0; y < metaY; y++) {
            for (int x = 0; x < metaX; x++) {
                int tile = y * metaX + x;
                tilesGridPos[tile][0] = metaGridCov[0] + x;
                tilesGridPos[tile][1] = metaGridCov[1] + y;
                tilesGridPos[tile][2] = metaGridCov[4];
            }
        }

        return tilesGridPos;
    }

    /**
     * The bottom left grid position and zoomlevel for this metatile, used for
     * locking.
     * 
     * @return
     */
    public long[] getMetaGridPos() {
        long[] gridPos = { metaGridCov[0], metaGridCov[1], metaGridCov[4] };
        return gridPos;
    }

    /**
     * The bounds for the metatile
     * 
     * @return
     */
    public long[] getMetaTileGridBounds() {
        return metaGridCov;
    }

    public long[][] getTilesGridPositions() {
        return tilesGridPositions;
    }

    public SRS getSRS() {
        return this.gridSubSet.getSRS();
    }
    
    public MimeType getResponseFormat() {
        return this.responseFormat;
    }
    
    public MimeType getRequestFormat() {
        if(formatModifier == null) {
            return this.responseFormat;
        } else {
            return this.formatModifier.getRequestFormat();
        }
    }
}
