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
package org.geowebcache.filter.request;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;

/**
 * This is a test filter for the new request filter core.
 * 
 * It is not really useful other than to illustrate the concept.
 * 
 * Basically it takes the extent of the layer and constructs a
 * a circle that covers half the 
 *
 */
public class CircularExtentFilter extends RequestFilter {
    
    CircularExtentFilter() {
        
    }
    
    public void apply(ConveyorTile convTile) throws RequestFilterException {
        TileLayer tl = convTile.getLayer();
        SRS srs = convTile.getSRS(); 
        Grid grid = tl.getGrid(srs);
        int z = convTile.getTileIndex()[2];
        int[] gridBounds = null;
        
        try {
            gridBounds = grid.getGridCalculator().getGridBounds(z);
        } catch (GeoWebCacheException e) {
            e.printStackTrace();
        }
        
        // Figure out the radius
        int width = gridBounds[2] - gridBounds[0];
        int height = gridBounds[3] - gridBounds[1];
        
        // Rounding must always err on the side of 
        // caution if you want to use KML hierarchies
        int maxRad = 0;
        if(width > height) {
            maxRad = (width / 4) + 1;
        } else {
            maxRad = (height / 4) + 1;
        }
        
        // Figure out how the requested bounds relate
        int midX = gridBounds[0] + width/2;
        int midY = gridBounds[1] + height/2;
        

        int xDist = midX - convTile.getTileIndex()[0];
        int yDist = midY - convTile.getTileIndex()[1];
        
        long rad = Math.round(Math.sqrt(xDist*xDist + yDist*yDist));
        
        if(rad > maxRad) {
            throw new BlankTileException(this);
        }   
    }

    public void initialize(TileLayer layer) throws GeoWebCacheException {
        // Do nothing
    }
}
