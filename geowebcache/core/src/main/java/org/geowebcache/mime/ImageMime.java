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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.mime;

import it.geosolutions.jaiext.JAIExt;
import it.geosolutions.jaiext.colorindexer.ColorIndexer;
import it.geosolutions.jaiext.colorindexer.Quantizer;
import java.awt.RenderingHints;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageWriter;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ExtremaDescriptor;
import org.geotools.util.logging.Logging;

public class ImageMime extends MimeType {

    public static final String NATIVE_PNG_WRITER_CLASS_NAME =
            "com.sun.media.imageioimpl.plugins.png.CLibPNGImageWriter";

    private static Logger log = Logging.getLogger(ImageMime.class.getName());

    boolean supportsAlphaChannel;

    boolean supportsAlphaBit;

    static {
        // register the custom JAIExt operations, without forcing replacement of JAI own
        JAIExt.initJAIEXT(false, false);
    }

    public static final ImageMime png =
            new ImageMime("image/png", "png", "png", "image/png", true, true, true) {

                /** Any response mime starting with image/png will do */
                @Override
                public boolean isCompatible(String otherMimeType) {
                    return super.isCompatible(otherMimeType)
                            || otherMimeType.startsWith("image/png");
                };
            };

    public static final ImageMime jpeg =
            new ImageMime("image/jpeg", "jpeg", "jpeg", "image/jpeg", true, false, false) {

                /** Shave off the alpha band, JPEG cannot write it out */
                @Override
                public RenderedImage preprocess(RenderedImage ri) {
                    if (ri.getColorModel().hasAlpha()) {
                        final int numBands = ri.getSampleModel().getNumBands();
                        // handle both gray-alpha and RGBA (same code as in GeoTools ImageWorker)
                        final int[] bands = new int[numBands - 1];
                        for (int i = 0; i < bands.length; i++) {
                            bands[i] = i;
                        }
                        // ParameterBlock creation
                        ParameterBlock pb = new ParameterBlock();
                        pb.setSource(ri, 0);
                        pb.set(bands, 0);
                        final RenderingHints hints =
                                new RenderingHints(JAI.KEY_IMAGE_LAYOUT, new ImageLayout(ri));
                        ri = JAI.create("BandSelect", pb, hints);
                    }
                    return ri;
                }
            };

    public static final ImageMime gif =
            new ImageMime("image/gif", "gif", "gif", "image/gif", true, false, true);

    public static final ImageMime tiff =
            new ImageMime("image/tiff", "tiff", "tiff", "image/tiff", true, true, true);

    public static final ImageMime png8 =
            new ImageMime("image/png", "png8", "png", "image/png8", true, false, true) {

                /** Quantize if the source did not do so already */
                @Override
                public RenderedImage preprocess(RenderedImage canvas) {
                    if (!(canvas.getColorModel() instanceof IndexColorModel)) {
                        if (canvas.getColorModel() instanceof ComponentColorModel
                                && canvas.getSampleModel().getDataType() == DataBuffer.TYPE_BYTE) {
                            ColorIndexer indexer =
                                    new Quantizer(256).subsample().buildColorIndexer(canvas);
                            if (indexer != null) {
                                ParameterBlock pb = new ParameterBlock();
                                pb.setSource(canvas, 0); // The source image.
                                pb.set(indexer, 0);
                                canvas =
                                        JAI.create(
                                                "ColorIndexer",
                                                pb,
                                                JAI.getDefaultInstance().getRenderingHints());
                            }
                        }
                    }
                    return canvas;
                }
            };

    public static final ImageMime png24 =
            new ImageMime("image/png", "png24", "png", "image/png24", true, true, true);

    public static final ImageMime png_24 =
            new ImageMime(
                    "image/png; mode=24bit",
                    "png_24",
                    "png",
                    "image/png;%20mode=24bit",
                    true,
                    true,
                    true);

