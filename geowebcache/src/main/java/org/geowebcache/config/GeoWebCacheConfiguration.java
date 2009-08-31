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

package org.geowebcache.config;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.FormatModifier;


/**
 * This class only exists to facilitate the use of XStream, 
 * so that the XML configuration file(s) can be loaded directly.
 */
public class GeoWebCacheConfiguration {
    
    /* Attributes for parser */
    String xmlns_xsi;

    String xsi_noNamespaceSchemaLocation; 

    String xmlns;
    
    /* Meta information */
    String version = "";
    
    /* Default values */
    Integer backendTimeout;
    
    Boolean cacheBypassAllowed;
    
    Boolean disableRuntimeStats;
    
    ServiceInformation serviceInformation;
    
    protected List<FormatModifier> formatModifiers;
        
    /* The layers */
    LinkedList<TileLayer> layers;

    public GeoWebCacheConfiguration() {

    }

    /** 
     * Initialize these variables because XStream for some reason doesnt do it. 
     */
    public void init() {
        xmlns_xsi = "http://www.w3.org/2001/XMLSchema-instance";
        xsi_noNamespaceSchemaLocation = "http://geowebcache.org/schema/1.2.0/geowebcache.xsd";
        xmlns = "http://geowebcache.org/schema/1.2.0";
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
