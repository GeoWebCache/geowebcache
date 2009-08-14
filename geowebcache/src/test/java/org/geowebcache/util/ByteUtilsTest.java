package org.geowebcache.util;

import junit.framework.TestCase;

public class ByteUtilsTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testBasicConversion() throws Exception {
        basicConversion(0);
        basicConversion(1);
        basicConversion(2);
        basicConversion(200);
        basicConversion(300);
        basicConversion(1025);
    }
    
    private void basicConversion(int number) throws Exception {
        byte[] testB = ByteUtils.uIntLongToByteWord((long) number);
        long testL = ByteUtils.bytesToUIntLong(testB, 0);
        
        assertEquals((long) number, testL);
    }
}