    public static final ImageMime dds =
            new ImageMime("image/dds", "dds", "dds", "image/dds", false, false, false);

    public static final ImageMime jpegPng =
            new JpegPngMime(
                    "image/vnd.jpeg-png", "jpeg-png", "jpeg-png", "image/vnd.jpeg-png", jpeg, png);

    public static final ImageMime jpegPng8 =
            new JpegPngMime(
                    "image/vnd.jpeg-png8",
                    "jpeg-png8",
                    "jpeg-png8",
                    "image/vnd.jpeg-png8",
                    jpeg,
                    png8);

    private ImageMime(
            String mimeType,
            String fileExtension,
            String internalName,
            String format,
            boolean tiled,
            boolean alphaChannel,
            boolean alphaBit) {
        super(mimeType, fileExtension, internalName, format, tiled);

        this.supportsAlphaChannel = alphaChannel;
        this.supportsAlphaBit = alphaBit;
    }

    protected static ImageMime checkForFormat(String formatStr) throws MimeException {
        if (!formatStr.startsWith("image/")) {
            return null;
        }

        // TODO Making a special exception, generalize later
        if (!formatStr.equals("image/png; mode=24bit") && formatStr.contains(";")) {
            if (log.isLoggable(Level.FINE)) {
                log.fine("Slicing off " + formatStr.split(";")[1]);
            }
            formatStr = formatStr.split(";")[0];
        }

        final String tmpStr = formatStr.substring(6);
        if (tmpStr.equalsIgnoreCase("png")) {
            return png;
        } else if (tmpStr.equalsIgnoreCase("jpeg")) {
            return jpeg;
        } else if (tmpStr.equalsIgnoreCase("gif")) {
            return gif;
        } else if (tmpStr.equalsIgnoreCase("tiff")) {
            return tiff;
        } else if (tmpStr.equalsIgnoreCase("png8")) {
            return png8;
        } else if (tmpStr.equalsIgnoreCase("png24")) {
            return png24;
        } else if (tmpStr.equalsIgnoreCase("png; mode=24bit")) {
            return png_24;
        } else if (tmpStr.equalsIgnoreCase("png;%20mode=24bit")) {
            return png_24;
        } else if (tmpStr.equalsIgnoreCase("vnd.jpeg-png")) {
            return jpegPng;
        } else if (tmpStr.equalsIgnoreCase("vnd.jpeg-png8")) {
            return jpegPng8;
        }
        return null;
    }

    protected static ImageMime checkForExtension(String fileExtension) throws MimeException {
        if (fileExtension.equalsIgnoreCase("png")) {
            return png;
        } else if (fileExtension.equalsIgnoreCase("jpeg")
                || fileExtension.equalsIgnoreCase("jpg")) {
            return jpeg;
        } else if (fileExtension.equalsIgnoreCase("gif")) {
            return gif;
        } else if (fileExtension.equalsIgnoreCase("tiff")) {
            return tiff;
        } else if (fileExtension.equalsIgnoreCase("png8")) {
            return png8;
        } else if (fileExtension.equalsIgnoreCase("png24")) {
            return png24;
        } else if (fileExtension.equalsIgnoreCase("png_24")) {
            return png_24;
        } else if (fileExtension.equalsIgnoreCase("jpeg-png")) {
            return jpegPng;
        } else if (fileExtension.equalsIgnoreCase("jpeg-png8")) {
            return jpegPng8;
        }
        return null;
    }

    public boolean supportsAlphaBit() {
        return supportsAlphaBit;
    }

    public boolean supportsAlphaChannel() {
        return supportsAlphaChannel;
    }

    @Override
    protected boolean isBinary() {
        return true;
    }

