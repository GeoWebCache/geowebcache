package org.geowebcache.util.wms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class TimeExtentHandler extends ExtentHandler {

	@Override
	public Object getValue(String value) {
		return createTime(value);
	}

	@Override
	public boolean isValid(Dimension dimension, String value) {
		boolean valid = true;
		
		if (value == null && StringUtils.isEmpty(dimension.getDefaultValue())) {
			valid = false;
		} else if ("current".equalsIgnoreCase(value) && !dimension.supportsCurrent()) {
			valid = false;
		} else {
			Time time = createTime(value);
			Time extent = createTime(dimension.getExtent());
			if (extent == null || time == null) {
				valid = false;
			} else if (!extent.contains(time) && !dimension.supportsNearestValue()) {
				valid = false;
			}
		}

		return true;
	}

	/**
	 * Splits the time parameter string on "," into a list of trimed strings
	 * Allowed formats of the time parameter value is:
	 * <ul>
	 * 	<li>value</li>
	 * 	<li>value1, value2, value3...</li>
	 * 	<li>min/max/res</li>
	 * 	<li>min1/max1/res1, min2/max2/res2...</li>
	 * </ul>
	 * @param time
	 * @return
	 */
	private static List<String> splitTimeString(String time) {
		List<String> timeList = new ArrayList<String>();
		if (time != null) {
			String[] ts = time.split(",");
			for (int j = 0; j < ts.length; j++) {
				ts[j] = ts[j].trim();
			}
			timeList.addAll(Arrays.asList(ts));
		}
		return timeList;
	}
	
	/**
	 * Splits the time parameter string on "/" into list of strings
	 * If time parameter is null an empty list is returned
	 * If no "/" is found (a single value) a one element list is returned
	 * Else if other than two "/" is found an empty list is returned
	 * 
	 * Use splitTimeString(String time) prior to trying to parse with this method
	 * 
	 * @param time
	 * @return
	 */
	private static List<String> getPeriodExtents(String time) {
		if (time == null || time.contains(",")) {
			return Collections.emptyList();
			// throw new IllegalArgumentException("Parameter time is null or contains a comma");
		}
		
		List<String> timeList = new ArrayList<String>();
		if (time != null) {
			String[] ts = time.split("/");
			for (int j = 0; j < ts.length; j++) {
				ts[j] = ts[j].trim();
			}
			timeList.addAll(Arrays.asList(ts));
		}
		if (timeList.size() == 1 || timeList.size() == 3) {
			return timeList; 
		} else {
			return Collections.emptyList();
		}
	}
	
	public static Time createTime(String time) {
		List<String> times = splitTimeString(time);
		List<String> parsedTimes = new ArrayList<String>();
		for (String t : times) {
			parsedTimes = getPeriodExtents(t);
		}
		
		return null;
	}

}
