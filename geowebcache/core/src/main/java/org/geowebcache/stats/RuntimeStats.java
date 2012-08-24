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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.util.ServletUtils;

public class RuntimeStats {    
    private static Log log = LogFactory.getLog(RuntimeStats.class);
    
    final int pollInterval;
    
    long startTime = System.currentTimeMillis();
    
    // These must be multiples of POLL_INTERVAL
    final int[] intervals;
    
    final String[] intervalDescs;
    
    int curBytes = 0;
    
    int curRequests = 0;
    
    long peakBytesTime = 0;
    
    int peakBytes = 0;
    
    long peakRequestsTime = 0;
    
    int peakRequests = 0;
    
    long totalBytes = 0;
    
    long totalRequests = 0;
    
    long totalHits;
    
    long totalMisses;

    long totalWMS;
    
    final int[] bytes;
    
    final int[] requests;
    
    int ringPos = 0;
    
    RuntimeStatsThread statsThread;
    
    /**
     * 
     * @param pollInterval seconds between recording aggregate values
     * @param intervals the intervals for which to report, in seconds, ascending. Each interval
     * must be a multiple of the pollInterval
     * @param intervalDescs the description for each of the previously defined intervals
     */
    public RuntimeStats(int pollInterval, List<Integer> intervals, List<String> intervalDescs) {
        this.pollInterval = pollInterval;
        
        if(intervals.size() != intervalDescs.size()) {
            log.fatal("The interval and interval description lists must be of the same size!");
        }
        
        if(pollInterval < 1) {
            log.error("poll interval cannot be less than 1 second");
        }
        
        this.intervals = new int[intervals.size()];
        for(int i=0; i< intervals.size(); i++) {
            int curVal = intervals.get(i);
            if(curVal % pollInterval  != 0) {
                log.error("The interval ("+curVal+") must be a multiple of the poll interval " + pollInterval); 
                curVal = curVal - (curVal % pollInterval);
            }
            this.intervals[i] = curVal;
        }
        
        this.intervalDescs = new String[intervalDescs.size()];
        for(int i=0; i< intervalDescs.size(); i++) {
            this.intervalDescs[i] = intervalDescs.get(i);
        }
        
        bytes = new int[this.intervals[this.intervals.length - 1] / pollInterval];
        requests = new int[this.intervals[this.intervals.length - 1] / pollInterval];
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
    
    public void log(int size, CacheResult cacheResult) {
        if(this.statsThread != null) {
            synchronized(bytes) {
                curBytes += size;
                curRequests += 1;
                
                if(cacheResult == CacheResult.HIT) {
                    totalHits++;
                } else if(cacheResult == CacheResult.MISS) {
                    totalMisses++;
                } else if(cacheResult == CacheResult.WMS) {
                    totalWMS++;
                }
            }
        }
    }
    
    protected int[] popIntervalData() {
        synchronized(bytes) {
            int[] ret = {curBytes, curRequests};
        
            curBytes = 0;
            curRequests = 0;
        
            return ret;
        }
    }

    public String getHTMLStats() {
        long runningTime = (System.currentTimeMillis() - startTime) / 1000;
        
        StringBuilder str = new StringBuilder();
        
        str.append("<table border=\"0\" cellspacing=\"5\">");
        
        
        synchronized(bytes) {
            // Starting time
            str.append("<tr><td colspan=\"2\">Started:</td><td colspan=\"3\">");
            str.append(ServletUtils.formatTimestamp(this.startTime)+ " (" + formatTimeDiff(runningTime) + ") ");
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Total number of requests:</td><td colspan=\"3\">"+totalRequests);
            str.append(" (" + totalRequests / (runningTime) +"/s ) ");
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Total number of untiled WMS requests:</td><td colspan=\"3\">"+totalWMS);
            str.append(" (" + totalWMS / (runningTime) +"/s ) ");
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Total number of bytes:</td><td colspan=\"3\">"+totalBytes);
            str.append(" ("+formatBits((totalBytes*8.0)/(runningTime))+") ");
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
            
