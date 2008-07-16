package org.geowebcache.cache.file;

import org.geowebcache.layer.SRS;

import junit.framework.TestCase;

public class FilePathKey2Test extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testPadder() throws Exception {
        String actual = FilePathKey2.zeroPadder(0, 1);
        this.assertEquals("0", actual);
        
        actual = FilePathKey2.zeroPadder(1, 2);
        this.assertEquals("01", actual);
        
        actual = FilePathKey2.zeroPadder(1, 3);
        this.assertEquals("001", actual);
        
        actual = FilePathKey2.zeroPadder(100, 2);
        this.assertEquals("100", actual);
        
        actual = FilePathKey2.zeroPadder(99, 2);
        this.assertEquals("99", actual);
        
        actual = FilePathKey2.zeroPadder(99, 3);
        this.assertEquals("099", actual);
    }
    
    public void testFilePath() throws Exception {
        FilePathKey2 fpk = new FilePathKey2();
        
        String actual = fpk.createKey("/tmp/gwc", 0, 0, 0, SRS.getEPSG4326(), "png");
        this.assertEquals("/tmp/gwc/EPSG_4326_00/0_0/00_00.png", actual);

        actual = fpk.createKey("/tmp/gwc", 15, 15, 4, SRS.getEPSG4326(), "png");
        this.assertEquals("/tmp/gwc/EPSG_4326_04/1_1/15_15.png", actual);
        
        actual = fpk.createKey("/tmp/gwc", 03, 03, 4, SRS.getEPSG4326(), "png");
        this.assertEquals("/tmp/gwc/EPSG_4326_04/0_0/03_03.png", actual);
    }
}