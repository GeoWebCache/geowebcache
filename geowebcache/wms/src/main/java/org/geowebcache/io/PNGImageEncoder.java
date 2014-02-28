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
 * @author Nicola Lagomarsini, GeoSolutions S.A.S., Copyright 2014
 * 
 */
package org.geowebcache.io;

import it.geosolutions.imageio.plugins.png.PNGWriter;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.geotools.image.ImageWorker;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;

import ar.com.hjg.pngj.FilterType;

/**
 * Subclass of the {@link ImageEncoderImpl} class optimized for the PNG format. It uses a new PNGEncoder which provides better performances.
 */
public class PNGImageEncoder extends ImageEncoderImpl {
    /** Filter type associated string*/
    private static final String FILTER_TYPE = "filterType";
    /** Logger used*/
    private static final Logger LOGGER = Logger.getLogger(PNGImageEncoder.class);
    /** Supported mime types*/
    private static List<String> supportedMimeTypes;
    /** Boolean used for disabling the png encoding*/
    private boolean disablePNG;
    /** Boolean indicating if the aggressive output stream is supported*/
    private final boolean isAggressiveSupported;
    /** Default quality value*/
    private final static float DEFAULT_QUALITY = 1;
    /** Quality value*/
    private final float quality;

    static {
        supportedMimeTypes = new ArrayList<String>();
        supportedMimeTypes.add(ImageMime.png.getMimeType());
        supportedMimeTypes.add(ImageMime.png8.getMimeType());
        supportedMimeTypes.add(ImageMime.png24.getMimeType());
        supportedMimeTypes.add(ImageMime.png_24.getMimeType());
    }

    public PNGImageEncoder(boolean aggressiveOutputStreamOptimization, Float quality,
            List<String> writerSpi, Map<String, String> inputParams) {
        super(aggressiveOutputStreamOptimization, supportedMimeTypes, writerSpi, inputParams);

        if (quality != null) {
            this.quality = quality;
        } else {
            this.quality = DEFAULT_QUALITY;
        }

        // Setting of the Aggressive OutputStream only if the first ImageWriterSpi object is an instance of the Default PNGImageWriterSpi
        this.isAggressiveSupported = (!this.disablePNG);
    }

    public boolean isAggressiveOutputStreamSupported() {
        // If Default PNG Writer must not be used, then Aggressive OutputStream is not supported.
        return super.isAggressiveOutputStreamSupported() && isAggressiveSupported;
    }

    public void encode(RenderedImage image, Object destination,
            boolean aggressiveOutputStreamOptimization, MimeType type, Map<String, ?> map) throws Exception {

        if (!isAggressiveOutputStreamSupported() && aggressiveOutputStreamOptimization) {
            throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
        }

        // If the new PNGWriter must be disabled then the other writers are used
        if (disablePNG) {
            super.encode(image, destination, aggressiveOutputStreamOptimization, type, map);
        } else {
            // Creation of the associated Writer
            PNGWriter writer = new PNGWriter();
            OutputStream stream = null;
            try {
                // writer = new PNGJWriter();
                // Check if the input object is an OutputStream
                if (destination instanceof OutputStream) {
                    boolean isScanlinePresent = writer.isScanlineSupported(image);
                    if (!isScanlinePresent) {
                        image = new ImageWorker(image).rescaleToBytes().forceComponentColorModel()
                                .getRenderedImage();
                    }
                    Object filterObj = null;
                    if (map != null) {
                        filterObj = map.get(FILTER_TYPE);
                    }
                    FilterType filter = null;
                    if (filterObj == null || !(filterObj instanceof FilterType)) {
                        filter = FilterType.FILTER_NONE;
                    } else {
                        filter = (FilterType) filterObj;
                    }
                    stream = (OutputStream) destination;
                    
                    //Image preparation if an image helper is present
                    WriteHelper helper = getHelper();
                    RenderedImage finalImage = image;
                    if(helper!=null){
                        finalImage = helper.prepareImage(image, type);
                    }                   
                    // Image writing
                    writer.writePNG(finalImage, stream, quality, filter);
                } else {
                    throw new IllegalArgumentException(
                            "Only an OutputStream can be provided to the PNGEncoder");
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            } finally {
                // Writer disposal
                if (writer != null) {
                    writer = null;
                }
                // Stream closure
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    stream = null;
                }
            }
        }
    }
    /**
     * Boolean indicating if the new PNG encoder is disabled
     */
    public boolean isDisablePNG() {
        return disablePNG;
    }

    /**
     * Disable/Enable PNG encoding
     */
    public void setDisablePNG(boolean disablePNG) {
        this.disablePNG = disablePNG;
    }

}
