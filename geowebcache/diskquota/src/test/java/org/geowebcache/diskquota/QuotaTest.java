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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota;

import junit.framework.TestCase;

public class QuotaTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testAdd() {
        Quota q1 = new Quota(512, StorageUnit.GiB);
        q1.add(1024, StorageUnit.MiB);
        assertEquals(StorageUnit.GiB, q1.getUnits());
        assertEquals(513.0, q1.getValue().doubleValue(), 1e-6);

        q1.add(512, StorageUnit.GiB);
        assertEquals(StorageUnit.TiB, q1.getUnits());
        assertEquals(1.000976, q1.getValue().doubleValue(), 1e-6);
    }

    public void testSubstract() {
        Quota q1 = new Quota();
        q1.setValue(512.0);
        q1.setUnits(StorageUnit.GiB);

        q1.subtract(1, StorageUnit.GiB);
        assertEquals(StorageUnit.GiB, q1.getUnits());
        assertEquals(511, q1.getValue().doubleValue(), 1e-6);

        q1.subtract(510.5, StorageUnit.GiB);
        assertEquals(StorageUnit.MiB, q1.getUnits());
        assertEquals(512, q1.getValue().doubleValue(), 1e-6);

        q1.subtract(1024 * 511.5, StorageUnit.KiB);
        assertEquals(StorageUnit.KiB, q1.getUnits());
        assertEquals(512, q1.getValue().doubleValue(), 1e-6);

        q1.subtract(511.5, StorageUnit.KiB);
        assertEquals(StorageUnit.B, q1.getUnits());
        assertEquals(512, q1.getValue().doubleValue(), 1e-6);

        q1.subtract(1024, StorageUnit.B);
        assertEquals(StorageUnit.B, q1.getUnits());
        assertEquals(-512D, q1.getValue().doubleValue(), 1e-6);

        q1.subtract(1024, StorageUnit.B);
        assertEquals(StorageUnit.KiB, q1.getUnits());
        assertEquals(-1.5D, q1.getValue().doubleValue(), 1e-6);
    }

    public void testDifference() {
        Quota q1 = new Quota(512, StorageUnit.GiB);
        Quota difference;

        difference = q1.difference(new Quota(1024 * 511, StorageUnit.MiB));
        assertEquals(StorageUnit.GiB, difference.getUnits());
        assertEquals(1D, difference.getValue().doubleValue(), 1e-6);

        difference = q1.difference(new Quota(511.5, StorageUnit.GiB));
        assertEquals(StorageUnit.MiB, difference.getUnits());
        assertEquals(512D, difference.getValue().doubleValue(), 1e-6);

        difference = q1.difference(new Quota(512.5, StorageUnit.GiB));
        assertEquals(StorageUnit.MiB, difference.getUnits());
        assertEquals(-512D, difference.getValue().doubleValue(), 1e-6);
    }

}
