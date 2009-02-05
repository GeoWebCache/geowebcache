package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.List;

public class NumericExtentHandler extends ExtentHandler {

    @Override
    protected List<Object> parseValue(String value) {
        List<String> values = splitString(value);
        List<Object> parsedValues = new ArrayList<Object>();
        boolean usesPeriods = false;
        boolean usesList = false;
        for (String val : values) {
            if (val.contains("/")) {
                List<String> parsedTimes = getPeriodExtents(val);
                if (parsedTimes.size() != 3) {
                    throw new IllegalArgumentException("Could not parse time period!");
                }
                Float now = Float.parseFloat(parsedTimes.get(0));
                Float end = Float.parseFloat(parsedTimes.get(1));
                Float period = Float.parseFloat(parsedTimes.get(2));
                
                while (now <= end) {
                    parsedValues.add(now);
                    now += period;
                }
                
                usesPeriods = true;
            } else {
                parsedValues.add(Float.parseFloat(val));
                usesList = true;
            }
        }
        if (usesPeriods && usesList) {
            throw new IllegalArgumentException("Can not mix periods and list values!");
        }

        return parsedValues;
    }

    @Override
    protected Object getNearestValue(Object obj, List<Object> extent) {
        Float minSize = Float.MAX_VALUE;
        Float val = (Float) obj;
        Float nearestValue = val;
        for (Object ext : extent) {
            Float e = (Float) ext;
            Float interval = Math.abs(e - val);
            if (interval < minSize) {
                minSize = interval;
                nearestValue = e;
            }
        }
        return nearestValue;
    }
}
