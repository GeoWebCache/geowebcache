package org.geowebcache.util.wms;


/**
 * Base class for Dimensions
 * 
 *  
 * @author pereng
 *
 */
public class Dimension {
	
	public static final String DIMENSION_TIME = "time";
	public static final String DIMENSION_ELEVATION = "elevation";
	
	protected String name;
	protected String units;
	protected String unitSymbol;
	protected String defaultValue;
	protected boolean multipleValues;
	protected boolean nearestValue;
	protected boolean current;
	protected String extent;
	protected ExtentHandler extentHandler;
	
	public Dimension(String name, ExtentHandler extentHandler) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Dimension name can not be null!");
		}
		this.name = name;
		this.extentHandler = extentHandler;
	}
	
	public Dimension(String name, String units, String extent, ExtentHandler extentHandler) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Dimension name can not be null!");
		}
		// According to spec 1.3.0 <Dimension units=""/> is valid
		if (units == null) {
			throw new IllegalArgumentException("Dimension units can not be null!");
		}
		this.name = name;
		this.units = units;
		this.extent = extent;
		this.extentHandler = extentHandler;
	}
	
	/**
	 * Simple syntax check. 
	 * <h2>Table C.2 � Syntax for listing one or more extent values</h2>
	 * <table>
	 * 	<tr><th>Syntax</th><th>Meaning</th></tr>
	 * 	<tr><td>value</td><td>A single value.</td></tr>
	 * 	<tr><td>value1,value2,value3,...</td><td>A list of multiple values.</td></tr>
	 * 	<tr><td>min/max/resolution</td><td>An interval defined by its lower and upper bounds and its resolution.</td></tr>
	 * 	<tr><td>min1/max1/res1,min2/max2/res2,...</td><td>A list of multiple intervals.</td></tr>
	 * </table>
	 * 
	 * Whitespace is allowed following commas in a list in a <Dimension> element of service metadata. 
	 * 
	 *  
	 * @param value
	 * @return
	 */
	public boolean isValid(String value) {
		boolean valid = true;
		if (value == null && defaultValue == null) {
			valid = false;
		}
		if (value != null) {
			String[] values = value.split(",");
			if (values.length > 1 && !multipleValues) {
				valid = false;
			}
			for (String val : values) {
				String[] intervalParts = val.split("/");
				if (!(intervalParts.length == 1 || intervalParts.length == 3)) {
					valid = false;
				}
			}
		}
		if (!extentHandler.isValid(this, value)) {
			valid = false;
		}
		return valid;
	}
	
	public Object getValue() {
		return extentHandler.getValue(extent);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUnits() {
		return units;
	}

	public void setUnits(String units) {
		this.units = units;
	}

	public String getUnitSymbol() {
		return unitSymbol;
	}

	public void setUnitSymbol(String unitSymbol) {
		this.unitSymbol = unitSymbol;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public boolean isMultipleValues() {
		return multipleValues;
	}

	public void setMultipleValues(boolean multipleValues) {
		this.multipleValues = multipleValues;
	}

	public boolean supportsNearestValue() {
		return nearestValue;
	}

	public void setNearestValue(boolean nearestValues) {
		this.nearestValue = nearestValues;
	}

	public boolean supportsCurrent() {
		return current;
	}

	public void setCurrent(boolean current) {
		this.current = current;
	}

	public static Dimension getDimension(String dimension) {
		return null;
	}

	public String getExtent() {
		return extent;
	}

	public void setExtent(String extent) {
		this.extent = extent;
	}
	
}
