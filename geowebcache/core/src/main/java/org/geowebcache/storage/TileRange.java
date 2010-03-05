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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 *  
 */
package org.geowebcache.storage;

import org.geowebcache.mime.MimeType;

public class TileRange {
    final public String layerName;
    
    final public String gridSetId;
    
    final public int zoomStart;
    
    final public int zoomStop;
    
    // {zoom}{minx,miny,maxx,maxy}
    final public long[][] rangeBounds;
    
    final public MimeType mimeType;
    
    final public String parameters;
    
    public TileRange(String layerName, String gridSetId, int zoomStart, 
            int zoomStop, long[][] rangeBounds, MimeType mimeType, String parameters) {
        this.layerName = layerName;
        this.gridSetId = gridSetId;
        this.rangeBounds = rangeBounds;
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.mimeType = mimeType;
        this.parameters = parameters;
    }
    
    public boolean contains(long[] idx) {
        if(idx[2] >= zoomStart && idx[2] <= zoomStop) {
            
            long[] rB = rangeBounds[(int) idx[2]];
            
            if(rB[0] <= idx[0] 
                    && rB[2] >= idx[0] 
                    && rB[1] <= idx[1] 
                    && rB[3] >= idx[1]) {
                return true;
            }
        }
        return false;
    }
}
