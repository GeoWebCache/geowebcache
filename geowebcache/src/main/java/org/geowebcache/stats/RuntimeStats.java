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
 * @author Arne Kepp, The OpenGeo, Copyright 2009
 */
package org.geowebcache.stats;

public class RuntimeStats {
    
    static int POLL_INTERVAL = 3;
    
    long startTime = System.currentTimeMillis();
    
    // These must be multiples of POLL_INTERVAL
    int[] intervals = {3, 15, 60};
    
    String[] intervalDesc = {"3 seconds", "15 seconds", "60 seconds"};
    
    int curBytes = 0;
    
    int curRequests = 0;
    
    int peakBytes = 0;
    
    int peakRequests = 0;
    
    long totalBytes = 0;
    
    long totalRequests = 0;
    
    // Ringbuffers to holds the samples. 160 bytes in total
    
    int[] bytes = new int[intervals[intervals.length - 1] / POLL_INTERVAL];
    
    int[] requests  = new int[intervals[intervals.length - 1] / POLL_INTERVAL];
    
    int ringPos = 0;
    
    RuntimeStatsThread statsThread;
    
    public RuntimeStats() {
        
    }
    
    public void start() {
        statsThread = new RuntimeStatsThread(this);
        
        statsThread.start();
    }
    
    public void destroy() {
        if(this.statsThread != null) {
            statsThread.run = false;
        
            statsThread.interrupt();
            
            Thread.yield();
        }
    }
    
    public void log(int size) {
        if(this.statsThread != null) {
            synchronized(this) { 
                curBytes += size;
                curRequests += 1;
            }
        }
    }
    
    protected synchronized int[] popIntervalData() {
        int[] ret = {curBytes, curRequests};
        
        curBytes = 0;
        curRequests = 0;
        
        return ret;
    }

    public synchronized String getHTMLStats() {
        long runningTime = (System.currentTimeMillis() - startTime) / 1000;
        
        StringBuilder str = new StringBuilder();
        
        str.append("<table border=\"0\" cellspacing=\"5\">");
        
        
        synchronized(bytes) {
            str.append("<tr><td colspan=\"2\">Total number of requests:</td><td colspan=\"3\">"+totalRequests+"</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Total number of bytes:</td><td colspan=\"3\">"+totalBytes+"</td></tr>\n");
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
            
            str.append("<tr><td colspan=\"2\">Peak request rate:</td><td colspan=\"3\">"+ formatRequests( (peakRequests * 1.0) / POLL_INTERVAL) +"</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Peak bandwidth:</td><td colspan=\"3\">"+ formatBits((peakBytes * 8.0) / POLL_INTERVAL) +"</td></tr>\n");
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
                        
            str.append("<tr><td>Interval</td><td>Requests</td><td>Rate</td><td>Bytes</td><td>Bandwidth</td></tr>\n");
            
            for(int i=0; i<intervals.length; i++) {
                if(runningTime < intervals[i]) {
                    continue;
                }
                
                String[] requests = calculateRequests(intervals[i]);
                
                String[] bits = calculateBits(intervals[i]);
                
                str.append("<tr><td>"
                        +intervalDesc[i]+"</td><td>"
                        +requests[0]+"</td><td>"
                        +requests[1]+"</td><td>"
                        +bits[0]+"</td><td>"
                        +bits[1]+"</td><td>"
                        +"</tr>\n");
            }
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
            
            str.append("<tr><td colspan=\"5\">Note: These figures are 3 seconds delayed and do not include HTTP overhead</td></tr>");
            
        }
        
        return str.toString();
    }
    
    private String[] calculateRequests(int interval) {
        int nodeCount = interval / RuntimeStats.POLL_INTERVAL;
        
        int accu = 0;
        
        int pos = ((ringPos - 1) + bytes.length) % bytes.length;
        
        synchronized(bytes) {
            for(int i=0; i<nodeCount; i++) {
                accu += requests[pos];
                pos = ((pos - 1) + bytes.length) % bytes.length;
            }
        }
        
        String avg = formatRequests((accu * 1.0) / interval);
        
        String[] ret = {accu + "", avg}; 
        
        return ret;
    }
    
    private String formatRequests(double requestsps) {
        return Math.round(requestsps * 10.0) / 10.0 + " /s";
    }
    
    private String[] calculateBits(int interval) {
        
        int nodeCount = interval / RuntimeStats.POLL_INTERVAL;
        
        int accu = 0;
        
        int pos = ((ringPos - 1) + bytes.length) % bytes.length;
        
        synchronized(bytes) {
            for(int i=0; i<nodeCount; i++) {
                accu += bytes[pos];    
                pos = ((pos - 1) + bytes.length) % bytes.length;
            }
        }
        
        String avg = formatBits((accu * 8.0) / interval);
        
        String[] ret = {accu + "", avg}; 
        
        return ret; 
    }
    
    private String formatBits(double bitsps) {
        String avg;
        
        if(bitsps > 1000000) {
            avg = (Math.round(bitsps / 100000.0) / 10.0)  + " mbps";
        } else if(bitsps > 1000) {
            avg = (Math.round(bitsps / 100.0) / 10.0)  + " kbps";
        } else {
            avg = (Math.round(bitsps * 10.0) / 10.0)  + " bps";
        }
        
        return avg;
    }
    
    private class RuntimeStatsThread extends Thread {
        
        final RuntimeStats stats;
        
        boolean run = true;
        
        private RuntimeStatsThread(RuntimeStats runtimeStats) {
            this.stats = runtimeStats;
        }
        
        public void run() {
            while(run) {
                try {
                    Thread.sleep(RuntimeStats.POLL_INTERVAL * 1000);
                } catch (InterruptedException e) {
                    // /Nothing
                }
                
                updateLists();
            }
        }

        private void updateLists() {
            int[] bytesRequests = stats.popIntervalData();
            
            stats.totalBytes += bytesRequests[0];
            stats.totalRequests += bytesRequests[1];
            
            if(bytesRequests[0] > peakBytes) {
                peakBytes = bytesRequests[0];
            }
            
            if(bytesRequests[1] > peakRequests) {
                peakRequests = bytesRequests[1];
            }
                        
            synchronized(bytes) {
                bytes[ringPos] = bytesRequests[0];
                requests[ringPos] = bytesRequests[1];
                
                ringPos = (ringPos + 1) % bytes.length;
            }
        }
    }
}
