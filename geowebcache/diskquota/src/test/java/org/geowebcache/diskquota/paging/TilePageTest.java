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
package org.geowebcache.diskquota.paging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.TestCase;

/**
 * @author groldan
 * 
 */
public class TilePageTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Test method for {@link org.geowebcache.diskquota.paging.TilePage#TilePage(int, int, int)}.
     */
    public void testSimpleConstructor() {
        TilePage page = new TilePage("testLayer", "EPSG:4326", 0, 1, 2);
        assertEquals(0, page.getX());
        assertEquals(1, page.getY());
        assertEquals(2, page.getZ());
        assertEquals(0, page.getNumHits());
        assertEquals(0, page.getNumTilesInPage());
        assertEquals(0, page.getLastAccessTimeMinutes());
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.TilePage#TilePage(int, int, int, long, long, int)

     */
    public void testFullConstructor() {
        TilePage page = new TilePage("testLayer", "EPSG:4326", 0, 1, 2, 3, 4, 5);
        assertEquals(0, page.getX());
        assertEquals(1, page.getY());
        assertEquals(2, page.getZ());
        assertEquals(3, page.getNumHits());
        assertEquals(4, page.getNumTilesInPage());
        assertEquals(5, page.getLastAccessTimeMinutes());
    }

    /**
     * Test method for {@link org.geowebcache.diskquota.paging.TilePage#markHit()}. and
     * {@link TilePage#getNumHits()}
     */
    public void testkHits() {
        TilePage page = new TilePage("testLayer", "EPSG:4326", 0, 1, 2);
        page.markHit();
        assertEquals(1, page.getNumHits());
        page.markHit();
        assertEquals(2, page.getNumHits());
    }

    /**
     * Test method for {@link org.geowebcache.diskquota.paging.TilePage#getNumTilesInPage()}.
     */
    public void testNumTilesInPage() {
        TilePage page = new TilePage("testLayer", "EPSG:4326", 0, 1, 2);
        page.addTile();
        assertEquals(1, page.getNumTilesInPage());
        page.addTile();
        assertEquals(2, page.getNumTilesInPage());
        page.removeTile();
        assertEquals(1, page.getNumTilesInPage());
    }

    /**
     * Test method for {@link org.geowebcache.diskquota.paging.TilePage#getLastAccessTimeMinutes()}.
     */
    public void testGetLastAccessTimeMinutes() {
        TilePage page = new TilePage("testLayer", "EPSG:4326", 0, 1, 2);
        assertEquals(0, page.getLastAccessTimeMinutes());
        page.markHit();
        assertTrue(page.getLastAccessTimeMinutes() > 0);
    }

    public void testSerialization() throws IOException, ClassNotFoundException {
        TilePage page = new TilePage("testLayer", "EPSG:4326", 0, 1, 2, 3, 4, 5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        oout.writeObject(page);
        oout.close();
        byte[] serialized = out.toByteArray();
        ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(serialized));
        Object readObject = oin.readObject();
        assertTrue(readObject instanceof TilePage);
        assertEquals(page, readObject);
    }
}
