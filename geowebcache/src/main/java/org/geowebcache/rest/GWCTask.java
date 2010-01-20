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
package org.geowebcache.rest;

import org.geowebcache.GeoWebCacheException;

/**
 * 
 */
public abstract class GWCTask {
    
    public static enum TYPE {
        SEED, RESEED, TRUNCATE, UNSET;
    };
    
    protected int threadCount = 1;
    
    protected int threadOffset = 0;
    
    long taskId = -1;
    
    protected TYPE type = TYPE.UNSET;
    
    protected String layerName = null;
    
    protected long timeSpent = -1;
    
    protected long timeRemaining  = -1;
    
    protected long tilesDone = -1;
    
    protected long tilesTotal = -1;
    
    protected boolean terminate = false;
        
    public abstract void doAction() throws GeoWebCacheException;

    public void setThreadInfo(int threadCount, int threadOffset) {
        this.threadCount = threadCount;
        this.threadOffset = threadOffset;
    }
    
    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }
    
    public long getTaskId() {
        return taskId;
    }
    
    public int getThreadCount() {
        return threadCount;
    }
    
    public int getThreadOffset() {
        return threadOffset;
    }
    
    public String getLayerName() {
        return layerName;
    }
    
    public long getTilesTotal() {
        return tilesTotal;
    }
    
    public String getTilesTotalStr() {
        if(tilesTotal > 0) {
            return Long.toString(tilesTotal);
        } else {
            return "Too many to count";
        }
        
    }
    
    public long getTilesDone() {
        return tilesDone;
    }
    
    public long getTimeRemaining() {
        if(tilesTotal > 0) {
            return timeRemaining;
        } else {
            return -2;
        }   
    }
    
    public void terminateNicely() {
        this.terminate = true;
    }
    
    public TYPE getType() {
        return type;
    }
}
