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

import org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend;

import junit.framework.TestCase;

public class MetaStoreTest extends TestCase {
    public static String TEST_DB_NAME = "gwcTestMetaStore";
    
    public void testTile() throws Exception {
        MetaStore ms = setup();
        
        long[] xyz = {1L,2L,3L};
        byte[] bytes = "Test 1 2 3".getBytes();
        TileObject to = TileObject.createCompleteTileObject(
                "test'Layer:æøå;", xyz, "jpeg", "a=x&b=y", bytes);
        
        ms.put(to);
        
        long[] xyz2 = {1L,2L,3L};
        TileObject to2 = TileObject.createQueryTileObject(
                "test'Layer:æøå;", xyz2, "jpeg", "a=x&b=y");
        
        ms.get(to2);
        
        assertEquals(bytes.length,to2.getBlobSize());
        assertEquals(true, to2.getCreated() <= System.currentTimeMillis());
    }
    
    public void testWFSParam() throws Exception {
        MetaStore ms = setup();
        
        byte[] bytes = "1 2 3 Test".getBytes();
        WFSObject wo = WFSObject.createCompleteWFSObject("a=æ&å=Ø", bytes);
        ms.put(wo);
        
        WFSObject wo2 = WFSObject.createQueryWFSObject("a=æ&å=Ø");
        ms.get(wo2);
        
        assertEquals(bytes.length, wo2.getBlobSize());
        assertEquals(true, wo2.getCreated() <= System.currentTimeMillis());
    }
    
    public void testWFSQueryBlob() throws Exception {
        MetaStore ms = setup();
        
        byte[] bytes = "1 2 3 Test".getBytes();
        byte[] queryBytes = "1 2 3 4 5 6 Test".getBytes();
        WFSObject wo = WFSObject.createCompleteWFSObject(queryBytes, bytes);
        ms.put(wo);
        
        WFSObject wo2 = WFSObject.createQueryWFSObject(queryBytes);
        ms.get(wo2);
        
        assertEquals(bytes.length, wo2.getBlobSize());
        // The next few are a bit silly, but they should be equal anyway
        assertEquals(wo2.getQueryBlobMd5(), wo.getQueryBlobMd5());
        assertEquals(wo2.getQueryBlobSize(), queryBytes.length);
        assertEquals(true, wo2.getCreated() <= System.currentTimeMillis());
        
        StorageBrokerTest.deleteDb(TEST_DB_NAME);
    }

    public MetaStore setup() throws Exception {
        StorageBrokerTest.deleteDb(TEST_DB_NAME);
        
        return new JDBCMetaBackend("org.h2.Driver", 
                "jdbc:h2:file:" + StorageBrokerTest.findTempDir() 
                + File.separator +TEST_DB_NAME,
                "sa",
                "");
    }
}

