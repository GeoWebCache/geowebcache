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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.media.jai.JAI;
import javax.media.jai.operator.CropDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;

public class WMSMetaTile extends MetaTile {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSMetaTile.class);

    private BufferedImage img = null; // buffer for storing the metatile, if it is an image

    private RenderedImage[] tiles = null; // array with tiles (after cropping)

    private final RenderingHints no_cache = new RenderingHints(JAI.KEY_TILE_CACHE, null);

    protected WMSLayer wmsLayer = null;
    
    protected boolean requestTiled = false;

    protected String fullParameters;
    
    protected int[] gutter = new int[4]; // L,B,R,T in pixels
    
    /**
     * Used for requests by clients
     * 
     * @param profile
     * @param initGridPosition
     */
    protected WMSMetaTile(WMSLayer layer, GridSubset gridSubset, MimeType responseFormat, FormatModifier formatModifier, 
            long[] tileGridPosition, int metaX, int metaY, String fullParameters) {
        super(gridSubset, responseFormat, formatModifier, tileGridPosition, metaX, metaY);
        this.wmsLayer = layer;
        this.fullParameters = fullParameters;
        
        //ImageUtilities.allowNativeCodec("png", ImageReaderSpi.class, false);
    }

    protected String getWMSParams() throws GeoWebCacheException {
        String baseParameters = wmsLayer.getWMSRequestTemplate(this.getResponseFormat(), WMSLayer.RequestType.MAP);

        BoundingBox metaBbox = gridSubset.boundsFromRectangle(metaGridCov);
        
        // Fill in the blanks
        StringBuilder strBuilder = new StringBuilder(baseParameters);
        if(formatModifier == null) {
            strBuilder.append("&FORMAT=").append(responseFormat.getFormat());
        } else {
            strBuilder.append("&FORMAT=").append(formatModifier.getRequestFormat().getFormat());
        }

        strBuilder.append("&SRS=").append(wmsLayer.backendSRSOverride(gridSubset.getSRS()));
        
        if(wmsLayer.gutter == 0 || metaX*metaY == 1) {
            strBuilder.append("&WIDTH=").append(metaX * gridSubset.getTileWidth());
            strBuilder.append("&HEIGHT=").append(metaY * gridSubset.getTileHeight());
            strBuilder.append("&BBOX=").append(metaBbox);
        } else {
            adjustParamsForGutter(strBuilder);
        }
        
        strBuilder.append(fullParameters);
        
        return strBuilder.toString();
    }
    
    /***
     * Adding a gutter should be really easy, just add to all sides, right ?
     * 
     * But GeoServer / GeoTools, and possibly other WMS servers, can get mad 
     * if we exceed 180,90 (or the equivalent for other projections), 
     * so we'lll treat those with special care.
     * 
     * @param strBuilder
     * @param metaTileGridBounds
     */
    protected void adjustParamsForGutter(StringBuilder strBuilder) 
    throws GeoWebCacheException {
        //GridCalculator gridCalc = wmsLayer.getGrid(srs).getGridCalculator();
        
        long[] layerCov = gridSubset.getCoverage((int) this.metaGridCov[4]);
        
        BoundingBox metaBbox = gridSubset.boundsFromRectangle(metaGridCov);
        
        double[] metaCoords = metaBbox.coords;
        
        long pixelWidth = metaX * gridSubset.getTileWidth();
        long pixelHeight = metaY * gridSubset.getTileHeight();
        
        double widthRelDelta = ((1.0 * pixelWidth + wmsLayer.gutter) / pixelWidth ) - 1.0;
        double heightRelDelta = ((1.0 * pixelHeight + wmsLayer.gutter) / pixelHeight ) - 1.0;
        
        double coordWidth = metaCoords[2] - metaCoords[0];
        double coordHeight = metaCoords[3] - metaCoords[1];
        
        double coordWidthDelta = coordWidth * widthRelDelta;
        double coordHeightDelta = coordHeight * heightRelDelta;
        
        if(layerCov[0] < metaGridCov[0]) {
            pixelWidth += wmsLayer.gutter;
            gutter[0] = wmsLayer.gutter;
            metaCoords[0] -= coordWidthDelta;
        }
        if(layerCov[1] < metaGridCov[1]) {
            pixelHeight += wmsLayer.gutter;
            gutter[1] = wmsLayer.gutter;
            metaCoords[1] -= coordHeightDelta;
        }
        if(layerCov[2] > metaGridCov[2]) {
            pixelWidth += wmsLayer.gutter;
            gutter[2] = wmsLayer.gutter;
            metaCoords[2] += coordWidthDelta;
        }
        if(layerCov[3] > metaGridCov[3]) {
            pixelHeight += wmsLayer.gutter;
            gutter[3] = wmsLayer.gutter;
            metaCoords[3] += coordHeightDelta;
        }
        
        strBuilder.append("&WIDTH=").append(pixelWidth);
        strBuilder.append("&HEIGHT=").append(pixelHeight);
        strBuilder.append("&BBOX=").append(metaBbox);
    }
    
    protected WMSLayer getLayer() {
        return wmsLayer;
    }

    protected void setImageBytes(byte[] image) throws GeoWebCacheException {
        if (image == null || image.length == 0) {
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
        if(img == null) {
            throw new GeoWebCacheException("ImageIO.read(InputStream) returned null. Unable to read image.");
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
    protected void createTiles(int tileHeight, int tileWidth, boolean useJAI) {
        int tileCount = metaX * metaY;
        tiles = new RenderedImage[tileCount];

        if (tileCount > 1) {
            for (int y = 0; y < metaY; y++) {
                for (int x = 0; x < metaX; x++) {
                    int i = x * tileWidth + gutter[0];
                    int j = (metaY - 1 - y) * tileHeight + gutter[3];

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
            String format = super.responseFormat.getInternalName();

            if (log.isDebugEnabled()) {
                log.debug("Thread: " + Thread.currentThread().getName() + " writing: " + tileIdx);
            }
            
            
            // TODO should we recycle the writers ? 
            ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName(format).next();
            ImageWriteParam param  = writer.getDefaultWriteParam();
            
            if(this.formatModifier != null) {
                param = formatModifier.adjustImageWriteParam(param);
            }
            
            ImageOutputStream imgOut = new MemoryCacheImageOutputStream(os);
            writer.setOutput(imgOut);
            IIOImage image = new IIOImage(tiles[tileIdx], null, null);
            writer.write(null, image, param);
            imgOut.close();
            writer.dispose();
            
            return true;
        }

        return false;
    }

    public String debugString() {
        return " metaX: " + metaX + " metaY: " + metaY + " metaGridCov: "
                + Arrays.toString(metaGridCov);
    }
}
