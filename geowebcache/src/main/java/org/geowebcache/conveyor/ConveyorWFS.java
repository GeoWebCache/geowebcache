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
package org.geowebcache.conveyor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.WFSObject;

public class ConveyorWFS extends Conveyor {
    private static Log log = LogFactory.getLog(org.geowebcache.conveyor.ConveyorWFS.class);
    
    public ConveyorWFS(StorageBroker sb, String parameters, byte[] queryBlob, 
            HttpServletRequest srq, HttpServletResponse srp) {
        super(sb, srq, srp);
        super.setRequestHandler(Conveyor.RequestHandler.SERVICE);
        
        if(queryBlob != null) {
            super.stObj = WFSObject.createQueryWFSObject(queryBlob);
        } else if(parameters != null) {
            super.stObj = WFSObject.createQueryWFSObject(parameters);
        }
    }
    
    public byte[] getQueryBlob() {
        return ((WFSObject) stObj).getQueryBlob();
    }
    
    public boolean persist() throws GeoWebCacheException {
        return storageBroker.put((WFSObject) stObj);
    }
    
    public String getMimeTypeString() {
        return ((WFSObject) stObj).getBlobFormat();
    }
    
    public void setMimeTypeString(String mimeType) {
        ((WFSObject) stObj).setBlobFormat(mimeType);
    }
    
    public boolean retrieve(int maxAge) throws GeoWebCacheException {
        try {
            return storageBroker.get((WFSObject) stObj);
        } catch (StorageException se) {
            throw new GeoWebCacheException(se.getMessage());
        }
    }
}
