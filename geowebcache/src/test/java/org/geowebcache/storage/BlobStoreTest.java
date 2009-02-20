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
import java.util.Arrays;

import org.geowebcache.storage.blobstore.file.FileBlobStore;

import junit.framework.TestCase;

public class BlobStoreTest extends TestCase {
    public static String TEST_BLOB_DIR_NAME = "gwcTestBlobs";
    
    public void testTile() throws Exception {
        FileBlobStore fbs = setup();
        
        byte[] bytes = "1 2 3 4 5 6 test".getBytes();
        long[] xyz = {1L,2L,3L};
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, 4326, "image/jpeg", "a=x&b=ø", bytes);
        to.setId(11231231);
        
        fbs.put(to);
        
        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, 4326, "image/jpeg", "a=x&b=ø");
        to2.setId(11231231);
        
        byte[] resp = fbs.get(to2);
        
        to2.setBlob(resp);
        
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());
        assertTrue(Arrays.equals(to.getBlob(), to2.getBlob()));
    }
    
    public void testWFSParam() throws Exception {
        FileBlobStore fbs = setup();
        
        byte[] bytes = "1 2 3 Test".getBytes();
        WFSObject wo = WFSObject.createCompleteWFSObject("a=æ&å=Ø", bytes);
        wo.setId(123123123);
        
        fbs.put(wo);
        
        WFSObject wo2 = WFSObject.createQueryWFSObject("a=æ&å=Ø");
        wo2.setId(123123123);
        
        byte[] resp = fbs.get(wo2);
        
        wo2.setBlob(resp);
        
        assertTrue(Arrays.equals(wo.getBlob(), wo2.getBlob()));
    }
    
    public void testWFSBlob() throws Exception {
        FileBlobStore fbs = setup();
        
        byte[] bytes = "1 2 3 Test".getBytes();
        byte[] queryBlob = "'ad;wer0sv234".getBytes();
        
        WFSObject wo = WFSObject.createCompleteWFSObject(queryBlob, bytes);
        wo.setId(123123123);
        
        fbs.put(wo);
        
        WFSObject wo2 = WFSObject.createQueryWFSObject(queryBlob);
        wo2.setId(123123123);
        
        byte[] resp = fbs.get(wo2);
        wo2.setBlob(resp);
        
        assertTrue(Arrays.equals(wo.getBlob(), wo2.getBlob()));
    }
    
    public FileBlobStore setup() throws Exception {
        File fh = new File(StorageBrokerTest.findTempDir() 
                + File.separator + TEST_BLOB_DIR_NAME);
        fh.mkdirs();
        return new FileBlobStore(StorageBrokerTest.findTempDir() 
                + File.separator + TEST_BLOB_DIR_NAME);
    }
}