    public ImageWriter getImageWriter(RenderedImage image) {
        Iterator<ImageWriter> it = javax.imageio.ImageIO.getImageWritersByFormatName(internalName);
        ImageWriter writer = it.next();

        // Native PNG Writer can't handle 2-4 bit PNG, so if our sample depth isn't 1/8 and the
        // returned writer is the native version, let's skip it and move on to the next
        // which will presumably be the pure Java version. A bit hacky, but it's roughly what
        // GeoServer does to make sure it doesn't encode incompatible PNGs with the native writer
        if (this.internalName.equals(ImageMime.png.internalName)
                || this.internalName.equals(ImageMime.png8.internalName)) {

            int bitDepth = image.getSampleModel().getSampleSize(0);
            if (bitDepth > 1
                    && bitDepth < 8
                    && writer.getClass().getName().equals(NATIVE_PNG_WRITER_CLASS_NAME)) {

                writer = it.next();
            }
        }
        return writer;
    }

    /** Preprocesses the image to optimize it for the write about to happen */
    public RenderedImage preprocess(RenderedImage tile) {
        return tile;
    }

    private static class JpegPngMime extends ImageMime {

        private static final int JPEG_MAGIC_MASK = 0xffd80000;
        private final ImageMime jpegDelegate;
        private final ImageMime pngDelegate;

        public JpegPngMime(
                String mimeType,
                String fileExtension,
                String internalName,
                String format,
                ImageMime jpegDelegate,
                ImageMime pngDelegate) {
            super(mimeType, fileExtension, internalName, format, true, true, true);
            this.jpegDelegate = jpegDelegate;
            this.pngDelegate = pngDelegate;
        }

        /**
         * Returns true if the best format to encode the image is jpeg (the image is rgb, or rgba
         * without any actual transparency use). This code is duplicated in GeoServer
         * JpegPngRenderedImageMapOutputFormat. Unfortunately gwc-core does not depend on GeoTools,
         * so we don't have an easy place to share it. On the bright side, it's small.
         */
        boolean isBestFormatJpeg(RenderedImage renderedImage) {
            int numBands = renderedImage.getSampleModel().getNumBands();
            if (numBands == 4 || numBands == 2) {
                RenderedOp extremaOp =
                        ExtremaDescriptor.create(
                                renderedImage,
                                null,
                                1,
                                1,
                                false,
                                1,
                                JAI.getDefaultInstance().getRenderingHints());
                double[][] extrema = (double[][]) extremaOp.getProperty("Extrema");
                double[] mins = extrema[0];

                return mins[mins.length - 1] == 255; // fully opaque
            } else if (renderedImage.getColorModel() instanceof IndexColorModel) {
                // JPEG would still compress a bit better, but in order to figure out
                // if the image has transparency we'd have to expand to RGB or roll
                // a new JAI image op that looks for the transparent pixels. Out of scope
                // for the moment
                return false;
            } else {
                // otherwise support RGB or gray
                return (numBands == 3) || (numBands == 1);
            }
        }

        @Override
        public ImageWriter getImageWriter(RenderedImage image) {
            if (isBestFormatJpeg(image)) {
                return jpegDelegate.getImageWriter(image);
            } else {
                return pngDelegate.getImageWriter(image);
            }
        }

        @Override
        public String getMimeType(org.geowebcache.io.Resource resource) throws IOException {
            try (DataInputStream dis = new DataInputStream(resource.getInputStream())) {
                final int head = dis.readInt();
                if ((head & 0xFFFF0000) == JPEG_MAGIC_MASK) {
                    return jpegDelegate.getMimeType();
                } else {
                    return pngDelegate.getMimeType();
                }
            }
        };

        @Override
        public boolean isCompatible(String otherMimeType) {
            return jpegDelegate.isCompatible(otherMimeType)
                    || pngDelegate.isCompatible(otherMimeType);
        }

        @Override
        public RenderedImage preprocess(RenderedImage tile) {
            if (isBestFormatJpeg(tile)) {
                return jpegDelegate.preprocess(tile);
            } else {
                return pngDelegate.preprocess(tile);
            }
        }
    }
}
