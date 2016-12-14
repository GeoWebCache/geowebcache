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
 * (c) 2016 Open Source Geospatial Foundation - all rights reserved 
 */
package org.geowebcache.mime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.junit.Before;
import org.junit.Test;

public class ImageMimeTest {
    
    private BufferedImage indexed;
    private BufferedImage gray;
    private BufferedImage rgb;
    private BufferedImage rgba;
    private BufferedImage rgba_opaque;
    private BufferedImage rgba_partial;

    @Before
    public void prepareImages() {
        // paletted image
        indexed = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_INDEXED);
        // gray one, no transparency
        gray = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        // opaque rgb
        rgb = new BufferedImage(10, 10, BufferedImage.TYPE_3BYTE_BGR);
        // transparent rgba
        rgba = new BufferedImage(10, 10, BufferedImage.TYPE_4BYTE_ABGR);
        // fully opaque rgba
        rgba_opaque = new BufferedImage(10, 10, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D graphics = rgba_opaque.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, 10, 10);
        graphics.dispose();
        // partially transparent rgba
        rgba_partial = new BufferedImage(10, 10, BufferedImage.TYPE_4BYTE_ABGR);
        graphics = rgba_partial.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, 10, 5);
        graphics.dispose();
    }

    @Test
    public void testJpegPng() throws MimeException {
        assertNotNull(MimeType.createFromFormat("image/vnd.jpeg-png"));
        assertNotNull(MimeType.createFromExtension("jpeg-png"));
    }
    
    @Test
    public void testJpegPngImageWriter() {
        assertExpectedWriter(indexed, ImageMime.png);
        assertExpectedWriter(gray, ImageMime.jpeg);
        assertExpectedWriter(gray, ImageMime.jpeg);
        assertExpectedWriter(rgba, ImageMime.png);
        assertExpectedWriter(rgba_opaque, ImageMime.jpeg);
        assertExpectedWriter(rgba_partial, ImageMime.png);
    }
    
    @Test
    public void testJpegPngMime() throws IOException {
        Resource pngImage = getAsResource(indexed, ImageMime.png);
        assertEquals("image/png", ImageMime.jpegPng.getMimeType(pngImage));
        Resource jpegImage = getAsResource(rgb, ImageMime.jpeg);
        assertEquals("image/jpeg", ImageMime.jpegPng.getMimeType(jpegImage));
    }

    private Resource getAsResource(BufferedImage image, ImageMime mime) throws IOException {
        ImageWriter writer = mime.getImageWriter(image);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        final MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(os);
        writer.setOutput(ios);
        writer.write(image);
        ios.flush();
        return new ByteArrayResource(os.toByteArray());
    }

    private void assertExpectedWriter(BufferedImage bi, ImageMime expectedMime) {
        ImageWriter jpegPngWriter = ImageMime.jpegPng.getImageWriter(bi);
        ImageWriter expectedWriter = expectedMime.getImageWriter(bi);
        // compare by class, these object are not meant to be compared by equality
        assertEquals(expectedWriter.getClass(), jpegPngWriter.getClass());
    }
}