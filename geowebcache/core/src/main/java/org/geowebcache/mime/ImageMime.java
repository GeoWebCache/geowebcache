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
package org.geowebcache.mime;

import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageWriter;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ExtremaDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ImageMime extends MimeType {

    public static final String NATIVE_PNG_WRITER_CLASS_NAME = "com.sun.media.imageioimpl.plugins.png.CLibPNGImageWriter";

    private static Log log = LogFactory.getLog(org.geowebcache.mime.ImageMime.class);
    
    boolean supportsAlphaChannel;
    
    boolean supportsAlphaBit;

    public static final ImageMime png = 
        new ImageMime("image/png", "png", "png", "image/png", true, true, true) {
        
        /**
         * Any response mime starting with image/png will do
         */
        public boolean isCompatible(String otherMimeType) {
            return super.isCompatible(otherMimeType) || otherMimeType.startsWith("image/png");
        };
    };

    public static final ImageMime jpeg = 
        new ImageMime("image/jpeg", "jpeg", "jpeg", "image/jpeg", true, false, false);
    
    public static final ImageMime gif = 
        new ImageMime("image/gif", "gif", "gif", "image/gif", true, false, true);    
    
    public static final ImageMime tiff = 
            new ImageMime("image/tiff", "tiff", "tiff", "image/tiff", true, true, true);
    
    public static final ImageMime png8 = 
        new ImageMime("image/png", "png8", "png", "image/png8", true, false, true);
    
    public static final ImageMime png24 = 
        new ImageMime("image/png", "png24", "png", "image/png24", true, true, true);

    public static final ImageMime png_24 = 
        new ImageMime("image/png; mode=24bit", "png_24", "png", "image/png;%20mode=24bit", true, true, true);
    
    public static final ImageMime dds = 
        new ImageMime("image/dds", "dds", "dds", "image/dds", false, false, false);
    
    public static final ImageMime jpegPng = 
        new ImageMime("image/vnd.jpeg-png", "jpeg-png", "jpeg-png", "image/vnd.jpeg-png", true, true, true) {
        
        private static final int JPEG_MAGIC_MASK = 0xffd80000;
        
        /**
         * Returns true if the best format to encode the image is jpeg (the image is rgb, or rgba without any actual
         * transparency use). This code is duplicated in GeoServer JpegPngRenderedImageMapOutputFormat. Unfortunately
         * gwc-core does not depend on GeoTools, so we don't have an easy place to share it. On the bright side, it's small.
         *
         * @param renderedImage
         * @return
         */
        boolean isBestFormatJpeg(RenderedImage renderedImage)
        {
            int numBands = renderedImage.getSampleModel().getNumBands();
            if (numBands == 4 || numBands == 2)
            {
                RenderedOp extremaOp = ExtremaDescriptor.create(renderedImage, null, 1, 1, false, 1, JAI.getDefaultInstance().getRenderingHints());
                double[][] extrema = (double[][]) extremaOp.getProperty("Extrema");
                double[] mins = extrema[0];
        
                return mins[mins.length - 1] == 255; // fully opaque
            } else if(renderedImage.getColorModel() instanceof IndexColorModel) {
                // JPEG would still compress a bit better, but in order to figure out
                // if the image has transparency we'd have to expand to RGB or roll
                // a new JAI image op that looks for the transparent pixels. Out of scope for the moment
                return false;
            } else {
                // otherwise support RGB or gray
                return (numBands == 3) || (numBands == 1);
            }
        }
        
        public ImageWriter getImageWriter(RenderedImage image) {
            if(isBestFormatJpeg(image)) {
                return jpeg.getImageWriter(image);
            } else {
                return png.getImageWriter(image);
            }
        }
        
        public String getMimeType(org.geowebcache.io.Resource resource) throws IOException {
            try(DataInputStream dis = new DataInputStream(resource.getInputStream()))
            {
                final int head = dis.readInt();
                if((head & 0xFFFF0000) == JPEG_MAGIC_MASK) {
                    return jpeg.getMimeType();
                } else {
                    return png.getMimeType();
                }
            }
        };
        
        @Override
            public boolean isCompatible(String otherMimeType) {
                return jpeg.isCompatible(otherMimeType) || png.isCompatible(otherMimeType);
            }
        
    };
    
    private ImageMime(String mimeType, String fileExtension, 
            String internalName, String format, boolean tiled,
            boolean alphaChannel, boolean alphaBit) {
        super(mimeType, fileExtension, internalName, format, tiled);
        
        this.supportsAlphaChannel = alphaChannel;
        this.supportsAlphaBit = alphaBit;
    }

    protected static ImageMime checkForFormat(String formatStr) throws MimeException {
        if (!formatStr.startsWith("image/")) {
            return null;
        }
        final String tmpStr = formatStr.substring(6, formatStr.length());

        // TODO Making a special exception, generalize later
        if (!formatStr.equals("image/png; mode=24bit") && formatStr.contains(";")) {
            if (log.isDebugEnabled()) {
                log.debug("Slicing off " + formatStr.split(";")[1]);
            }
            formatStr = formatStr.split(";")[0];
        }
 
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
        } else if(tmpStr.equalsIgnoreCase("vnd.jpeg-png")) {
            return jpegPng;
        }
        return null;
    }

    protected static ImageMime checkForExtension(String fileExtension) 
    throws MimeException {
        if (fileExtension.equalsIgnoreCase("png")) {
            return png;
        } else if (fileExtension.equalsIgnoreCase("jpeg") || fileExtension.equalsIgnoreCase("jpg")) {
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
        }
        return null;
    }
    
    public boolean supportsAlphaBit() {
       return supportsAlphaBit; 
    }
    
    public boolean supportsAlphaChannel() {
        return supportsAlphaChannel;
    }
    
    public ImageWriter getImageWriter(RenderedImage image) {
        Iterator<ImageWriter> it = javax.imageio.ImageIO.getImageWritersByFormatName(internalName);
        ImageWriter writer = it.next();

        //Native PNG Writer can't handle 2-4 bit PNG, so if our sample depth isn't 1/8 and the
        //returned writer is the native version, let's skip it and move on to the next
        //which will presumably be the pure Java version. A bit hacky, but it's roughly what
        //GeoServer does to make sure it doesn't encode incompatible PNGs with the native writer
        if (this.internalName.equals(ImageMime.png.internalName)
            || this.internalName.equals(ImageMime.png8.internalName)) {

            int bitDepth = image.getSampleModel().getSampleSize(0);
            if (bitDepth > 1 && bitDepth < 8
                && writer.getClass().getName().equals(NATIVE_PNG_WRITER_CLASS_NAME)) {

                writer = it.next();
            }
        }
        return writer;
    }

}
