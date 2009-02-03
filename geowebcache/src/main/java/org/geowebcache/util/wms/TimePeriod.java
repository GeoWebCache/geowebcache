package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

public class TimePeriod extends Time {
    private static Log log = LogFactory.getLog(TimePeriod.class);

    Interval interval;
    Period period;

    public TimePeriod(List<String> time) {
    	super(time);
    }

    /**
     * Sets from an array of strings
     * 
     * @param time
     */
    public void setFromStringArray(List<String> time) {
    	if (time.size() == 3) {
    		interval = new Interval(time.get(0).trim() + "/" + time.get(1).trim());
    		period = new Period(time.get(2).trim());
    	} else {
    		log.error("Does not understand " + time.toString());
    	}
    }

    /**
     * Outputs a string suitable for logging and other human-readable tasks
     * 
     * @return a readable string
     */
    public String getReadableString() {
    	return "Start: " + interval.getStart().toString() + 
    		   " End: " + interval.getEnd().toString() + 
    		   " Period: " + period.toString();
    }

    /**
     * Returns a comma separated value String suitable for URL output
     */
    @Override
    public String toString() {
    	return interval.toString() + "/" + period.toString();
    }

    public String toKML() {
    	// TODO
    	
    	return null;
    }
    
    /**
     * Comparing whether the differences between the time periods can be
     * ignored.
     * 
     * @param other
     * @return whether the time intervals and periods are equal
     */
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == this.getClass()) {
            TimePeriod other = (TimePeriod) obj;
            return interval.equals(other.interval) && period.equals(other.period);
        }
        return false;
    }

    /**
     * Check whether this time interval contains the time interval
     * 
     * @param other
     * @return whether other is contained by this
     */
    public boolean contains(Time other) {
    	if (!(other instanceof TimePeriod)) {
    		return false;
    	}
    	TimePeriod time = (TimePeriod) other;
    	boolean contains = true;
    	if (this.interval.getStart().isBefore(time.interval.getStart())) {
    		contains = false;
    	} else if (this.interval.getEnd().isAfter(time.interval.getEnd())) {
    		contains = false;
    	}
    	/*
    	 * meta code
    	 * 
    	 * this.period equal to other.period or this.period equal to other.period/(x*2)
    	 * 		and
    	 * other.getStart() equals one date in the series of this
    	 */
        return contains;
    }

    /**
     * Minimal sanity check
     * 
     * @return whether start < end and start + period < end
     */
    public boolean isSane() {
    	boolean sane = true;
    	if (interval.getStart().isAfter(interval.getEnd())) {
    		sane = false;
    	} else if (interval.getStart().plus(period).isAfter(interval.getEnd())) {
    		sane = false;
    	}
    	
        return sane;
    }
    
    public List<DateTime> getTimeArray() {
    	DateTime start = interval.getStart();
    	DateTime end = interval.getEnd();
    	DateTime now = start;
    	List<DateTime> times = new ArrayList<DateTime>();
    	while(now.isBefore(end) || now.equals(end)) {
    		times.add(now);
    		now = now.plus(period);
    	}
    	return times;
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 31	+ ((interval == null) ? 0 : interval.hashCode());
		result = prime * result + ((period == null) ? 0 : period.hashCode());
		return result;
	}

	@Override
	public DateTime getCurrent() {
		return interval.getEnd();
	}

}
