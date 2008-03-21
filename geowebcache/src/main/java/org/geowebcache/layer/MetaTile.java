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
 *  
 */
package org.geowebcache.layer;

import java.awt.RenderingHints;
import java.awt.image.RenderedImage;

import javax.media.jai.JAI;
import javax.media.jai.operator.CropDescriptor;

import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.Request;
import org.geowebcache.service.wms.WMSParameters;

public class MetaTile {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.MetaTile.class);

    protected int[] metaTileGridBounds = null; // minx,miny,maxx,maxy,zoomlevel

    int metaX; // X metatiling factor, after adjusting to bounds

    int metaY; // Y metatiling factor, after adjusting to bounds

    int[][] tilesGridPositions = null; // the grid positions of the individual tiles

    private BufferedImage img = null; // buffer for storing the metatile

    private RenderedImage[] tiles = null; // array with tiles (after cropping)
    
    private long expiration = LayerProfile.CACHE_VALUE_UNSET;

    public boolean failed = false;

    /**
     * Used for requests by clients
     * 
     * @param profile
     * @param initGridPosition
     */
    protected MetaTile(int[] gridBounds, int[] tileGridPosition, int metaX, int metaY) {
        this.metaX = metaX;
        this.metaY = metaY;
        
        metaTileGridBounds = calculateMetaTileGridBounds(gridBounds, tileGridPosition);
        tilesGridPositions = calculateTilesGridPositions();
    }

    /**
     * Figures out the bounds of the metatile, in terms of the gridposition
     * of all contained tiles. To get the BBOX you need to add one tilewidth
     * to the top and right.
     * 
     * It also updates metaX and metaY to the actual metatiling factors
     * 
     * @param gridBounds
     * @param tileGridPosition
     * @return
     */
    private int[] calculateMetaTileGridBounds(int[] gridBounds, int[] tileGridPosition) {
        // Sanity checks that are hopefully redundant
        if(  ! (tileGridPosition[0] >= gridBounds[0] && tileGridPosition[0] <= gridBounds[2])
                &&(tileGridPosition[1] >= gridBounds[1] && tileGridPosition[1] <= gridBounds[3])
                &&(tileGridPosition[2] >= gridBounds[0] && tileGridPosition[2] <= gridBounds[2])
                &&(tileGridPosition[3] >= gridBounds[1] && tileGridPosition[3] <= gridBounds[3])) {
            log.error("calculateMetaTileGridBounds(): " + Arrays.toString(gridBounds) +" "+ Arrays.toString(tileGridPosition) );
            return null;
        }
        
        int[] metaTileGridBounds = new int[5];
        metaTileGridBounds[0] = tileGridPosition[0] - (tileGridPosition[0] % metaX);
        metaTileGridBounds[1] = tileGridPosition[1] - (tileGridPosition[1] % metaY);
        metaTileGridBounds[2] = Math.min( metaTileGridBounds[0] + metaX - 1, gridBounds[2]);
        metaTileGridBounds[3] = Math.min( metaTileGridBounds[1] + metaY - 1, gridBounds[3]);
        metaTileGridBounds[4] = tileGridPosition[2];
        
        // Save the actual metatiling factor, important at the boundaries
        metaX = metaTileGridBounds[2] - metaTileGridBounds[0] + 1;
        metaY = metaTileGridBounds[3] - metaTileGridBounds[1] + 1;
        
        return metaTileGridBounds;
    }

    /**
     * The bottom left grid position and zoomlevel for this metatile, used for
     * locking.
     * 
     * @return
     */
    protected int[] getMetaGridPos() {
        int[] gridPos = { metaTileGridBounds[0], metaTileGridBounds[1], metaTileGridBounds[4] };
        return gridPos;
    }
    
    /**
     * The bounds for the metatile
     * 
     * @return
     */
    protected int[] getMetaTileGridBounds() {
        return metaTileGridBounds;
    }

    /**
     * Creates an array with all the grid positions, used for cache keys
     */
    protected int[][] calculateTilesGridPositions() {        
        int[][] tilesGridPositions = new int[metaX * metaY][3];

        for (int y = 0; y < metaY; y++) {
            for (int x = 0; x < metaX; x++) {
                int tile = y * metaX + x;
                tilesGridPositions[tile][0] = metaTileGridBounds[0] + x;
                tilesGridPositions[tile][1] = metaTileGridBounds[1] + y;
                tilesGridPositions[tile][2] = metaTileGridBounds[4];
            }
        }
        
        return tilesGridPositions;
    }

    /**
     * Requests a metatile from the backend, or consults multiple backends
     * if necessary, and saves the result in the internal buffer for
     * future processing.
     * 
     * @param profile the profile provides the general parameters for the request
     * @param imageMime the desired image format
     * @return
     */
    protected String doRequest(LayerProfile profile, String imageMime) {
        WMSParameters wmsparams = profile.getWMSParamTemplate();

        // Fill in the blanks
        try {
            wmsparams.setImagemime(imageMime);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        wmsparams.setWidth(metaX * profile.width);
        wmsparams.setHeight(metaY * profile.height);
        BBOX metaBbox = profile.gridCalc.bboxFromGridBounds(metaTileGridBounds);
        metaBbox.adjustForGeoServer(wmsparams.getSrs());
        wmsparams.setBBOX(metaBbox);

        
        // Ask the WMS server, saves returned image into metaTile
        // TODO add exception for configurations that do not use metatiling
        // TODO move this closer to the WMSLayer?
        String backendURL = "";
        int backendTries = 0; // keep track of how many backends we have tried
        while (img == null && backendTries < profile.wmsURL.length) {
            backendURL = profile.nextWmsURL();
            
            boolean saveExpiration = (profile.expireCache == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE
                    || profile.expireClients == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE);

            try {
                forwardRequest(wmsparams, backendURL, saveExpiration);
            } catch (ConnectException ce) {
                log.error("Error forwarding request, " + backendURL
                        + wmsparams.toString() + " " + ce.getMessage());
            } catch (IOException ioe) {
                log.error("Error forwarding request, " + backendURL
                        + wmsparams.toString() + " " + ioe.getMessage());
                ioe.printStackTrace();
            }
            backendTries++;
        }

        if (img == null) {
            failed = true;
        }
        
        return backendURL + wmsparams.toString();
    }

    /**
     * Forwards the request to the actual WMS backend and saves the image
     * that comes back.
     * 
     * @param wmsparams the parameters for the request
     * @param backendURL the URL of the backend to use
     * @param saveExpiration whether to extract and save expiration headers
     * @throws IOException
     * @throws ConnectException
     */
    private void forwardRequest(WMSParameters wmsparams, String backendURL, boolean saveExpiration)
            throws IOException, ConnectException {
        // Create an outgoing WMS request to the server
        Request wmsrequest = new Request(backendURL, wmsparams);
        URL wmsBackendUrl = new URL(wmsrequest.toString());
        URLConnection wmsBackendCon = wmsBackendUrl.openConnection();

        // Do we need to keep track of expiration headers?
        if(saveExpiration) {
            String cacheControlHeader = wmsBackendCon
                    .getHeaderField("Cache-Control");
            Long wmsBackendMaxAge = extractHeaderMaxAge(cacheControlHeader);

            if (wmsBackendMaxAge != null) {
                log.info("Saved Cache-Control MaxAge from backend: "
                        + wmsBackendMaxAge.toString());
                expiration = wmsBackendMaxAge.longValue() * 1000;
            } else {
                log.error("Layer profile wants MaxAge from backend,"
                		+" but backend does not provide this.");
            }
        }

        img = ImageIO.read(wmsBackendCon.getInputStream());

        if (img == null) {
            // System.out.println("Failed fetching "+ wmsrequest.toString());
            log.error("Failed fetching: " + wmsrequest.toString());
        } else if (log.isDebugEnabled()) {
            // System.out.println("Fetched "+ wmsrequest.toString());
            log.debug("Requested and got: " + wmsrequest.toString());
        }

        if (log.isTraceEnabled()) {
            log.trace("Got image from backend, height: " + img.getHeight());
        }
    }

    /**
     * Cuts the metaTile into the specified number of tiles,
     * the actual number of tiles is determined by metaX and metaY,
     * not the width and height provided here.
     * 
     * @param tileWidth width of each tile
     * @param tileHeight height of each tile
     */
    protected void createTiles(int tileWidth, int tileHeight) {
        tiles = new RenderedImage[metaX * metaY];
        final RenderingHints no_cache = new RenderingHints(JAI.KEY_TILE_CACHE, null);
        
        if (tiles.length > 1) {
            for (int y = 0; y < metaY; y++) {
                for (int x = 0; x < metaX; x++) {
                    int i = x * tileWidth;
                    int j = (metaY - 1 - y) * tileHeight;
                    
                    try {
                    	RenderedImage tile = CropDescriptor.create(
                    			img, 
                    			new Float(i), new Float(j), 
                    			new Float(tileWidth), new Float(tileHeight), 
                    			no_cache);
                    	
                    	tiles[y * metaX + x] = tile;
                    	
                    } catch(RasterFormatException rfe) {
                        log.error("Unable to get i: "+i+", j:"+ j);
                        rfe.printStackTrace();
                    }
                }
            }
        } else {
            tiles[0] = img;
        }
    }

    /**
     * Outputs one tile from the internal array of tiles to a 
     * provided stream
     * 
     * @param tileIdx the index of the tile relative to the internal array
     * @param format the Java name for the format
     * @param os the outputstream
     * @return true if no error was encountered
     * @throws IOException
     */
    protected boolean writeTileToStream(int tileIdx, String format,
            OutputStream os) throws IOException {
        if (tiles == null) {
            return false;
        } else {
            if(! javax.imageio.ImageIO.write(tiles[tileIdx], format, os)) {
            	log.error("javax.imageio.ImageIO.write("
            			+tiles[tileIdx].toString()+ ","
            			+ format + "," + os.toString() + ")");
            }
            return true;
        }
    }

    private static Long extractHeaderMaxAge(String cacheControlHeader) {
        if (cacheControlHeader == null) {
            return null;
        }

        String expression = "max-age=([0-9]*)[ ,]";
        Pattern p = Pattern.compile(expression);
        Matcher m = p.matcher(cacheControlHeader.toLowerCase());

        if (m.find()) {
            return new Long(Long.parseLong(m.group(1)));
        } else {
            return null;
        }
    }

    protected int[][] getTilesGridPositions() {
        return tilesGridPositions;
    }

    protected BufferedImage getRawImage() {
        return img;
    }

    protected long getExpiration() {
        return expiration;
    }

    public String debugString() {
        return " metaX: " + metaX + " metaY: " + metaY + " metaGrid: "
                + Arrays.toString(metaTileGridBounds);
    }
}
