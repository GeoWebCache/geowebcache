package org.geowebcache.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ByteUtilsTest {

    @Before
    public void setUp() throws Exception {}

    @Test
    public void testBasicConversion() throws Exception {
        basicConversion(0);
        basicConversion(1);
        basicConversion(2);
        basicConversion(200);
        basicConversion(300);
        basicConversion(1025);
    }

    private void basicConversion(int number) throws Exception {
        byte[] testB = ByteUtils.uIntLongToByteWord(number);
        long testL = ByteUtils.bytesToUIntLong(testB, 0);

        Assert.assertEquals(number, testL);
    }
}
