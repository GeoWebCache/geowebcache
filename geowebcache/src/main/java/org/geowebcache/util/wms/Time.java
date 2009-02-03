package org.geowebcache.util.wms;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

public abstract class Time {
    private static Log log = LogFactory.getLog(Time.class);

    public Time(List<String> time) {
        setFromStringArray(time);
        if (log.isTraceEnabled()) {
            log.trace("Created Time: " + getReadableString());
        }
    }
    
    /**
     * Sets from an array of strings
     * 
     * @param TimeList
     */
    public abstract void setFromStringArray(List<String> times);

    /**
     * Gets all possible time values as an array
     * 
     * @return times as a list of DateTime
     */
    public abstract List<DateTime> getTimeArray();
    
    /**
     * Outputs a string suitable for logging and other human-readable tasks
     * 
     * @return a readable string
     */
    public abstract String getReadableString();
    
    /**
     * Returns a comma separated value String suitable for URL output
     */
    @Override
    public abstract String toString();
    
    public abstract String toKML();
    
    /**
     * Comparing whether the differences between the times can be
     * ignored.
     * 
     * @param other
     * @return whether the time intervals are equals
     */
    public abstract boolean equals(Object obj); 
    
    @Override
    public abstract int hashCode();
    
    /**
     * Check whether this time interval contains the time interval
     * 
     * @param other
     * @return whether other is contained by this
     */
    public abstract boolean contains(Time other);
    
    /**
     * Method for getting the most current data (the most recent DateTime)
     * @return the most recent DateTime object
     */
    public abstract DateTime getCurrent();

    /**
     * Minimal sanity check
     */
    public abstract boolean isSane(); 
    
}
