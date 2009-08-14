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
 */
package org.geowebcache.grid;

import java.util.Hashtable;

import org.geowebcache.util.wms.BBOX;

public class GridSetBroker {

    public final GridSet WORLD_EPSG4326;
    
    public final GridSet WORLD_EPSG3857;

    
    Hashtable<String,GridSet> gridSets = new Hashtable<String,GridSet>();
    
    public GridSetBroker(boolean useEPSG900913) {
        WORLD_EPSG4326 = GridSetFactory.createGridSet(
                "EPSG:4326", 
                SRS.getEPSG4326(), 
                BBOX.WORLD4326, 
                GridSetFactory.DEFAULT_LEVELS, 
                256,
                256 );
        
        if(useEPSG900913) {
            WORLD_EPSG3857 = GridSetFactory.createGridSet(
                    "GoogleMapsCompatible",
                    SRS.getEPSG900913(),
                    BBOX.WORLD3857,
                    GridSetFactory.DEFAULT_LEVELS, 
                    256,
                    256 );
        } else {
            WORLD_EPSG3857 = GridSetFactory.createGridSet(
                    "GoogleMapsCompatible", 
                    SRS.getEPSG3857(),
                    BBOX.WORLD3857,
                    GridSetFactory.DEFAULT_LEVELS,
                    256,
                    256 );
        }        
    }
    
    public GridSet get(String gridSetId) {
        return gridSets.get(gridSetId);
    }
    
    public void put(GridSet gridSet) {
        gridSets.put(gridSet.getName(), gridSet);
    }
}
