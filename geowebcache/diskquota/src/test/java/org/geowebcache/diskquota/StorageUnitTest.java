package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.StorageUnit.B;
import static org.geowebcache.diskquota.StorageUnit.GiB;
import static org.geowebcache.diskquota.StorageUnit.KiB;
import static org.geowebcache.diskquota.StorageUnit.PiB;
import static org.geowebcache.diskquota.StorageUnit.TiB;

import java.math.BigDecimal;

import junit.framework.TestCase;

public class StorageUnitTest extends TestCase {

    public void testConvertTo() {
        assertEquals(1D, B.convertTo(1024, KiB).doubleValue());
        assertEquals(1D, KiB.convertTo(1024 * 1024D, GiB).doubleValue());
        assertEquals(1024D, GiB.convertTo(1024 * 1024D, TiB).doubleValue());

        BigDecimal k = BigDecimal.valueOf(1024);
        BigDecimal value = k.multiply(k).multiply(k).multiply(k).multiply(k);
        assertEquals(BigDecimal.ONE, B.convertTo(value, PiB));
    }
}
