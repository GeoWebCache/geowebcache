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
package org.geowebcache.layer.wms;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.imageio.spi.ImageReaderSpi;
import javax.media.jai.JAI;
import javax.media.jai.operator.CropDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.resources.image.ImageUtilities;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.GridCalculator;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.layer.SRS;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.wms.BBOX;

public class WMSMetaTile extends MetaTile {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSMetaTile.class);

    private BufferedImage img = null; // buffer for storing the metatile, if

    // it's an image

    private RenderedImage[] tiles = null; // array with tiles (after cropping)

    private final RenderingHints no_cache = new RenderingHints(JAI.KEY_TILE_CACHE, null);

    protected WMSLayer wmsLayer = null;
    
    protected boolean requestTiled = false;

    protected String fullParameters;
    
    /**
     * Used for requests by clients
     * 
     * @param profile
     * @param initGridPosition
     */
    protected WMSMetaTile(WMSLayer layer, SRS srs, MimeType mimeType,
            int[] gridBounds, int[] tileGridPosition, int metaX, int metaY, String fullParameters) {
        super(srs, mimeType, gridBounds, tileGridPosition, metaX, metaY);
        this.wmsLayer = layer;
        this.fullParameters = fullParameters;
        
        //ImageUtilities.allowNativeCodec("png", ImageReaderSpi.class, false);
    }

    protected String getWMSParams() throws GeoWebCacheException {
        String baseParameters = wmsLayer.getWMSRequestTemplate();
        //int srsIdx = wmsLayer.getSRSIndex(srs);

        // Fill in the blanks
        StringBuilder strBuilder = new StringBuilder(baseParameters);
        
        strBuilder.append("&FORMAT=").append(mimeType.getFormat());
        strBuilder.append("&SRS=").append(srs.toString());
        strBuilder.append("&WIDTH=").append(metaX * GridCalculator.TILEPIXELS);
        strBuilder.append("&HEIGHT=").append(metaY * GridCalculator.TILEPIXELS);

        GridCalculator gridCalc = wmsLayer.getGrid(srs).getGridCalculator();
        BBOX metaBbox = gridCalc.bboxFromGridBounds(metaTileGridBounds);
        
        strBuilder.append("&BBOX=").append(metaBbox);
        
        strBuilder.append(fullParameters);
        
        return strBuilder.toString();
    }

    protected WMSLayer getLayer() {
        return wmsLayer;
    }

    protected void setImageBytes(byte[] image) throws GeoWebCacheException {
        if (image == null) {
            throw new GeoWebCacheException("WMSMetaTile.setImageBytes() "
                    + " received null instead of byte[]");
        }

        InputStream is = new ByteArrayInputStream(image);
        try {
            this.img = ImageIO.read(is);
        } catch (IOException ioe) {
            throw new GeoWebCacheException("WMSMetaTile.setImageBytes() "
                    + "failed on ImageIO.read(byte[" + image.length + "])");
        }
    }

    /**
     * Cuts the metaTile into the specified number of tiles, the actual number
     * of tiles is determined by metaX and metaY, not the width and height
     * provided here.
     * 
     * @param tileWidth
     *            width of each tile
     * @param tileHeight
     *            height of each tile
     */
    protected void createTiles(int tileWidth, int tileHeight, boolean useJAI) {
        int tileCount = metaX * metaY;
        tiles = new RenderedImage[tileCount];

        if (tileCount > 1) {
            for (int y = 0; y < metaY; y++) {
                for (int x = 0; x < metaX; x++) {
                    int i = x * tileWidth;
                    int j = (metaY - 1 - y) * tileHeight;

                    tiles[y * metaX + x] = createTile(i, j, tileWidth,
                            tileHeight, useJAI);
                }
            }
        } else {
            tiles[0] = img;
        }
    }

    /**
     * Extracts a single tile from the metatile. Handles JPEG
     * 
     * @param minX
     * @param minY
     * @param tileWidth
     * @param tileHeight
     * @return
     */
    private RenderedImage createTile(int minX, int minY, int tileWidth,
            int tileHeight, boolean useJAI) {

        RenderedImage tile = null;

        // TODO JAI is messing up for JPEG, this is a hack, retest
        if (useJAI) {
            // Use JAI
            try { 
            tile = CropDescriptor.create(img, new Float(minX), new Float(minY),
                    new Float(tileWidth), new Float(tileHeight), no_cache);
            } catch (IllegalArgumentException iae) {
                log.error("Error cropping, image is " 
                        + img.getWidth() + "x" + img.getHeight()
                        + ", requesting a "+tileWidth+"x"+tileHeight
                        +" tile starting at "+minX+","+minY+".");
                log.error("Message from JAI: " + iae.getMessage());
                iae.printStackTrace();
            }
        } else {
            // Don't use JAI
            try {
                tile = img.getSubimage(minX, minY, tileWidth, tileHeight);
            } catch (RasterFormatException rfe) {
                log.error("RendereedImage.getSubimage(" + minX + "," + minY
                        + "," + tileWidth + "," + tileHeight
                        + ") threw exception:");
                rfe.printStackTrace();
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Thread: " + Thread.currentThread().getName() + "\n"
                    + tile.toString() + ", "
                    + "Information from tile (width, height, minx, miny): "
                    + tile.getWidth() + ", " + tile.getHeight() + ", "
                    + tile.getMinX() + ", " + tile.getMinY() + "\n"
                    + "Information set (width, height, minx, miny): "
                    + new Float(tileWidth) + ", " + new Float(tileHeight)
                    + ", " + new Float(minX) + ", " + new Float(minY));
        }

        return tile;
    }

    /**
     * Outputs one tile from the internal array of tiles to a provided stream
     * 
     * @param tileIdx
     *            the index of the tile relative to the internal array
     * @param format
     *            the Java name for the format
     * @param os
     *            the outputstream
     * @return true if no error was encountered
     * @throws IOException
     */
    protected boolean writeTileToStream(int tileIdx, OutputStream os)
            throws IOException {
        if (tiles != null) {
            String format = super.mimeType.getInternalName();

            if (log.isDebugEnabled()) {
                log.debug("Thread: " + Thread.currentThread().getName()
                        + " writing: " + tileIdx);
            }

            if (!javax.imageio.ImageIO.write(tiles[tileIdx], format, os)) {
                log.error("javax.imageio.ImageIO.write("
                        + tiles[tileIdx].toString() + "," + format + ","
                        + os.toString() + ")");
            }
            return true;
        }

        return false;
    }

    public String debugString() {
        return " metaX: " + metaX + " metaY: " + metaY + " metaGrid: "
                + Arrays.toString(metaTileGridBounds);
    }
}
