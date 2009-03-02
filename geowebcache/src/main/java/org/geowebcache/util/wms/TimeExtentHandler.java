package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.List;

import org.geowebcache.service.ServiceException;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

public class TimeExtentHandler extends ExtentHandler {

    @Override
    protected List<Object> parseValue(String value) {
        List<String> times = splitString(value);
        List<Object> dateTimes = new ArrayList<Object>();
        boolean usesPeriods = false;
        boolean usesList = false;
        for (String t : times) {
            if (t.contains("/")) {
                List<String> parsedTimes = getPeriodExtents(t);
                if (parsedTimes.size() != 3) {
                    throw new IllegalArgumentException("Could not parse time period!");
                }
                Interval interval = new Interval(new DateTime(parsedTimes.get(0)), 
                        new DateTime(parsedTimes.get(1)));
                Period period = new Period(parsedTimes.get(2));
                DateTime now = interval.getStart();
                DateTime end = interval.getEnd();
                while (now.isBefore(end) || now.equals(end)) {
                    // TODO: Use interval start and end instants (plus period?) 
                    // to figure out how to format the date
                    dateTimes.add(new DateTimeWrapper(now.toString(), now));
                    now = now.plus(period);
                }
                usesPeriods = true;
            } else {
                dateTimes.add(new DateTimeWrapper(t, new DateTime(t)));
                usesList = true;
            }
        }
        if (usesPeriods && usesList) {
            throw new IllegalArgumentException("Can not mix periods and list values!");
        }

        return dateTimes;
    }

    @Override
    protected Object getNearestValue(Object obj, List<Object> extent) {
        Long minSize = Long.MAX_VALUE;
        DateTimeWrapper time = (DateTimeWrapper) obj;
        DateTimeWrapper nearestTime = time;
        for (Object ext : extent) {
            DateTimeWrapper e = (DateTimeWrapper) ext;
            Long interval = Math.abs(e.getDateTime().getMillis() - time.getDateTime().getMillis());
            if (interval < minSize) {
                minSize = interval;
                nearestTime = e;
            }
        }
        return nearestTime;
    }

    public static String getCurrentValue(List<Object> extent) throws ServiceException {
        Long minSize = Long.MAX_VALUE;
        DateTimeWrapper nearestTime = new DateTimeWrapper("", new DateTime());
        DateTimeWrapper now = nearestTime;
        for (Object obj : extent) {
            if (!(obj instanceof DateTimeWrapper)) {
                throw new ServiceException("Could not find current value. Extent is not a list of DateTimeWrappers!");
            }
            DateTimeWrapper e = (DateTimeWrapper) obj;
            Long interval;
            if (e.getDateTime().isBefore(now.getDateTime())) {
                interval = e.getDateTime().getMillis() - now.getDateTime().getMillis();
            } else {
                interval = now.getDateTime().getMillis() - e.getDateTime().getMillis();
            }
            if (interval < minSize) {
                minSize = interval;
                nearestTime = e;
            }
        }
        return nearestTime.getValue();
    }

    public static class DateTimeWrapper {
        
        private String value;
        private DateTime dateTime;
        
        public DateTimeWrapper(String value, DateTime dateTime) {
            this.value = value;
            this.dateTime = dateTime;
        }
        
        public String getValue() {
            return value;
        }
        public DateTime getDateTime() {
            return dateTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DateTimeWrapper)) {
                return false;
            }
            DateTimeWrapper other = (DateTimeWrapper) obj;
            if (this.dateTime != null && other.dateTime != null) {
                return this.dateTime.equals(other.dateTime);
            }
            return super.equals(obj);
        }

        @Override
        public String toString() {
            return getValue();
        }
    }
}