            str.append("<tr><td colspan=\"2\">Cache hit ratio:</td><td colspan=\"3\">");
            if(totalHits + totalMisses > 0) {
                double hitPercentage = (totalHits * 100.0) / (totalHits + totalMisses);
                int rounded = (int) Math.round(hitPercentage * 100.0);
                int percents = rounded / 100;
                int decimals = rounded - percents * 100;
                str.append( percents + "." + decimals +"% of requests");
            } else {
                str.append("No data");
            }

            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Blank/KML/HTML:</td><td colspan=\"3\">");
            if(totalRequests > 0) {
                if(totalHits + totalMisses == 0) {
                    str.append("100.0% of requests");
                } else {
                    int rounded = (int) Math.round(((totalRequests - totalHits - totalMisses - totalWMS) * 100.0) / totalRequests);
                    int percents = rounded / 100;
                    int decimals = rounded - percents * 100;
                    str.append( percents + "." + decimals +"% of requests");
                }
            } else {
                str.append("No data");
            }
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
            
            str.append("<tr><td colspan=\"2\">Peak request rate:</td><td colspan=\"3\">");
            if(totalRequests > 0) {
                str.append(formatRequests( (peakRequests * 1.0) / pollInterval));
                str.append(" ("+ServletUtils.formatTimestamp(peakRequestsTime)+") ");
            } else {
                str.append("No data");
            }
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"2\">Peak bandwidth:</td><td colspan=\"3\">");
            if(totalRequests > 0) {
                str.append(formatBits((peakBytes * 8.0) / pollInterval));
                str.append(" ("+ServletUtils.formatTimestamp(peakRequestsTime)+") ");
            } else {
                str.append("No data");
            }
            str.append("</td></tr>\n");
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
                        
            str.append("<tr><td>Interval</td><td>Requests</td><td>Rate</td><td>Bytes</td><td>Bandwidth</td></tr>\n");
            
            for(int i=0; i<intervals.length; i++) {
                if(runningTime < intervals[i]) {
                    continue;
                }
                
                String[] requests = calculateRequests(intervals[i]);
                
                String[] bits = calculateBits(intervals[i]);
                
                str.append("<tr><td>"
                        +intervalDescs[i]+"</td><td>"
                        +requests[0]+"</td><td>"
                        +requests[1]+"</td><td>"
                        +bits[0]+"</td><td>"
                        +bits[1]+"</td><td>"
                        +"</tr>\n");
            }
            
            str.append("<tr><td colspan=\"5\"> </td></tr>");
            
            str.append("<tr><td colspan=\"5\">All figures are "+pollInterval+" second(s) delayed and do not include HTTP overhead</td></tr>");
            
            str.append("<tr><td colspan=\"5\">The cache hit ratio does not account for metatiling</td></tr>");
        }
        
        return str.toString();
    }
    
    private String[] calculateRequests(int interval) {
        int nodeCount = interval / pollInterval;
        
        int accu = 0;
        
        synchronized(bytes) {
            int pos = ((ringPos - 1) + bytes.length) % bytes.length;
    
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
        
        int nodeCount = interval / pollInterval;
        
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
    
    private String formatTimeDiff(long seconds) {
        if(seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if(seconds < 3600*48) {
            return (seconds / 3600) + " hours";
        } else {
            return (seconds / (3600*24)) + " days";
        }
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
                    Thread.sleep(stats.pollInterval * 1000);
                } catch (InterruptedException e) {
                    // /Nothing
                }
                
                updateLists();
            }
        }

        private void updateLists() {                        
            synchronized(bytes) {
                int[] bytesRequests = stats.popIntervalData();
                
                stats.totalBytes += bytesRequests[0];
                stats.totalRequests += bytesRequests[1];
                
                if(bytesRequests[0] > peakBytes) {
                    peakBytes = bytesRequests[0];
                    peakBytesTime = System.currentTimeMillis();
                }
                
                if(bytesRequests[1] > peakRequests) {
                    peakRequests = bytesRequests[1];
                    peakRequestsTime = System.currentTimeMillis();
                }
                
                bytes[ringPos] = bytesRequests[0];
                requests[ringPos] = bytesRequests[1];
                
                ringPos = (ringPos + 1) % bytes.length;
            }
        }
    }
}