package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.geowebcache.service.ServiceException;
import org.geowebcache.service.wms.WMSParameters;


public abstract class ExtentHandler {

    public ExtentHandler() {
    }

    /**
     * Gets the value to be used in cache key and to request to back end WMS.
     * The implementation of this function address the following properties:
     * <ul>
     *     <li>value is correctly formatted</li> 
     *     <li>dimension supports default value</li> 
     *     <li>dimension supports nearest value</li> 
     *     <li>dimension supports multiple values</li>
     *     <li>dimension extent contains value(s)</li> 
     *     <li>if time dimension, supports current as value</li> 
     * </ul>
     * 
     * @param dimension    container for properties used when parsing value
     * @param value        the parameter value to be "parsed"
     * @return             the "parsed" value
     * @throws ServiceException 
     */
    public String getValue(Dimension dimension, String value) throws ServiceException {
        String parsedValue;
        List<Object> extent = parseValue(dimension.getExtent());
        if (value == null || value.length() == 0) {
            String defaultValue = dimension.getDefaultValue();
            if (defaultValue == null || defaultValue.length() == 0) {
                throw new ServiceException("Parameter is null and dimension " + dimension.getName() +" does not specify a default value");
            } else {
                parsedValue = defaultValue;
            }
        } else if (WMSParameters.TIME_PARAM.equals(dimension.getName()) && "current".equalsIgnoreCase(value)) {
            if (dimension.supportsCurrent()) {
                parsedValue = TimeExtentHandler.getCurrentValue(extent);
            } else {
                throw new ServiceException("Time dimension does not support \"Current\" as parameter value");
            }
        } else {
            List<Object> parsedValues = parseValue(value);
            if (extent == null || parsedValues == null) {
                throw new ServiceException("Could not parse value or extent");
            } 
            if (parsedValues.size() > 1 && !dimension.isMultipleValues()) {
                throw new ServiceException("Dimension does not allow multiple values");
            }
            if (extent.containsAll(parsedValues)) {
                parsedValue = listToString(extent, parsedValues);
            } else if (dimension.supportsNearestValue()) {
                List<Object> nearestTimes = new ArrayList<Object>(parsedValues.size());
                for (Object obj : parsedValues) {
                    Object nearestValue = getNearestValue(obj, extent);
                    nearestTimes.add(nearestValue);
                }
                parsedValue = listToString(extent, nearestTimes);
            } else {
                throw new ServiceException("Dimension does not contain one or more values and does not support nearest value");
            }
        }
        return parsedValue;
    }

    /**
     * Splits the string value of a parameter on "," into a list of trimmed 
     * strings. Allowed formats of the value is:
     * <ul>
     *  <li>value</li>
     *  <li>value1, value2, value3...</li>
     *  <li>min/max/res</li>
     *  <li>min1/max1/res1, min2/max2/res2...</li>
     * </ul>
     * @param value
     * @return
     */
    protected static List<String> splitString(String value) {
        List<String> timeList = new ArrayList<String>();
        if (value != null) {
            String[] ts = value.split(",");
            for (int j = 0; j < ts.length; j++) {
                ts[j] = ts[j].trim();
            }
            timeList.addAll(Arrays.asList(ts));
        }
        return timeList;
    }

    /**
     * Splits the string value of a parameter string on "/" into list of strings
     * If value argument is null an empty list is returned
     * If no "/" is found (a single value) a one element list is returned
     * Else if other than two "/" is found an empty list is returned
     * 
     * Use splitString(String value) prior to trying to parse with this method
     * 
     * @param value
     * @return
     */
    protected static List<String> getPeriodExtents(String value) {
        if (value == null || value.contains(",")) {
            throw new IllegalArgumentException("Parameter value is null or contains a comma");
        }

        List<String> valueList = new ArrayList<String>();
        String[] ts = value.split("/");
        for (int j = 0; j < ts.length; j++) {
            ts[j] = ts[j].trim();
        }
        valueList.addAll(Arrays.asList(ts));
        if (valueList.size() == 1 || valueList.size() == 3) {
            return valueList; 
        } else {
            throw new IllegalArgumentException("Could not parse value!");
        }
    }

    /**
     * Help function to get the text representation of the request.
     * 
     * Always be sure to get the value from the extent - values may be parsed and
     * valid but not formatted in request as in extent. 
     * I.e. 2009-01-01T00:00:00Z = 2009-01-01T01:00:00+01:00 
     *  
     * @param extent - the extent to get the values from
     * @param values - the values to be expressed as text 
     * @return a text representation of the values
     */
    protected String listToString(List<Object> extent, List<Object> values) {
        StringBuffer value = new StringBuffer();
        Iterator<Object> valuesIter = values.iterator();
        while (valuesIter.hasNext()) {
            Object val = valuesIter.next();
            int i = extent.indexOf(val);
            value.append(extent.get(i).toString());
            if (valuesIter.hasNext()) {
                value.append(",");
            }
        }
        return value.toString();
    }

    protected abstract Object getNearestValue(Object obj, List<Object> extent);

    protected abstract List<Object> parseValue(String value);
}
