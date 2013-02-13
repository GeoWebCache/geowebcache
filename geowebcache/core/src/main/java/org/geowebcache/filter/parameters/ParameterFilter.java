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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.filter.parameters;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * A filter for a WMS parameter that ensure that it fits within a finite set of defined values.
 *
 */
public abstract class ParameterFilter implements Serializable, Cloneable {

    private static final long serialVersionUID = -531248230951783132L;

    private String key;

    private String defaultValue;

    public ParameterFilter() {
        // Empty for XStream
    }

    /**
     * Get the key of the parameter to filter.
     * @return
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the default value to use if the parameter is not specified.
     * @return
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public abstract ParameterFilter clone();
    
    /**
     * Checks whether a given parameter value applies to this filter.
     * 
     * Calls {@link #apply(String)} and checks for {@link ParameterException}.  Subclasses should 
     * override if a more efficient check is available.
     * 
     * @param parameterValue
     *            the value to check if applies to this parameter filter
     * @return {@code true} if {@code parameterValue} is valid according to this filter,
     *         {@code false} otherwise
     */
    public boolean applies(String parameterValue) {
        try {
            apply(parameterValue);
        } catch (ParameterException e) {
            return false;
        }
        return true;
    }

    /**
     * Apply the filter to the specified parameter value.
     * @param str the value of the parameter to filter
     * @return one of the legal values
     * @throws ParameterException if the parameter value could not be reduced to one of the
     *          legal values.
     */
    public String apply(String str) throws ParameterException {
        return null; // TODO Shouldn't this method be abstract?
    }

    /**
     * @return null if the legal values cannot be enumerated
     */
    public abstract List<String> getLegalValues();

    /**
     * @param key
     *            the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @param defaultValue
     *            the defaultValue to set
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

}
