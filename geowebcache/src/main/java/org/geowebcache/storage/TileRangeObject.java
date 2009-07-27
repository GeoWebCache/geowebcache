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

import org.geowebcache.layer.SRS;
import org.geowebcache.mime.MimeType;

public class TileRangeObject {
    public String layerName;
    
    public SRS srs;
    
    public int zoomStart;
    
    public int zoomStop;
    
    // {zoom}{minx,miny,maxx,maxy}
    public int[][] rangeBounds;
    
    public MimeType mimeType;
    
    public String parameters;
    
    public TileRangeObject(String layerName, SRS srs, int zoomStart, 
            int zoomStop, int[][] rangeBounds, MimeType mimeType, String parameters) {
        this.layerName = layerName;
        this.srs = srs;
        this.rangeBounds = rangeBounds;
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.mimeType = mimeType;
        this.parameters = parameters;
    }
}
