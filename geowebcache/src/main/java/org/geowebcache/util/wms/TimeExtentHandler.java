package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.List;

import org.geowebcache.service.ServiceException;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class TimeExtentHandler extends ExtentHandler {

    
    private static DateTimeFormatter fmt = ISODateTimeFormat.dateHourMinuteSecond();

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
                    dateTimes.add(now);
                    now = now.plus(period);
                }
                usesPeriods = true;
            } else {
                dateTimes.add(new DateTime(t));
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
        DateTime time = (DateTime) obj;
        DateTime nearestTime = time;
        for (Object ext : extent) {
            DateTime e = (DateTime) ext;
            Long interval = Math.abs(e.getMillis() - time.getMillis());
            if (interval < minSize) {
                minSize = interval;
                nearestTime = e;
            }
        }
        return nearestTime;
    }

    // TODO use string buffer
    @Override
    protected String listToString(List<Object> times) {
        String string = "";
        for (Object time : times) {
            if (time instanceof DateTime) {
                string += ((DateTime) time).toString(fmt);
            }
        }
        return string;
    }

    public static String getCurrentValue(List<Object> extent) throws ServiceException {
        Long minSize = Long.MAX_VALUE;
        DateTime nearestTime = new DateTime();
        DateTime now = nearestTime;
        for (Object obj : extent) {
            if (!(obj instanceof DateTime)) {
                throw new ServiceException("");
            }
            DateTime e = (DateTime) obj;
            Long interval;
            if (e.isBefore(now)) {
                interval = e.getMillis() - now.getMillis();
            } else {
                interval = now.getMillis() - e.getMillis();
            }
            if (interval < minSize) {
                minSize = interval;
                nearestTime = e;
            }
        }
        return nearestTime.toString(fmt);
    }

   
}
