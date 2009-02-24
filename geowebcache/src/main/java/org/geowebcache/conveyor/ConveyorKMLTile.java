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
 * @author Arne Kepp / The Open Planning Project 2008
 *  
 */
package org.geowebcache.conveyor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.SRS;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;


public class ConveyorKMLTile extends ConveyorTile {
    public ConveyorKMLTile(StorageBroker sb, String layerId, SRS srs,
            int[] tileIndex, MimeType mimeType, String fullParameters,
            String modifiedParameters, HttpServletRequest servletReq,
            HttpServletResponse servletResp) {
        super(sb, layerId, srs, tileIndex, mimeType, fullParameters,
                modifiedParameters, servletReq, servletResp);
    }

    private static Log log = LogFactory.getLog(org.geowebcache.conveyor.ConveyorKMLTile.class);
    
    String urlPrefix = null;
    
    protected MimeType wrapperMimeType = null;
    
    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }
    
    public String getUrlPrefix() {
        return urlPrefix;
    }
    
    public MimeType getWrapperMimeType() {
        return wrapperMimeType;
    }
    
    public void setWrapperMimeType(MimeType mimeType) {
        this.wrapperMimeType = mimeType;
    }
    
    public void setError(String message) {
        this.error = true;
        this.errorMsg = message;
    }
    
    public String getHint() {
        return hint;
    }
    
    public void setHint(String hint) {
        this.hint = hint;
    }
    
}
