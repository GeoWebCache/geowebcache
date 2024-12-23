/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.storage.StorageUnit.B;
import static org.geowebcache.diskquota.storage.StorageUnit.EiB;
import static org.geowebcache.diskquota.storage.StorageUnit.GiB;
import static org.geowebcache.diskquota.storage.StorageUnit.KiB;
import static org.geowebcache.diskquota.storage.StorageUnit.MiB;
import static org.geowebcache.diskquota.storage.StorageUnit.PiB;
import static org.geowebcache.diskquota.storage.StorageUnit.TiB;
import static org.geowebcache.diskquota.storage.StorageUnit.YiB;
import static org.geowebcache.diskquota.storage.StorageUnit.ZiB;
import static org.geowebcache.diskquota.storage.StorageUnit.bestFit;

import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Test;

public class StorageUnitTest {

    @Test
    public void testConvertTo() {
        Assert.assertEquals(1D, B.convertTo(1024, KiB).doubleValue(), 0d);
        Assert.assertEquals(1D, KiB.convertTo(1024 * 1024D, GiB).doubleValue(), 0d);
        Assert.assertEquals(1024D, GiB.convertTo(1024 * 1024D, TiB).doubleValue(), 0d);

        BigDecimal k = BigDecimal.valueOf(1024);
        BigDecimal value = k.multiply(k).multiply(k).multiply(k).multiply(k);
        Assert.assertEquals(BigDecimal.ONE, B.convertTo(value, PiB));
    }

    @Test
    public void testClosest() {
        Assert.assertEquals(YiB, bestFit(1, YiB));
        Assert.assertEquals(YiB, bestFit(1025, ZiB));
        Assert.assertEquals(ZiB, bestFit(1023, ZiB));
        Assert.assertEquals(ZiB, bestFit(1025, EiB));
        Assert.assertEquals(EiB, bestFit(1023, EiB));
        Assert.assertEquals(EiB, bestFit(1025, PiB));
        Assert.assertEquals(PiB, bestFit(1023, PiB));
        Assert.assertEquals(TiB, bestFit(1023, TiB));
        Assert.assertEquals(TiB, bestFit(1025, GiB));
        Assert.assertEquals(GiB, bestFit(1023, GiB));
        Assert.assertEquals(GiB, bestFit(1025, MiB));
        Assert.assertEquals(MiB, bestFit(1023, MiB));
        Assert.assertEquals(MiB, bestFit(1025, KiB));
        Assert.assertEquals(KiB, bestFit(1023, KiB));
        Assert.assertEquals(KiB, bestFit(1025, B));
        Assert.assertEquals(B, bestFit(0.5, KiB));
    }
}
