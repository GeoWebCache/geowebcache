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
 * @author Arne Kepp, OpenGeo, Copyright 2010
 */
package org.geowebcache.layer.updatesource;

public class GeoRSSFeedDefinition extends UpdateSourceDefinition {
  
    String feedUrl;
    
    String gridSetId;
    
    Integer pollInterval;
    
    /**
     * The URL to the feed. I think we should use templating for parameters, 
     * so in the initial implementation we search the string for {lastEntryId}
     * and replace any occurrences with the actual last entry id.
     * @return
     */
    public String getFeedUrl() {
        return feedUrl;
    }
    
    /**
     * Grid set for which this feed is valid
     */
    public String gridSetId() {
        return gridSetId;
    }
    
    /**
     * Defaults to 60 minutes
     * @return the polling interval in seconds
     */
    public int pollInterval() {
        if(pollInterval != null) {
            return pollInterval;
        } else {
            return 3600;
        }
    }
}
