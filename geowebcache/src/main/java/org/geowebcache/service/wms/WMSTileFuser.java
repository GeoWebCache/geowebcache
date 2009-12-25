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
package org.geowebcache.service.wms;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ServletUtils;

/*
 * It will work as follows
 * 2) Based on the dimensions and bounding box of the request, GWC will determine the smallest available resolution that equals or exceeds the requested resolution.
 * 3) GWC will create a new in-memory raster, based on the best resolution and requested bounding box, and write the appropriate PNG tiles to it. Missing tiles will be requested from WMS.
 * 4) GWC will scale the raster down to the requested dimensions.
 * 5) GWC will then compress the raster to the desired output format and return the image. The image is not cached. 
 */
public class WMSTileFuser {
    private static Log log = LogFactory.getLog(WMSTileFuser.class);
    
    final StorageBroker sb;
    
    final GridSubset gridSubset;
    
    final TileLayer layer;
    
    final ImageMime outputFormat;
    
    int height;
    
    int width;
    
    final BoundingBox bounds;
    
    // For adjustment of final raster
    double xResolution;
    
    double yResolution;
    
    // The source resolution
    double srcResolution;
    
    int srcIdx;
    
    long[] srcRectangle;
    
    BoundingBox srcBounds;
    
    BoundingBox canvasBounds;
    
    int[] canvasSize = new int[2];
    
    // These are values before scaling
    int[] canvOfs = new int[4];
    
    double[] boundOfs = new double[4];
    
    BufferedImage canvas;
        
    protected WMSTileFuser(TileLayerDispatcher tld, StorageBroker sb, HttpServletRequest servReq) 
    throws GeoWebCacheException {
        this.sb = sb;
        
        String[] keys = { "layers", "format","srs","bbox", "width", "height" };
        String[] values = ServletUtils.selectedStringsFromMap(servReq.getParameterMap(), servReq.getCharacterEncoding(), keys);
        
        layer = tld.getTileLayer(values[0]);
        
        gridSubset = layer.getGridSubsetForSRS(SRS.getSRS(values[2]));
        
        outputFormat = (ImageMime) ImageMime.createFromFormat(values[1]);

        bounds = new BoundingBox(values[3]);
        
        width = Integer.valueOf(values[4]);
        
        height = Integer.valueOf(values[5]);        
        
    }
    
    protected WMSTileFuser(TileLayer layer, GridSubset gridSubset, BoundingBox bounds, int width, int height) {
        this.sb = null;
        this.outputFormat = ImageMime.png;
        this.layer = layer;
        this.gridSubset = gridSubset;
        this.bounds = bounds;
        this.width = width;
        this.height = height;
    }
    
    protected void determineSourceResolution() {
        xResolution = bounds.getWidth() / width;
        yResolution = bounds.getHeight() / height;
        
        double tmpResolution;
        // We use the smallest one
        if(yResolution < xResolution) {
            tmpResolution = yResolution;
        } else {
            tmpResolution = xResolution;
        }
        
        log.debug(
                "x res: " + xResolution 
                + " y res: " + yResolution
                + " tmpResolution: " + tmpResolution
        );
        
        // Cut ourselves 0.5% slack
        double compResolution = 1.005*tmpResolution;
        
        double[] resArray = gridSubset.getResolutions();
        
        for(srcIdx=0; srcIdx < resArray.length; srcIdx++) {
            srcResolution = resArray[srcIdx];
            if(srcResolution < compResolution) {
                break;
            }
        }
        
        if(srcIdx >= resArray.length) {
            srcIdx = resArray.length - 1;
        }
        
        log.debug("z: " + srcIdx + " , resolution: " + srcResolution + " ("+tmpResolution+")");
        
        // At worst, we have the best resolution possible
    }
    
    protected void determineRasterLayout() {
        srcRectangle = gridSubset.getCoverageIntersection(srcIdx, bounds);
        srcBounds = gridSubset.boundsFromRectangle(srcRectangle);
        
        //We now have the complete area, lets figure out our offsets
        //Positive means that there is blank space to the first tile,
        //negative means we will not use the entire tile
        boundOfs[0] = srcBounds.coords[0] - bounds.coords[0];
        boundOfs[1] = srcBounds.coords[1] - bounds.coords[1];
        boundOfs[2] = bounds.coords[2] - srcBounds.coords[2];
        boundOfs[3] = bounds.coords[3] - srcBounds.coords[3];
        
        canvasSize[0] = (int) Math.round(bounds.getWidth() / this.srcResolution);
        canvasSize[1] = (int) Math.round(bounds.getHeight() / this.srcResolution);
        
        //Calculate the corresponding pixel offsets. We'll stick to sane,
        // i.e. bottom left, coordinates at this point
        canvOfs[0] = (int) Math.round(boundOfs[0] / this.srcResolution);
        canvOfs[1] = (int) Math.round(boundOfs[1] / this.srcResolution);
        canvOfs[2] = (int) Math.round(boundOfs[2] / this.srcResolution);
        canvOfs[3] = (int) Math.round(boundOfs[3] / this.srcResolution);
        
        if(log.isDebugEnabled()) {
            log.debug("intersection rectangle: " + Arrays.toString(srcRectangle));
            log.debug("intersection bounds: " + srcBounds + " ("+bounds+")");
            log.debug("Bound offsets: " + Arrays.toString(boundOfs));
            log.debug("Canvas size: " + Arrays.toString(canvasSize) + "("+width+","+height+")");
            log.debug("Canvas offsets: " + Arrays.toString(canvOfs));               
        }  
    }
    
