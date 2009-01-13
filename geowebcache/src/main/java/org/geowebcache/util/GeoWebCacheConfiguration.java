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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */

package org.geowebcache.util;

import java.util.Iterator;
import java.util.LinkedList;

import org.geowebcache.layer.TileLayer;


/**
 * This class only exists to facilitate the use of XStream, 
 * so that the XML configuration file(s) can be loaded directly.
 */
public class GeoWebCacheConfiguration {

    /* Meta information */
    String version = "";
    
    /* Default values */
    Integer backendTimeout;
    
    Boolean cacheBypassAllowed;
    
    /* The layers */
    LinkedList<TileLayer> layers;

    public GeoWebCacheConfiguration() {
        
    }
    
    protected boolean replaceLayer(TileLayer layer) {
        if(layers == null)
            return false;
        
        Iterator<TileLayer> iter = layers.iterator();
        while(iter.hasNext()) {
            TileLayer aLayer = iter.next();
            if(aLayer.getName().equals(layer.getName())) {
                layers.remove(aLayer);
                layers.add(layer);
                return true;
            }
        }
        return false;
    }

    protected boolean addLayer(TileLayer layer) {
        if(layers == null)
            return false;
        
        Iterator<TileLayer> iter = layers.iterator();
        while(iter.hasNext()) {
            TileLayer aLayer = iter.next();
            if(aLayer.getName().equals(layer.getName())) {
                return false;
            }
        }
        
        layers.add(layer);
        return true;
    }
    
    protected boolean removeLayer(TileLayer layer) {
        if(layers == null)
            return false;
        
        return layers.remove(layer);
    }
}
