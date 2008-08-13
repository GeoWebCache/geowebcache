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
 * @author Marius Suta / The Open Planning Project 2008 
 */
package org.geowebcache.seeder;

import org.geowebcache.util.wms.BBOX;
import org.geowebcache.layer.SRS;

public class SeedRequest {
    private String name = null;

    private BBOX bounds = null;

    private SRS projection = null;

    private int zoomstart = -1;

    private int zoomstop = -1;

    private String format = null;

    /**
     * Method returns the name of the tileLayer that was requested
     * @return name of the requested tile layer
     */
    public String getName() {
        return this.name;
    }
    /**
     * Method gets the bounds for the requested region
     * @return a BBOX 
     */
    public BBOX getBounds() {
        return this.bounds;
    }
    /**
     * Method returns the projection for this request
     * @return SRS
     */
    public SRS getProjection() {
        return this.projection;
    }
    /**
     * Method returns the format requested
     * @return the format in String form 
     */
    public String getMimeFormat() {
        return this.format;
    }
    /**
     * Method returns the zoom start level for this seed request
     * @return integer representing zoom start level
     */
    public int getZoomStart() {
        return this.zoomstart;
    }
    /**
     * Method returns the zoom stop level for this seed request
     * @return integer representing zoom stop level
     */
    public int getZoomStop() {
        return this.zoomstop;
    }

}
