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
 * @author Nicola Lagomarsini, GeoSolutions S.A.S., Copyright 2014
 */
package org.geowebcache.io.codec;

import it.geosolutions.imageio.stream.input.FileImageInputStreamExtImpl;
import it.geosolutions.imageio.stream.input.ImageInputStreamAdapter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;

/**
 * Class implementing the ImageDecoder interface, the user should only create a new bean for instantiating a new decoder
 * object.
 */
public class ImageDecoderImpl implements ImageDecoder {
    /** Logger used */
    private static final Logger LOGGER = Logging.getLogger(ImageEncoderImpl.class.getName());

    /** Default string used for exceptions */
    public static final String OPERATION_NOT_SUPPORTED = "Operation not supported";

    /** Boolean indicating is aggressive inputstream is supported */
    private final boolean isAggressiveInputStreamSupported;
    /** Supported Mimetypes */
    private final List<String> supportedMimeTypes;
    /** ImageReaderSpi object used */
    private ImageReaderSpi spi;

    /**
     * Creates a new Instance of ImageEncoder supporting or not OutputStream optimization, with the defined MimeTypes.
     */
    public ImageDecoderImpl(boolean aggressiveInputStreamOptimization, List<String> supportedMimeTypes) {
        this(aggressiveInputStreamOptimization, supportedMimeTypes, null);
    }

    /**
     * Creates a new Instance of ImageEncoder supporting or not OutputStream optimization, with the defined MimeTypes
     * and Spi classes.
     */
    public ImageDecoderImpl(
            boolean aggressiveInputStreamOptimization, List<String> supportedMimeTypes, String preferredSpi) {
        this.isAggressiveInputStreamSupported = aggressiveInputStreamOptimization;
        this.supportedMimeTypes = new ArrayList<>(supportedMimeTypes);
        // Get the IIORegistry if needed
        // Looks up an SPI for each supported MimeType, without breaking the JDK package sealing
        ImageReaderSpi backupSPI = null;
        for (String mimeType : supportedMimeTypes) {
            Iterator<ImageReader> reader = ImageIO.getImageReadersByMIMEType(mimeType);
            if (reader.hasNext()) {
                ImageReaderSpi readerSpi = reader.next().getOriginatingProvider();
                if (readerSpi != null) {
                    if (preferredSpi == null) {
                        this.spi = readerSpi;
                        break;
                    } else if (readerSpi.getClass().getName().equals(preferredSpi)) {
                        this.spi = readerSpi;
                        break;
                    } else if (backupSPI == null) {
                        // Keep the first available SPI as a backup
                        backupSPI = readerSpi;
                    }
                }
            }
        }
        if (this.spi == null) {
            if (backupSPI == null) {
                throw new IllegalArgumentException(
                        "No ImageReaderSpi found for the selected mimetypes: " + supportedMimeTypes);
            } else {
                LOGGER.log(
                        Level.WARNING,
                        "Preferred SPI not found, using the first available one: "
                                + backupSPI.getClass().getName());
                this.spi = backupSPI;
            }
        }
    }

    /**
     * Decodes the selected image with the defined output object. The user can set the aggressive outputStream if
     * supported.
     *
     * @param source Source object to read
     * @param aggressiveInputStreamOptimization Parameter used if aggressive outputStream optimization must be used.
     */
    @Override
    @SuppressWarnings("PMD.CloseResource") // the caller is in charge of source's life cycle if its a stream
    public BufferedImage decode(Object source, boolean aggressiveInputStreamOptimization, Map<String, Object> map)
            throws Exception {

        if (!isAggressiveInputStreamSupported() && aggressiveInputStreamOptimization) {
            throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
        }

        // Selection of the first priority writerSpi
        ImageReaderSpi newSpi = getReaderSpi();

        if (newSpi != null) {
            // Creation of the associated Writer
            ImageReader reader = null;
            ImageInputStream stream = null;
            try { // NOPMD (handling of stream is complicated)
                reader = newSpi.createReaderInstance();
                if (source instanceof FileResource resource) {
                    // file
                    stream = new FileImageInputStreamExtImpl(resource.getFile());
                    // Image reading
                    reader.setInput(stream);
                    return reader.read(0);
                } else {
                    // create a stream and move on
                    source = ((Resource) source).getInputStream();
                }

                // Check if the input object is an InputStream
                if (source instanceof InputStream inputStream) {
                    // Use of the ImageInputStreamAdapter
                    if (isAggressiveInputStreamSupported()) {
                        stream = new ImageInputStreamAdapter(inputStream);
                    } else {
                        stream = new MemoryCacheImageInputStream(inputStream);
                    }

                    // Image reading
                    reader.setInput(stream);
                    return reader.read(0);
                } else {
                    throw new IllegalArgumentException("Wrong input object");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
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
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    }
                    stream = null;
                }
            }
        }

        return null;
    }

    /** Returns the ImageSpiReader associated to */
    ImageReaderSpi getReaderSpi() {
        return spi;
    }

    /**
     * Returns all the supported MimeTypes
     *
     * @return supportedMimeTypes List of all the supported Mime Types
     */
    @Override
    public List<String> getSupportedMimeTypes() {
        return supportedMimeTypes;
    }

    /**
     * Indicates if optimization on InputStream can be used
     *
     * @return isAggressiveInputStreamSupported Boolean indicating if the selected decoder supports an aggressive input
     *     stream optimization
     */
    @Override
    public boolean isAggressiveInputStreamSupported() {
        return isAggressiveInputStreamSupported;
    }
}
