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
package org.geowebcache.rest;

import org.geowebcache.util.wms.BBOX;
import org.geowebcache.layer.SRS;

public class SeedRequest {
    private String name = null;

    private BBOX bounds = null;

    private SRS srs = null;
    
    private Integer threadCount = null;

    private Integer zoomStart = null;

    private Integer zoomStop = null;

    private String format = null;
    
    private String type = null;
    
    public SeedRequest() {
        //do nothing, i guess
    }

    /**
     * Method returns the name of the tileLayer that was requested
     * @return name of the requested tile layer
     */
    public String getLayerName() {
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
    public SRS getSRS() {
        return this.srs;
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
    public Integer getZoomStart() {
        return this.zoomStart;
    }
    /**
     * Method returns the zoom stop level for this seed request
     * @return integer representing zoom stop level
     */
    public Integer getZoomStop() {
        return this.zoomStop;
    }

    /**
     * Method returns the number of threads that should be used 
     * for this seed request
     * @return integer representing number of threads
     */
    public Integer getThreadCount() {
        return threadCount;
    }
    
    /**
     * Method returns the type of seed, namely one of
     * <ul>
     * <li>seed - (default) seeds new tiles</li>
     * <li>reseed - seeds new tiles and replaces old ones</li>
     * <li>truncate - removes tiles</li>
     * </ul>
     * 
     * @return String representing the type of seed
     */
    public String getType() {
        return type;
    }

}
