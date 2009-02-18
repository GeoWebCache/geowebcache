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

public abstract class StorageObject {
    long created;
    
    long access_last;
    
    long access_count;
    
    String blob_format;
    
    int blob_size;
    
    byte[] blob;

    /** 
     * @return the unique id for this object (within the type), -1 if it has not been set
     */
    abstract public long getId();
    
    /**
     * @return the name of the type of object. Identical for all objects of a class.
     */
    abstract public String getType();
    
    public byte[] getBlob() {
        return blob;
    }
    
    public int getBlobSize() {
        return blob_size;
    }

    public String getBlobFormat() {
        return blob_format;
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
    }
    
    public void setBlob(byte[] blob) {
        if(blob != null) {
            this.blob_size = blob.length;
        } else {
            this.blob_size = -1;
        }
        
        this.blob = blob;
    }
    
    public void setBlobSize(int blob_size) { //throws StorageException {
        if(this.blob == null) {
            this.blob_size = blob_size;
        }// else {
        //    throw new StorageException("Cannot set blob size if blob is not null");
        //}
    }
}