    protected void createRaster() 
    throws OutsideCoverageException, GeoWebCacheException, IOException {
        canvas = new BufferedImage(canvasSize[0], canvasSize[1], BufferedImage.TYPE_INT_ARGB);
        
        Graphics gfx = canvas.getGraphics();
        
        //Now we loop over all the relevant tiles and write them to the canvas,
        //Starting at the bottom, moving to the right and up
        long starty = srcRectangle[1];
        for (long gridy = starty; gridy <= srcRectangle[3]; gridy++) {
            
            int tiley = 0;
            int canvasy = (int) (srcRectangle[3] - gridy)*gridSubset.getTileHeight();
            int tileHeight = gridSubset.getTileHeight();
            
            if(canvOfs[3] > 0) {
                // Add padding
                canvasy += canvOfs[3];
            } else {
                // Top tile is cut off
                if (gridy == srcRectangle[3]) {
                    // This one starts at the top, so canvasy remains 0
                    tileHeight = tileHeight + canvOfs[3];
                    tiley = -canvOfs[3];
                } else {
                    // Offset that the first tile contributed,
                    // rather, we subtract what it did not contribute
                    canvasy += canvOfs[3];
                }
            }
            
            if(gridy == srcRectangle[1] && canvOfs[1] < 0) {
                // Check whether we only use part of the first tiles (bottom row)
                // Offset is negative, slice the bottom off the tile
                tileHeight = -canvOfs[1];
            }
                        
            long startx = srcRectangle[0];
            for (long gridx = startx; gridx <= srcRectangle[2]; gridx++) {

                long[] gridLoc = { gridx, gridy, srcIdx };

                ConveyorTile tile = new ConveyorTile(sb, layer.getName(), gridSubset.getName(), gridLoc, ImageMime.png, null, null, null, null);
                
                layer.getTile(tile);
                
                BufferedImage tileImg = ImageIO.read(new ByteArrayInputStream(tile.getContent()));
                
                int tilex = 0;
                int canvasx = (int) (gridx - startx)*gridSubset.getTileWidth();
                int tileWidth = gridSubset.getTileWidth();
                
                if(canvOfs[0] > 0) {
                    // Add padding
                    canvasx += canvOfs[0];
                } else {
                    //Leftmost tile is cut off
                    if (gridx == srcRectangle[0]) {
                        // This one starts to the left top, so canvasx remains 0
                        tileWidth = tileWidth + canvOfs[0];
                        tilex = -canvOfs[0];
                    } else {
                        // Offset that the first tile contributed,
                        // rather, we subtract what it did not contribute
                        canvasx += canvOfs[0];
                    }
                }
                
                if(gridx == srcRectangle[3] && canvOfs[2] < 0) {
                    // Check whether we only use part of the first tiles (bottom row)
                    // Offset is negative, slice the bottom off the tile
                    tileWidth = tileWidth + canvOfs[2];
                }
                
                // Cut down the tile to the part we want
                if(tileWidth != gridSubset.getTileWidth() || tileHeight != gridSubset.getTileHeight()) {
                    log.debug("tileImg.getSubimage("+tilex+","+tiley+","+tileWidth+","+tileHeight+")");
                    tileImg = tileImg.getSubimage(
                            tilex,
                            tiley,
                            tileWidth,
                            tileHeight
                            );
                }
                
                // Render the tile on the big canvas
                log.debug("drawImage(subtile,"+canvasx+","+canvasy+",null)");                
                gfx.drawImage(
                        tileImg, 
                        canvasx, 
                        canvasy,
                        null); // imageObserver
            }
        }
        
        gfx.dispose();
    }
    
    protected void scaleRaster() {
        if(canvasSize[0] != width || canvasSize[1] != height) {
            BufferedImage preTransform = canvas;
            
            canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            Graphics2D gfx = canvas.createGraphics();
            AffineTransform affineTrans =
                AffineTransform.getScaleInstance(
                        ((double) width)/preTransform.getWidth(),
                        ((double) height)/preTransform.getHeight() );
            
            log.debug("AffineTransform: " 
                    + (((double) width)/preTransform.getWidth()) + "," + 
                    + (((double) height)/preTransform.getHeight()) 
            );
            
            gfx.drawRenderedImage(preTransform,affineTrans);
            gfx.dispose();
        }
    }
    
    protected void writeResponse(HttpServletResponse response) 
    throws IOException, OutsideCoverageException, GeoWebCacheException {
        determineSourceResolution();
        determineRasterLayout();
        createRaster();
        scaleRaster();
        
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("image/png");
        response.setCharacterEncoding("UTF-8");
        
        ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("PNG").next();
        ImageWriteParam param  = writer.getDefaultWriteParam();
        
        ServletOutputStream os = response.getOutputStream();
        ImageOutputStream imgOut = new MemoryCacheImageOutputStream(os);
        writer.setOutput(imgOut);
        IIOImage image = new IIOImage(canvas, null, null);
        try {
            writer.write(null, image, param);
            imgOut.close();
            writer.dispose();
            os.close();
        } catch (IOException ioe) {
            log.debug("IOException writing untiled response to client: " + ioe.getMessage());
        }
    }
}
