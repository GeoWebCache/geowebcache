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
 * @author Gabriel Roldan, OpenGeo, Copyright 2010
 * @author Arne Kepp, OpenGeo, Copyright 2010
 */
package org.geowebcache.rest.seed;

import java.awt.image.BufferedImage;

public class RasterMask {
    /**
     * By zoom level bitmasked images where every pixel represents a tile in the level's
     * {@link GridSubset#getCoverages() grid coverage}
     */
    private final BufferedImage[] byLevelMasks;
    
    private final long[][] gridCoverages;
    
    public RasterMask(BufferedImage[] byLevelMasks, long[][] gridCoverages) {
        this.byLevelMasks = byLevelMasks;
        this.gridCoverages = gridCoverages;
    }
    
    public long[][] getGridCoverages() {
        return gridCoverages;
    }
    
    public boolean lookup(long x, long y, long z) {
        BufferedImage mat = byLevelMasks[(int) z];
        
        long[] gridCoverage = gridCoverages[(int) z];
        
        // Changing index to top left hand origin
        long rasx = x - gridCoverage[0];
        long rasy = gridCoverage[3] - y;

        // TODO , switch this to == 1 ?
        return (mat.getRaster().getSample((int) rasx, (int) rasy, 0) == 0);
    }
    
    public boolean lookup(long[] idx) {
        return lookup(idx[0], idx[1], idx[2]);
    }
}
