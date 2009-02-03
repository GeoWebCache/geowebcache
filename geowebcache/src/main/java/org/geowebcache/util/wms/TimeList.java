package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

public class TimeList extends Time {
    private static Log log = LogFactory.getLog(org.geowebcache.util.wms.TimeList.class);

    private List<DateTime> timeList;

    public TimeList(List<String> time) {
    	super(time);
    }

    /**
     * Sets from an array of strings
     * 
     * @param TimeList
     */
    public void setFromStringArray(List<String> times) {
    	this.timeList = new ArrayList<DateTime>();
    	for (String time : times) {
    		this.timeList.add(new DateTime(time.trim()));
    	}
    	sortTimes();
    }
    
    /**
     * Outputs a string suitable for logging and other human-readable tasks
     * 
     * @return a readable string
     */
    public String getReadableString() {
        StringBuffer buff = new StringBuffer(40);
    	for (int i = 0 ; i < timeList.size() ; i++) {
    		buff.append(" Time argument ");
    		buff.append(i);
    		buff.append(": ");
    		buff.append(timeList.get(i));
    	}
        return buff.toString();
    }

    /**
     * Returns a comma separated value String suitable for URL output
     */
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer(40);
    	for (int i = 0 ; i < timeList.size() ; i++) {
    		buff.append(timeList.get(i));
    		if (i+1 < timeList.size()) {
    			buff.append(',');
    		}
    	}
        return buff.toString();
    }

    public String toKML() {
    	// TODO
    	
    	return null;
    }
    
    /**
     * Comparing whether the differences between the times can be
     * ignored.
     * 
     * @param other
     * @return whether the time intervals are equals
     */
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == this.getClass()) {
            TimeList other = (TimeList) obj;
            return timeList.size() == other.timeList.size() && contains(other);
        }
        return false;
    }

    @Override
	public int hashCode() {
		return 31 + ((timeList == null) ? 0 : timeList.hashCode());
	}


    /**
     * Minimal sanity check
     * 
     * @return whether arg1 != arg2 != ... != argN
     */
    public boolean isSane() {
    	boolean sane = true;
    	for(int i = 0; i < timeList.size() - 1; i++) {
    		if (timeList.get(i).equals(timeList.get(i+1))) {
    			sane = false;
    			break;
    		}
    	}    	
    	return sane;
    }
    
    public void sortTimes() {
    	Collections.sort(timeList);
    }

    /**
     * Check whether this time interval contains the time interval
     * 
     * @param other
     * @return whether other is contained by this
     */
	@Override
	public boolean contains(Time other) {
		if (!(other instanceof TimeList)) {
			return false;
		}
		return timeList.containsAll(((TimeList) other).timeList);
	}


	public List<DateTime> getTimeArray() {
		return timeList;
	}

	public void setTimeArray(List<DateTime> times) {
		this.timeList = times;
	}

	/* (non-Javadoc)
	 * @see org.geowebcache.util.wms.Time#getCurrent()
	 */
	@Override
	public DateTime getCurrent() {
		return timeList.get(timeList.size() - 1);
	}

}
