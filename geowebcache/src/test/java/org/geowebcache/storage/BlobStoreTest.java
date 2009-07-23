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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;

import junit.framework.TestCase;

import org.geowebcache.storage.blobstore.file.FileBlobStore;

public class BlobStoreTest extends TestCase {
    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";
    
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
        WFSObject wo = WFSObject.createCompleteWFSObject("a=æ&å=Ø");
        wo.setInputStream(new ByteArrayInputStream(bytes));
        wo.setId(123123123);
        
        fbs.put(wo);
        
        WFSObject wo2 = WFSObject.createQueryWFSObject("a=æ&å=Ø");
        wo2.setId(123123123);
        
        fbs.get(wo2);
        
        InputStream is = new ByteArrayInputStream(bytes);
        InputStream is2 = wo2.getInputStream();
        
        int read1 = 0;
        int read2 = 0;
        byte[] tmp1 = new byte[1];
        byte[] tmp2 = new byte[1];
        while(read1 != -1 && read2 != -1){
            read1 = is.read(tmp1);
            read2 = is2.read(tmp2);
            
            if(read1 != -1)
                assertEquals(tmp1[0],tmp2[0]);
        }
        
        assertEquals(read1,read2);
    }
    
    public void testWFSBlob() throws Exception {
        FileBlobStore fbs = setup();
        
        byte[] bytes = "1 2 3 Test".getBytes();
        byte[] queryBlob = "'ad;wer0sv234".getBytes();
        
        WFSObject wo = WFSObject.createCompleteWFSObject(queryBlob);
        wo.setInputStream(new ByteArrayInputStream(bytes));
        wo.setId(123123123);
        
        fbs.put(wo);
        
        WFSObject wo2 = WFSObject.createQueryWFSObject(queryBlob);
        wo2.setId(123123123);
        
        fbs.get(wo2);
        
        InputStream is = new ByteArrayInputStream(bytes);
        InputStream is2 = wo2.getInputStream();
        
        int read1 = 0;
        int read2 = 0;
        byte[] tmp1 = new byte[1];
        byte[] tmp2 = new byte[1];
        while(read1 != -1 && read2 != -1){
            read1 = is.read(tmp1);
            read2 = is2.read(tmp2);
            
            if(read1 != -1)
                assertEquals(tmp1[0],tmp2[0]);
        }
        
        assertEquals(read1,read2);
    }
    
    public FileBlobStore setup() throws Exception {
        File fh = new File(StorageBrokerTest.findTempDir() 
                + File.separator + TEST_BLOB_DIR_NAME);
        
        if(! fh.mkdirs()) {
            throw new StorageException("Unable to create " + fh.getAbsolutePath());
        }
        
        return new FileBlobStore(StorageBrokerTest.findTempDir() 
                + File.separator + TEST_BLOB_DIR_NAME);
    }
}
