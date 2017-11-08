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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */

package org.geowebcache.filter.request;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;

/**
 * Hardcoded to the built-in blank tile for now
 * 
 * @author ak
 */
public class BlankTileException extends RequestFilterException {
    /**
     * 
     */
    private static final long serialVersionUID = 6910805463474341350L;

    private static Log log = LogFactory.getLog(org.geowebcache.filter.request.BlankTileException.class);
    
    private volatile static Resource blankTile;
    
    public BlankTileException(RequestFilter reqFilter) {
        super(reqFilter, 200, "image/png");
    }

    private Resource getBlankTile() {       
        // Use the built-in one: 
        InputStream is = null;   
        try {
            is = GeoWebCacheDispatcher.class.getResourceAsStream("blank.png");
            byte[] blankTile = new byte[425];
            int ret = is.read(blankTile);
            log.info("Read " + ret + " from blank PNG file (expected 425).");
            
            return new ByteArrayResource(blankTile);
        } catch (IOException ioe) {
            log.error(ioe);
        } finally {
            try {
                if(is != null) 
                    is.close();
            } catch (IOException e) {
                // close quietly
            }
        }
        
       return null;
    }

    
    public Resource getResponse() {
        Resource ret = BlankTileException.blankTile;
        if (ret == null) {
            synchronized (BlankTileException.class) {
                ret = BlankTileException.blankTile;
                if (ret == null) {
                    BlankTileException.blankTile = ret = getBlankTile();
                }
            }
        }
        return ret;
    }
}
