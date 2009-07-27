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

import junit.framework.TestCase;

import org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend;

public class MetaStoreTest extends TestCase {
    public static final String TEST_DB_NAME = "gwcTestMetaStore";
    
    public void testTile() throws Exception {
        byte[] bytes = null;
        TileObject to2 = null;
        
        try {
            MetaStore ms = setup();

            long[] xyz = { 1L, 2L, 3L };
            bytes = "Test 1 2 3".getBytes();
            TileObject to = TileObject.createCompleteTileObject(
                    "test'Layer:æøå;", xyz, 4326, "jpeg", "a=x&b=y", bytes);

            ms.put(to);
            ms.unlock(to);
            
            long[] xyz2 = { 1L, 2L, 3L };
            to2 = TileObject.createQueryTileObject(
                    "test'Layer:æøå;", xyz2, 4326, "jpeg", "a=x&b=y");

            ms.get(to2);

        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }
        assertEquals(bytes.length,to2.getBlobSize());
        assertEquals(true, to2.getCreated() <= System.currentTimeMillis());
    }
    
    public void testWFSParam() throws Exception {
        WFSObject wo2 = null;
        try {
            MetaStore ms = setup();
            
            WFSObject wo = WFSObject.createCompleteWFSObject("a=æ&å=Ø");
            ms.put(wo);
            ms.unlock(wo);
            
            wo2 = WFSObject.createQueryWFSObject("a=æ&å=Ø");
            ms.get(wo2);

        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }

        //assertEquals(bytes.length, wo2.getBlobSize());
        assertEquals(true, wo2.getCreated() <= System.currentTimeMillis());
    }
    
    public void testWFSQueryBlob() throws Exception {
        WFSObject wo = null;
        WFSObject wo2 = null;
        byte[] queryBytes = null;
        
        try {

            MetaStore ms = setup();
            queryBytes = "1 2 3 4 5 6 Test".getBytes();
            wo = WFSObject.createCompleteWFSObject(queryBytes);
            ms.put(wo);
            ms.unlock(wo);
            wo2 = WFSObject.createQueryWFSObject(queryBytes);
            ms.get(wo2);

        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }

        //assertEquals(bytes.length, wo2.getBlobSize());
        // The next few are a bit silly, but they should be equal anyway
        assertEquals(wo2.getQueryBlobMd5(), wo.getQueryBlobMd5());
       // assertEquals(wo2.getQueryBlobSize(), queryBytes.length);
        assertEquals(true, wo2.getCreated() <= System.currentTimeMillis());

        StorageBrokerTest.deleteDb(TEST_DB_NAME);
    }
    
    public void testTileDelete() throws Exception {
        byte[] bytes = null;
        TileObject to2 = null;
        TileObject to3 = null;
        String layerName = "test'Layer:æøå;";
        String format = "jpeg";
        String parameters = "a=x&b=y";
        
        try {
            MetaStore ms = setup();

            long[] xyz = { 1L, 2L, 3L };
            bytes = "Test 1 2 3".getBytes();
            TileObject to = TileObject.createCompleteTileObject(
                    layerName, xyz, 4326, format, parameters, bytes);

            ms.put(to);
            ms.unlock(to);
            
            // Check
            long[] xyz2 = { 1L, 2L, 3L };
            to2 = TileObject.createQueryTileObject(
                    layerName, xyz2, 4326, format, parameters);

            assertTrue(ms.get(to2));
            assertEquals(bytes.length,to2.getBlobSize());
            
            // Delete
            assertTrue(ms.delete(to));
            
            // Check
            long[] xyz3 = { 1L, 2L, 3L };
            to3 = TileObject.createQueryTileObject(
                    layerName, xyz3, 4326, format, parameters);

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

