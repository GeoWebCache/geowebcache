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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class XMLGridSubset {
    private static Log log = LogFactory.getLog(XMLGridSubset.class);
    
    String gridSetName;
    
    BoundingBox extent;
    
    // TODO remove in 1.2.2
    BoundingBox coverageBounds;
    
    Integer zoomStart;
    
    Integer zoomStop;
    
    
    public GridSubset getGridSubSet(GridSetBroker gridSetBroker) {
        // TODO remove in 1.2.2
        if(extent == null && coverageBounds != null) {
            extent = coverageBounds;
        }
        
        GridSet gridSet = gridSetBroker.get(gridSetName);
        
        if(gridSet == null) {
            log.error("Unable to find GridSet for \""+gridSetName+"\"");
            return null;
        }
        return GridSubsetFactory.createGridSubSet(gridSet, extent, zoomStart, zoomStop);
    }
}
