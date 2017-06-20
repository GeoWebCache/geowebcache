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
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * A filter for a WMS parameter that ensure that it fits within a finite set of defined values.
 *
 */
@ParametersAreNonnullByDefault
public abstract class ParameterFilter implements Serializable, Cloneable {

    private static final long serialVersionUID = -531248230951783132L;

    private String key;

    private String defaultValue = "";

    /**
     * For XStream
     */
    public ParameterFilter() {
        // Empty for XStream
    }

    /**
     * @param key
     * @param defaultValue
     */
    public ParameterFilter(String key, @Nullable String defaultValue) {
        Preconditions.checkNotNull(key);
        Preconditions.checkArgument(!key.isEmpty(), "Parameter key must not be empty.");
        this.key = key;
        this.defaultValue = defaultValue;
    }
    
    /**
     * @param key
     * @param defaultValue
     */
    public ParameterFilter(String key) {
        this(key, "");
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
        if(defaultValue==null) return "";
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
    public boolean applies(@Nullable String parameterValue) {
        try {
            apply(parameterValue);
        } catch (ParameterException e) {
            return false;
        }
        return true;
    }

    /**
     * Apply the filter to the specified parameter value.
     * @param str the value of the parameter to filter. {@literal null} if the parameter was unspecified.
     * @return one of the legal values
     * @throws ParameterException if the parameter value could not be reduced to one of the
     *          legal values.
     */
    public abstract String apply(@Nullable String str) throws ParameterException;

    /**
     * @return null if the legal values cannot be enumerated
     */
    public abstract @Nullable List<String> getLegalValues();

    /**
     * The key of the parameter to filter.  
     * 
     * @param key
     *            the key to set
     */
    public void setKey(String key) {
        Preconditions.checkNotNull(key);
        Preconditions.checkArgument(!key.isEmpty(), "ParameterFilter key must be non-empty");
        Preconditions.checkState(this.key==null, "The key for this ParameterFilter has already been set");
        this.key = key;
    }
    
    /**
     * @param defaultValue
     *            the defaultValue to set
     */
    public void setDefaultValue(@Nullable String defaultValue) {
        if(defaultValue==null) defaultValue ="";
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

    protected ParameterFilter readResolve() {
        // Make sure XStream found a Key 
        Preconditions.checkNotNull(key);
        Preconditions.checkState(!key.isEmpty(), "ParameterFilter key must be non-empty");
        if(defaultValue==null) defaultValue ="";
        return this;
    }
    
    /**
     * Is the given value exactly a value that could be produced by the filter.
     * @param value
     * @return
     * @throws ParameterException
     */
    public boolean isFilteredValue(final String value) {
        if(Objects.equal(value, this.getDefaultValue())) {
            return true;
        }
        if(Optional.ofNullable(this.getLegalValues()).map(values->values.contains(value)).orElse(false)) {
            return true;
        }
        try {
            return Objects.equal(value, this.apply(value));
        } catch (ParameterException ex) {
            return false;
        }
    }
}
