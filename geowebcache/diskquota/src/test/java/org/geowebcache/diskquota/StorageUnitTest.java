package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.StorageUnit.B;
import static org.geowebcache.diskquota.StorageUnit.EiB;
import static org.geowebcache.diskquota.StorageUnit.GiB;
import static org.geowebcache.diskquota.StorageUnit.KiB;
import static org.geowebcache.diskquota.StorageUnit.MiB;
import static org.geowebcache.diskquota.StorageUnit.PiB;
import static org.geowebcache.diskquota.StorageUnit.TiB;
import static org.geowebcache.diskquota.StorageUnit.YiB;
import static org.geowebcache.diskquota.StorageUnit.ZiB;
import static org.geowebcache.diskquota.StorageUnit.closest;

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

    public void testClosest() {
        assertEquals(YiB, closest(1, YiB));
        assertEquals(YiB, closest(1025, ZiB));
        assertEquals(ZiB, closest(1023, ZiB));
        assertEquals(ZiB, closest(1025, EiB));
        assertEquals(EiB, closest(1023, EiB));
        assertEquals(EiB, closest(1025, PiB));
        assertEquals(PiB, closest(1023, PiB));
        assertEquals(TiB, closest(1023, TiB));
        assertEquals(TiB, closest(1025, GiB));
        assertEquals(GiB, closest(1023, GiB));
        assertEquals(GiB, closest(1025, MiB));
        assertEquals(MiB, closest(1023, MiB));
        assertEquals(MiB, closest(1025, KiB));
        assertEquals(KiB, closest(1023, KiB));
        assertEquals(KiB, closest(1025, B));
        assertEquals(B, closest(0.5, KiB));
    }

}
