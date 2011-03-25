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
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend;

public class MetaStoreTest extends TestCase {
    public static final String TEST_DB_NAME = "gwcTestMetaStore";
    
    public void testTile() throws Exception {
        Resource bytes = null;
        TileObject to2 = null;
        
        try {
            MetaStore ms = setup();

            long[] xyz = { 1L, 2L, 3L };
            bytes = new ByteArrayResource("Test 1 2 3".getBytes());
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("a", "x");
            parameters.put("b", "y");
            TileObject to = TileObject.createCompleteTileObject(
                    "test'Layer:æøå;", xyz, "hefty-gridSet:id", "jpeg", parameters, bytes);

            ms.put(to);
            ms.unlock(to);
            
            long[] xyz2 = { 1L, 2L, 3L };
            to2 = TileObject.createQueryTileObject(
                    "test'Layer:æøå;", xyz2, "hefty-gridSet:id", "jpeg", parameters);

            ms.get(to2);

        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }
        assertEquals(bytes.getSize(),to2.getBlobSize());
        assertEquals(true, to2.getCreated() <= System.currentTimeMillis());
    }
    
    public void testTileDelete() throws Exception {
        Resource bytes = null;
        TileObject to2 = null;
        TileObject to3 = null;
        String layerName = "test'Layer:æøå;";
        String format = "jpeg";
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "y");
        
        try {
            MetaStore ms = setup();

            long[] xyz = { 1L, 2L, 3L };
            bytes = new ByteArrayResource("Test 1 2 3".getBytes());
            TileObject to = TileObject.createCompleteTileObject(
                    layerName, xyz, "hefty-gridSet:id", format, parameters, bytes);

            ms.put(to);
            ms.unlock(to);
            
            // Check
            long[] xyz2 = { 1L, 2L, 3L };
            to2 = TileObject.createQueryTileObject(
                    layerName, xyz2, "hefty-gridSet:id", format, parameters);

            assertTrue(ms.get(to2));
            assertEquals(bytes.getSize(),to2.getBlobSize());
            
            // Delete
            assertTrue(ms.delete(to));
            
            // Check
            long[] xyz3 = { 1L, 2L, 3L };
            to3 = TileObject.createQueryTileObject(
                    layerName, xyz3, "hefty-gridSet:id", format, parameters);

            assertFalse(ms.get(to3));

            assertTrue(to3.status == StorageObject.Status.MISS);
            
        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }

    }

    public MetaStore setup() throws Exception {
        StorageBrokerTest.deleteDb(TEST_DB_NAME);
        
        return new JDBCMetaBackend("org.h2.Driver", 
                "jdbc:h2:file:" + StorageBrokerTest.findTempDir() 
                + File.separator +TEST_DB_NAME + ";TRACE_LEVEL_FILE=0",
                "sa",
                "");
    }
}

