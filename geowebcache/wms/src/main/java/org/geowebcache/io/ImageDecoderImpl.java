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

import it.geosolutions.imageio.stream.input.FileImageInputStreamExtImpl;
import it.geosolutions.imageio.stream.input.ImageInputStreamAdapter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.apache.log4j.Logger;

/**
 * Class implementing the ImageDecoder interface, the user should only create a new bean for instantiating a new decoder object.
 */
public class ImageDecoderImpl implements ImageDecoder{
    /**
     * Logger used
     */
    private static final Logger LOGGER = Logger.getLogger(ImageEncoderImpl.class);

    /**
     * Default string used for exceptions
     */
    public static final String OPERATION_NOT_SUPPORTED = "Operation not supported";

    /**Boolean indicating is aggressive inputstream is supported*/
    private final boolean isAggressiveInputStreamSupported;
    /**Supported Mimetypes*/
    private final List<String> supportedMimeTypes;
    /**ImageReaderSpi object used*/
    private ImageReaderSpi spi;

    /**
     * Creates a new Instance of ImageEncoder supporting or not OutputStream optimization, with the defined MimeTypes and Spi classes.
     * 
     * @param aggressiveOutputStreamOptimization
     * @param supportedMimeTypes
     * @param writerSpi
     */
    public ImageDecoderImpl(boolean aggressiveInputStreamOptimization,
            List<String> supportedMimeTypes, List<String> readerSpi, ImageIOInitializer initializer) {

        this.isAggressiveInputStreamSupported = aggressiveInputStreamOptimization;
        this.supportedMimeTypes = new ArrayList<String>(supportedMimeTypes);
        // Get the IIORegistry if needed
        IIORegistry theRegistry = initializer.getRegistry();
        // Checks for each Spi class if it is present and then it is added to the list.
        for (String spi : readerSpi) {
            try {
                Class<?> clazz = Class.forName(spi);
                ImageReaderSpi reader = (ImageReaderSpi) theRegistry
                        .getServiceProviderByClass(clazz);
                if (reader != null) {
                    this.spi=reader;
                    break;
                }
            } catch (ClassNotFoundException e) {
                LOGGER.error(e.getMessage(), e);
            }

        }
    }
    
    
    /**
     * Encodes the selected image with the defined output object. The user can set the aggressive outputStream if supported.
     * 
     * @param image Image to write.
     * @param source Destination object where the image is written.
     * @param aggressiveOutputStreamOptimization Parameter used if aggressive outputStream optimization must be used.
     * @throws IOException
     */
    public BufferedImage decode(Object source,
            boolean aggressiveInputStreamOptimization, Map<String,Object> map)  throws Exception{

        if (!isAggressiveInputStreamSupported() && aggressiveInputStreamOptimization) {
            throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
        }

        // Selection of the first priority writerSpi
        ImageReaderSpi newSpi = getReaderSpi();
        
        if(newSpi!=null){
            // Creation of the associated Writer
            ImageReader reader = null;
            ImageInputStream stream = null;
            try {
                reader = newSpi.createReaderInstance();
                if (source instanceof FileResource) {
                    // file
                    stream= new FileImageInputStreamExtImpl(((FileResource)source).getFile());
                    // Image reading
                    reader.setInput(stream);
                    return reader.read(0);                    
                } else {
                    // create a stream and move on
                    source=((Resource)source).getInputStream();
                }
                
                // Check if the input object is an InputStream
                if (source instanceof InputStream) {
                    // Use of the ImageInputStreamAdapter
                    if (isAggressiveInputStreamSupported()) {
                        stream = new ImageInputStreamAdapter((InputStream) source);
                    } else {
                        stream = new MemoryCacheImageInputStream((InputStream) source);
                    }

                    // Image reading
                    reader.setInput(stream);
                    return reader.read(0);
                }else{
                    throw new IllegalArgumentException("Wrong input object");
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            } finally {
                // reader disposal
                if (reader != null) {
                    reader.dispose();
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
        
        return null;
    }

    /**
     * Returns the ImageSpiReader associated to 
     * @return
     */
    ImageReaderSpi getReaderSpi() {
        return spi;
    }

    /**
     * Returns all the supported MimeTypes
     * 
     * @return supportedMimeTypes List of all the supported Mime Types
     */
    public List<String> getSupportedMimeTypes() {
        return supportedMimeTypes;
    }

    /**
     * Indicates if optimization on InputStream can be used
     * 
     * @return isAggressiveInputStreamSupported Boolean indicating if the selected decoder supports an aggressive input stream optimization
     */
    public boolean isAggressiveInputStreamSupported() {
        return isAggressiveInputStreamSupported;
    }

}
