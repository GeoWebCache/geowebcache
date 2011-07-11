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
 */
package org.geowebcache.storage;

import java.sql.Timestamp;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JobLogObject {

    private static Log log = LogFactory.getLog(JobLogObject.class);

    public static final String OBJECT_TYPE = "job_log";

    public static enum LOG_LEVEL {
        UNSET, INFO, WARN, ERROR
    };
    
    private long jobLogId = -1l;
    private long jobId = -1l;
    private LOG_LEVEL logLevel = LOG_LEVEL.UNSET;
    private Timestamp logTime = null;
    private String logSummary = null;
    private String logText = null;

    public static JobLogObject createJobLogObject(long jobId, LOG_LEVEL level, String summary, String text) {
        try {
            JobLogObject obj = new JobLogObject();
            
            obj.jobId = jobId;
            obj.logLevel = level;
            obj.logTime = new Timestamp(new Date().getTime());
            obj.logSummary = summary;
            obj.logText = text;
            
            return obj;
        } catch (Exception e) {
            log.error("Couldn't generate a job log. Log information will be lost. Information was:");
            log.error("\n\tJobID:" + jobId);
            log.error("\n\tLevel:" + level);
            log.error("\n\tSummary:" + summary);
            log.error("\n\tText:" + text);
            return null;
        }
    }    
    
    public static JobLogObject createErrorLog(long jobId, Throwable t) {
        return createJobLogObject(jobId, LOG_LEVEL.ERROR, "Exception: " + t.getClass().getName(), t.toString());
    }
    public static JobLogObject createWarnLog(long jobId, Throwable t) {
        return createJobLogObject(jobId, LOG_LEVEL.WARN, "Exception: " + t.getClass().getName(), t.toString());
    }
    public static JobLogObject createInfoLog(long jobId, Throwable t) {
        return createJobLogObject(jobId, LOG_LEVEL.INFO, "Exception: " + t.getClass().getName(), t.toString());
    }
    public static JobLogObject createErrorLog(long jobId, String summary, Throwable t) {
        return createJobLogObject(jobId, LOG_LEVEL.ERROR, summary, t.toString());
    }
    public static JobLogObject createWarnLog(long jobId, String summary, Throwable t) {
        return createJobLogObject(jobId, LOG_LEVEL.WARN, summary, t.toString());
    }
    public static JobLogObject createInfoLog(long jobId, String summary, Throwable t) {
        return createJobLogObject(jobId, LOG_LEVEL.INFO, summary, t.toString());
    }
    public static JobLogObject createErrorLog(long jobId, String summary, String text) {
        return createJobLogObject(jobId, LOG_LEVEL.ERROR, summary, text);
    }
    public static JobLogObject createWarnLog(long jobId, String summary, String text) {
        return createJobLogObject(jobId, LOG_LEVEL.WARN, summary, text);
    }
    public static JobLogObject createInfoLog(long jobId, String summary, String text) {
        return createJobLogObject(jobId, LOG_LEVEL.INFO, summary, text);
    }
    
    public long getJobLogId() {
        return jobLogId;
    }



    public void setJobLogId(long jobLogId) {
        this.jobLogId = jobLogId;
    }



    public long getJobId() {
        return jobId;
    }



    public void setJobId(long jobId) {
        this.jobId = jobId;
    }



    public LOG_LEVEL getLogLevel() {
        return logLevel;
    }



    public void setLogLevel(LOG_LEVEL logLevel) {
        this.logLevel = logLevel;
    }



    public Timestamp getLogTime() {
        return logTime;
    }



    public void setLogTime(Timestamp logTime) {
        this.logTime = logTime;
    }



    public String getLogSummary() {
        return logSummary;
    }



    public void setLogSummary(String logSummary) {
        this.logSummary = logSummary;
    }



    public String getLogText() {
        return logText;
    }



    public void setLogText(String logText) {
        this.logText = logText;
    }



    public String toString() {
        return "[" + logLevel + ":" + jobLogId + ",Job " + jobId + ": " + logSummary + "]";
    }
}
