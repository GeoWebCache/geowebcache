/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nicola Lagomarsini, GeoSolutions S.A.S., Copyright 2014
 */
package org.geowebcache.io.codec;

import com.sun.media.imageioimpl.plugins.clib.CLibImageWriter;
import it.geosolutions.imageio.stream.output.ImageOutputStreamAdapter;
import it.geosolutions.jaiext.colorindexer.ColorIndexer;
import it.geosolutions.jaiext.colorindexer.Quantizer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.geotools.image.ImageWorker;
import org.geotools.image.ImageWorker.PNGImageWriteParam;
import org.geotools.util.logging.Logging;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;

/**
 * Class implementing the ImageEncoder interface, the user should only create a new bean for
 * instantiating a new encoder object.
 */
public class ImageEncoderImpl implements ImageEncoder {

    /** Logger used */
    private static final Logger LOGGER = Logging.getLogger(ImageEncoderImpl.class.getName());

    /** Default string used for exceptions */
    public static final String OPERATION_NOT_SUPPORTED = "Operation not supported";

    /** Boolean indicating is aggressive outputstream is supported */
    private final boolean isAggressiveOutputStreamSupported;
    /** Supported Mimetypes */
    private final List<String> supportedMimeTypes;
    /** ImageReaderSpi object used */
    private ImageWriterSpi spi;
    /** Map containing the input parameters used by the WriteHelper object */
    private Map<String, String> inputParams;
    /** Helper object used for preparing Image and ImageWriteParam for writing the image */
    private WriteHelper helper;

    /**
     * This enum is used for preparing the image to write (prepareImage()) and the related
     * ImageWriteParam(prepareParams()).
     */
    public enum WriteHelper {
        PNG(
                "image/png",
                "image/png8",
                "image/png; mode=8bit",
                "image/png24",
                "image/png; mode=24bit",
                "image/png;%20mode=24bit") {
            @Override
            public ImageWriteParam prepareParameters(
                    ImageWriter writer,
                    String compression,
                    boolean compressUsed,
                    float compressionRate) {
                ImageWriteParam params = null;

                if (writer instanceof CLibImageWriter) {
                    params = writer.getDefaultWriteParam();
                    // Define compression mode
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    if (compressUsed) {
                        // best compression
                        params.setCompressionType(compression);
                    }
                    if (compressionRate > -1) {
                        // we can control quality here
                        params.setCompressionQuality(compressionRate);
                    }
                    // Use class name without import for JDK 11 compatibility
                } else if ("com.sun.imageio.plugins.png.PNGImageWriter"
                        .equals(writer.getClass().getName())) {
                    params = new PNGImageWriteParam();
                    // Define compression mode
                    params.setCompressionMode(ImageWriteParam.MODE_DEFAULT);
                }
                return params;
            }

            @Override
            public RenderedImage prepareImage(RenderedImage image, MimeType type) {
                boolean isPNG8 = type == ImageMime.png8;
                if (isPNG8) {
                    return applyPalette(image);
                }
                return image;
            }
        },
        JPEG("image/jpeg") {
            @Override
            protected ImageWriteParam prepareParameters(
                    ImageWriter writer,
                    String compression,
                    boolean compressUsed,
                    float compressionRate) {
                ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if (compressUsed) {
                    // Lossy compression.
                    params.setCompressionType(compression);
                }
                if (compressionRate > -1) {
                    // we can control quality here
                    params.setCompressionQuality(compressionRate);
                }
                // If JPEGWriteParams, additional parameters are set
                if (params instanceof JPEGImageWriteParam) {
                    final JPEGImageWriteParam jpegParams = (JPEGImageWriteParam) params;
                    jpegParams.setOptimizeHuffmanTables(true);
                    try {
                        jpegParams.setProgressiveMode(JPEGImageWriteParam.MODE_DEFAULT);
                    } catch (UnsupportedOperationException e) {
                        // Logged Exception
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    }

                    params = jpegParams;
                }
                return params;
            }
        },
        GIF("image/gif") {
            @Override
            public RenderedImage prepareImage(RenderedImage image, MimeType type) {
                return applyPalette(image);
            }
        },
        TIFF("image/tiff"),
        BMP("image/bmp");

        private String[] formatNames;

        WriteHelper(String... formatNames) {
            this.formatNames = formatNames;
        }

        public ImageWriteParam prepareParams(Map<String, String> inputParams, ImageWriter writer) {
            // Selection of the compression type
            String compression = inputParams.get("COMPRESSION");
            // Boolean indicating if compression is present
            boolean compressUsed =
                    compression != null
                            && !compression.isEmpty()
                            && !compression.equalsIgnoreCase("null");
            // Selection of the compression rate
            String compressionRateValue = inputParams.get("COMPRESSION_RATE");
            // Initial value for the compression rate
            float compressionRate = -1;
            // Evaluation of the compression rate
            if (compressionRateValue != null) {
                try {
                    compressionRate = Float.parseFloat(compressionRateValue);
                } catch (NumberFormatException e) {
                    // Do nothing and skip compression rate
                }
            }
            // Creation of the ImageWriteParams
            ImageWriteParam params =
                    prepareParameters(writer, compression, compressUsed, compressionRate);

            return params;
        }

