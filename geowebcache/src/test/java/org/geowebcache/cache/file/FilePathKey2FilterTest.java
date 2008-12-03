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

package org.geowebcache.cache.file;

import java.io.File;

import org.geowebcache.layer.SRS;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;

import junit.framework.TestCase;

public class FilePathKey2FilterTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testFilter() throws Exception {
        // Wipe everything...
        FilePathKey2Filter fpk2f = new FilePathKey2Filter(SRS.getEPSG4326(), -1, -1, null, null);
        
        File dir = new File("/tmp/EPSG_4326_06/000_000");
        assertTrue(fpk2f.accept(dir, "123_456.png"));
        
        // Check SRSs
        fpk2f = new FilePathKey2Filter(SRS.getEPSG900913(), -1, -1, null, null);
        assertTrue(fpk2f.accept(new File("/tmp"), "EPSG_900913_01"));
        assertFalse(fpk2f.accept(new File("/tmp"), "EPSG_4326_01"));
        
        // Check extensions
        MimeType[] mimesPng = { ImageMime.png };
        fpk2f = new FilePathKey2Filter(SRS.getEPSG4326(), -1, -1, null, mimesPng );
        assertTrue(fpk2f.accept(dir, "123_456.png"));

        // Check extensions
        MimeType[] mimesKml = { XMLMime.kml, XMLMime.kmz };
        fpk2f = new FilePathKey2Filter(SRS.getEPSG4326(), -1, -1, null, mimesKml );
        assertTrue(fpk2f.accept(dir, "123_456.png.kmz"));
        assertTrue(fpk2f.accept(dir, "123_456.kml"));
        assertTrue(fpk2f.accept(dir, "123_456.kml.kmz"));
        
        MimeType[] mimesJpeg = { ImageMime.jpeg };
        fpk2f = new FilePathKey2Filter(SRS.getEPSG4326(), -1, -1, null, mimesJpeg );
        assertFalse(fpk2f.accept(dir, "123_456.png"));
        
        // Check zoomlevel
        fpk2f = new FilePathKey2Filter(SRS.getEPSG4326(), 0, 3, null, mimesPng);
        assertTrue(fpk2f.accept(new File("/tmp"), "EPSG_4326_00"));
        assertTrue(fpk2f.accept(new File("/tmp"), "EPSG_4326_01"));
        assertTrue(fpk2f.accept(new File("/tmp"), "EPSG_4326_02"));
        assertTrue(fpk2f.accept(new File("/tmp"), "EPSG_4326_03"));
        assertFalse(fpk2f.accept(new File("/tmp"), "EPSG_4326_04"));

        // Check bounding boxes (unrealistic)
        int[][] bounds = { {0,0,0,0}, {0,0,1,1}, {5,5,6,6} };
        fpk2f = new FilePathKey2Filter(SRS.getEPSG4326(), 0, 2, bounds, mimesPng);
        assertTrue(fpk2f.accept(new File("/tmp/EPSG_4326_00/00_00"), "00_00.png"));
        assertFalse(fpk2f.accept(new File("/tmp/EPSG_4326_00/00_00"), "06_05.png"));
        assertTrue(fpk2f.accept(new File("/tmp/EPSG_4326_02/00_00"), "5_6.png"));
        assertFalse(fpk2f.accept(new File("/tmp/EPSG_4326_02/00_00"), "5_7.png"));
    }
}