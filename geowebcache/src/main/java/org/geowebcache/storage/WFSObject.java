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

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.geowebcache.util.ServletUtils;

public class WFSObject extends StorageObject {
    public static final String TYPE = "wfs";
    
    long wfs_id = -1L;
    
    String parameters;
    
    int query_blob_size = -1;
    
    byte[] query_blob;
    
    String query_blob_md5;
    
    InputStream wfsInputStream;
     
    public static WFSObject createCompleteWFSObject(
            String parameters) {
        WFSObject obj = createQueryWFSObject(parameters);
        
        obj.created = System.currentTimeMillis();
        return obj;
    }
    
    public static WFSObject createCompleteWFSObject(
            byte[] queryBlob) {
        WFSObject obj = createQueryWFSObject(queryBlob);
        
        obj.created = System.currentTimeMillis();
        return obj;
    }
    
    public static WFSObject createQueryWFSObject(String parameters) {
        WFSObject obj = new WFSObject();
        obj.parameters = parameters;
        return obj;
    }
    
    public static WFSObject createQueryWFSObject(byte[] queryBlob) {
        WFSObject obj = new WFSObject();
        obj.setQueryBlob(queryBlob);
        return obj;
    }
    
    private WFSObject() {
    }
    
    public long getId() {
        return wfs_id;
    }

    public String getParameters() {
        return parameters;
    }
    
    public String getType() {
        return TYPE;
    }
    
    public byte[] getQueryBlob() {
        return query_blob;
    }
    
    public String getQueryBlobMd5() {
        return query_blob_md5;
    }
    
    public InputStream getInputStream() {
        return wfsInputStream;
    }
    
    public void setInputStream(InputStream is) {
        wfsInputStream = is;
    }
 
    
    public int getQueryBlobSize() {
        return query_blob_size;
    }
    
    public void setId(long id) {
        wfs_id = id;
    }
    
   private void setQueryBlob(byte[] query_blob) {
       this.query_blob = query_blob;
       this.query_blob_size = query_blob.length;
       this.query_blob_md5 = calculateMd5(query_blob);
   }
   
   private static String calculateMd5(byte[] queryBlob) {
       MessageDigest digest = null;
       try {
           digest = java.security.MessageDigest.getInstance("MD5");
       } catch (NoSuchAlgorithmException e) {
           e.printStackTrace();
       }
       digest.update(queryBlob);
       
       return ServletUtils.hexOfBytes(digest.digest());
   }
}