        protected ImageWriteParam prepareParameters(
                ImageWriter writer,
                String compression,
                boolean compressUsed,
                float compressionRate) {
            // Parameters creation
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            if (compressUsed) {
                // best compression
                params.setCompressionType(compression);
            }
            if (compressionRate > -1) {
                // we can control quality here
                params.setCompressionQuality(compressionRate);
            }

            return params;
        }

        public RenderedImage prepareImage(RenderedImage image, MimeType type) {
            return image;
        }

        private boolean isFormatNameAccepted(String formatName) {
            boolean accepted = false;
            for (String format : formatNames) {
                accepted = format.equalsIgnoreCase(formatName);
                if (accepted) {
                    break;
                }
            }
            return accepted;
        }

        public static WriteHelper getWriteHelperForName(String formatName) {

            if (PNG.isFormatNameAccepted(formatName)) {
                return PNG;
            } else if (JPEG.isFormatNameAccepted(formatName)) {
                return JPEG;
            } else if (GIF.isFormatNameAccepted(formatName)) {
                return GIF;
            } else if (TIFF.isFormatNameAccepted(formatName)) {
                return TIFF;
            } else if (BMP.isFormatNameAccepted(formatName)) {
                return BMP;
            }
            return null;
        }
    }

    /**
     * Encodes the selected image with the defined output object. The user can set the aggressive
     * outputStream if supported.
     *
     * @param image Image to write.
     * @param destination Destination object where the image is written.
     * @param aggressiveOutputStreamOptimization Parameter used if aggressive outputStream
     *     optimization must be used.
     */
    @Override
    public void encode(
            RenderedImage image,
            Object destination,
            boolean aggressiveOutputStreamOptimization,
            MimeType type,
            Map<String, ?> map)
            throws Exception {

        if (!isAggressiveOutputStreamSupported() && aggressiveOutputStreamOptimization) {
            throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
        }

        // Selection of the first priority writerSpi
        ImageWriterSpi newSpi = getWriterSpi();

        if (newSpi != null) {
            // Creation of the associated Writer
            ImageWriter writer = null;
            ImageOutputStream stream = null;
            try { // NOPMD (complex instantiation of the image stream
                writer = newSpi.createWriterInstance();
                // Check if the input object is an OutputStream
                if (destination instanceof OutputStream) {
                    // Use of the ImageOutputStreamAdapter
                    if (isAggressiveOutputStreamSupported()) {
                        stream = new ImageOutputStreamAdapter((OutputStream) destination);
                    } else {
                        stream = new MemoryCacheImageOutputStream((OutputStream) destination);
                    }

                    // Preparation of the ImageWriteParams
                    ImageWriteParam params = null;
                    RenderedImage finalImage = image;
                    if (helper != null) {
                        params = helper.prepareParams(inputParams, writer);
                        finalImage = helper.prepareImage(image, type);
                    }

                    // Image writing
                    writer.setOutput(stream);
                    writer.write(null, new IIOImage(finalImage, null, null), params);
                } else {
                    throw new IllegalArgumentException("Wrong output object");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw e;
            } finally {
                // Writer disposal
                if (writer != null) {
                    writer.dispose();
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
    }

    /** Returns the ImageSpiWriter associated to */
    ImageWriterSpi getWriterSpi() {
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
     * Indicates if optimization on OutputStream can be used
     *
     * @return isAggressiveOutputStreamSupported Boolean indicating if the selected encoder supports
     *     an aggressive output stream optimization
     */
    @Override
    public boolean isAggressiveOutputStreamSupported() {
        return isAggressiveOutputStreamSupported;
    }

    /**
     * Creates a new Instance of ImageEncoder supporting or not OutputStream optimization, with the
     * defined MimeTypes and Spi classes.
     */
    public ImageEncoderImpl(
            boolean aggressiveOutputStreamOptimization,
            List<String> supportedMimeTypes,
            List<String> writerSpi,
            Map<String, String> inputParams,
            ImageIOInitializer initializer) {
        this.isAggressiveOutputStreamSupported = aggressiveOutputStreamOptimization;
        this.supportedMimeTypes = new ArrayList<>(supportedMimeTypes);
        this.inputParams = inputParams;
        // Get the IIORegistry if needed
        IIORegistry theRegistry = initializer.getRegistry();
        // Checks for each Spi class if it is present and then it is added to the list.
        for (String spi : writerSpi) {
            try {

                Class<?> clazz = Class.forName(spi);
                ImageWriterSpi writer =
                        (ImageWriterSpi) theRegistry.getServiceProviderByClass(clazz);
                if (writer != null) {
                    this.spi = writer;
                    break;
                }
            } catch (ClassNotFoundException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
        }

        // Selection of the helper object associated to the following format
        helper = WriteHelper.getWriteHelperForName(supportedMimeTypes.get(0));
    }

    /** Returns the WriteHelper object used */
    protected WriteHelper getHelper() {
        return helper;
    }

    private static RenderedImage applyPalette(RenderedImage canvas) {
        if (!(canvas.getColorModel() instanceof IndexColorModel)) {
            // try to force a RGBA setup
            ImageWorker imageWorker = new ImageWorker(canvas);
            RenderedImage image =
                    imageWorker.rescaleToBytes().forceComponentColorModel().getRenderedImage();
            ColorIndexer indexer = new Quantizer(256).subsample().buildColorIndexer(image);

            // if we have an indexer transform the image
            if (indexer != null) {
                image = new ImageWorker(image).colorIndex(indexer).getRenderedImage();
            }
            return image;
        }
        return canvas;
    }
}
