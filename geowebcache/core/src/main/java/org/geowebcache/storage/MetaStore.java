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

public interface MetaStore {
    // Whether the metastore is actually enabled
    public boolean enabled();
    
    public boolean delete(String layerName) throws StorageException;
    public boolean delete(TileObject stObj) throws StorageException;
    public boolean delete(WFSObject stObj) throws StorageException;
    public boolean delete(BlobStore blobStore, TileRange trObj) throws StorageException;
   
    public boolean expire(TileRange trObj) throws StorageException;
    
    // If lock is encountered, wait inside function until available
    public boolean get(TileObject obj) throws StorageException;
    public boolean get(WFSObject obj) throws StorageException;
    
    public void put(TileObject stObj) throws StorageException;
    public void put(WFSObject stObj) throws StorageException;
    
    public boolean unlock(TileObject stObj) throws StorageException;
    public boolean unlock(WFSObject stObj) throws StorageException;
    
    
    /**
     * Wipes the entire storage. Should only be invoked during testing.
     * 
     * @throws StorageException
     */
    public void clear() throws StorageException;
    
    /** 
     * Destroy method for Spring
     */
    public void destroy();

    
    
    ///** 
    // * Test to see whether the metastore is ready or not
    // */
    //public boolean isReady();
}
