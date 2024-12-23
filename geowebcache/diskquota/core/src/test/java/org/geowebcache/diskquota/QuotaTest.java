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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class QuotaTest {

    @Before
    public void setUp() throws Exception {}

    @Test
    public void testFake() {
        Assert.assertTrue(true);
    }

    /*
     * TODO fix tests public void testAdd() { Quota q1 = new Quota(1, GiB); q1.add(1024, MiB);
     * assertEquals(, q1.getBytes().doubleValue(), 1e-6);
     *
     * q1.add(512, GiB); assertEquals(1.000976, B.convertTo(new BigDecimal(q1.getBytes()), GiB)
     * .doubleValue(), 1e-6); }
     *
     * public void testSubstract() { Quota q1 = new Quota(); q1.setBytes(1);
     *
     * q1.subtract(1, GiB); assertEquals(511, q1.getValue().doubleValue(), 1e-6);
     *
     * q1.subtract(510.5, GiB); assertEquals(MiB, q1.getUnits()); assertEquals(512,
     * q1.getValue().doubleValue(), 1e-6);
     *
     * q1.subtract(1024 * 511.5, KiB); assertEquals(KiB, q1.getUnits()); assertEquals(512,
     * q1.getValue().doubleValue(), 1e-6);
     *
     * q1.subtract(511.5, KiB); assertEquals(B, q1.getUnits()); assertEquals(512,
     * q1.getValue().doubleValue(), 1e-6);
     *
     * q1.subtract(1024, B); assertEquals(B, q1.getUnits()); assertEquals(-512D,
     * q1.getValue().doubleValue(), 1e-6);
     *
     * q1.subtract(1024, B); assertEquals(KiB, q1.getUnits()); assertEquals(-1.5D,
     * q1.getValue().doubleValue(), 1e-6); }
     *
     * public void testDifference() { Quota q1 = new Quota(512, GiB); Quota difference;
     *
     * difference = q1.difference(new Quota(1024 * 511, MiB)); assertEquals(GiB,
     * difference.getUnits()); assertEquals(1D, difference.getValue().doubleValue(), 1e-6);
     *
     * difference = q1.difference(new Quota(511.5, GiB)); assertEquals(MiB, difference.getUnits());
     * assertEquals(512D, difference.getValue().doubleValue(), 1e-6);
     *
     * difference = q1.difference(new Quota(512.5, GiB)); assertEquals(MiB, difference.getUnits());
     * assertEquals(-512D, difference.getValue().doubleValue(), 1e-6); }
     */
}
