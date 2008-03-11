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

    //private LayerProfile profile = null;

    protected int[] metaTileGridBounds = null; // minx,miny,maxx,maxy,zoomlevel

    //protected BBOX metaBbox = null;

    int metaX; // The actual X metatiling factor, after adjusting to bounds

    int metaY; // The actual Y metatiling factor, after adjusting to

    // bounds.

    int[][] tilesGridPositions = null;

    private BufferedImage img = null;

    private BufferedImage[] tiles = null;

    private long expiration = LayerProfile.CACHE_VALUE_UNSET;

    ImageWriter imageWriter = null;

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

//    /**
//     * Used for seeder to distinguish int[]s
//     * 
//     * @param profile
//     * @param metaGrid
//     * @param doesNothing
//     */
//    protected MetaTile(LayerProfile profile, int[] metaGrid, boolean doesNothing) {
//        this.profile = profile;
//        this.metaGrid = metaGrid;
//        metaBbox = profile.gridCalc.calcMetaBbox(metaGrid, metaX, metaY);
//        fillGridPositions();
//    }

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
        wmsparams.setBBOX(profile.gridCalc.bboxFromGridBounds(metaTileGridBounds));

        
        // Ask the WMS server, saves returned image into metaTile
        // TODO add exception for configurations that do not use metatiling
        String backendURL = "";
        int backendTries = 0; // keep track of how many backends we have tried
        while (img == null && backendTries < profile.wmsURL.length) {
            backendURL = profile.nextWmsURL();

            try {
                forwardRequest(profile, wmsparams, backendURL);
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

    private void forwardRequest(LayerProfile profile, WMSParameters wmsparams, String backendURL)
            throws IOException, ConnectException {
        if (log.isTraceEnabled()) {
            log.trace("Forwarding request to " + profile.wmsURL);
        }

        // Create an outgoing WMS request to the server
        Request wmsrequest = new Request(backendURL, wmsparams);
        URL wmsBackendUrl = new URL(wmsrequest.toString());
        URLConnection wmsBackendCon = wmsBackendUrl.openConnection();

        // Do we need to keep track of expiration headers?
        if (profile.expireCache == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE
                || profile.expireClients == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {

            String cacheControlHeader = wmsBackendCon
                    .getHeaderField("Cache-Control");
            Long wmsBackendMaxAge = extractHeaderMaxAge(cacheControlHeader);

            if (wmsBackendMaxAge != null) {
                log.info("Saved Cache-Control MaxAge from backend: "
                        + wmsBackendMaxAge.toString());
                expiration = wmsBackendMaxAge.longValue() * 1000;
            } else {
                log
                        .error("Layer profile wants MaxAge from backend, but backend does not provide this.");
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

    protected void createTiles(LayerProfile profile) {
        tiles = new BufferedImage[metaX * metaY];

        if (tiles.length > 1) {
            // final int tileSize = key.getTileSize();
            // final RenderingHints no_cache = new
            // RenderingHints(JAI.KEY_TILE_CACHE, null);
            
            for (int y = 0; y < metaY; y++) {
                for (int x = 0; x < metaX; x++) {
                    int tile = y * metaX + x;

                    int i = x * profile.width;
                    int j = (metaY - 1 - y) * profile.height;
                    
                    try {
                        //System.out.println("i: " + i + "  j:"+j);
                        tiles[tile] = img.getSubimage(i, j, profile.width,
                            profile.height);
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

    protected boolean writeTileToStream(int tileIdx, String format,
            OutputStream os) throws IOException {
        if (tiles == null) {
            return false;
        } else {
            // if(this.imageWriter == null)
            // initImageWriter(format);
            //if(tiles == null || tiles.length < tileIdx || tiles[tileIdx] == null 
            //        || format == null || os == null)
            //    log.error("tiles: " + tiles + " tiles.length:" + tiles.length 
            //            + " tileIdx:" + tileIdx + " format:" + format + " img" + tiles[tileIdx]);
            javax.imageio.ImageIO.write(tiles[tileIdx], format, os);
            return true;
        }
    }

    // private void initImageWriter(String format) {
    // imageWriter =
    // javax.imageio.ImageIO.getImageWritersByFormatName(format).next();
    //	
    // if(imageWriter == null) {
    // log.error("Unable to find ImageWriter for format" + format);
    // }
    // }

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
