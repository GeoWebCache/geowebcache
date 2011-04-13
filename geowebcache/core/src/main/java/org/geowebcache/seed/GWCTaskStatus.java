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
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.seed;

import java.sql.Timestamp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.StorageBroker;

/**
 * 
 */
public class GWCTaskStatus extends GWCTask {
    
    private static Log log = LogFactory.getLog(org.geowebcache.seed.GWCTaskStatus.class);

    protected Timestamp timeInserted = null;
    
    protected Timestamp timeUpdated = null;
    
    protected String taskState = null;
    
    protected String taskType = null;
    
    protected boolean threadRunning = false;
        
    public GWCTaskStatus() {
    	
    }

    public void setLayerName(String name) {
    	this.layerName = name;
    }
    
    public void setTaskState(String st){
    	this.taskState = st;
    }
        
    public void setTaskType(String t){
    	this.taskType = t;
    }
    
    public void setTimeSpent(long timeSpent) {
    	this.timeSpent = timeSpent;
    }
    
    public void setTimeRemaing(long timeRemaning){
    	this.timeRemaining = timeRemaning;
    }
    
    public void setTilesDone(long done){
    	this.tilesDone = done;
    }
    
    public void setTilesTotal(long total) {
    	this.tilesTotal = total;
    }
    
    public void setTerminate(String b){
    	this.terminate = Boolean.parseBoolean(b);
    }
        
    public void setTimeInserted(Timestamp time) {
    	this.timeInserted = time;
    }

    public void setTimeUpdated(Timestamp time) {
    	this.timeUpdated = time;
    }
    
    public void setThreadRunning(boolean running) {
    	this.threadRunning = running;
    }
    
    public boolean getThreadRunning() {
    	return this.threadRunning;
    }

    @Override
    protected void dispose() {
    }

    @Override
    protected void doActionInternal() throws GeoWebCacheException, InterruptedException {
        log.error("method doActionInternal() not implemented in class GWCTaskStatus");
    }

}
