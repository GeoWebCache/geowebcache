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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Preconditions;

@ParametersAreNonnullByDefault
public class RegexParameterFilter extends ParameterFilter {

    private static final long serialVersionUID = -1496940509350980799L;

    public final static String DEFAULT_EXPRESSION = "";

    private String regex = DEFAULT_EXPRESSION;

    private transient Pattern pat = Pattern.compile(regex);

    public RegexParameterFilter() {
        super();
    }
    
    /**
     * Get a {@link Matcher} for this filter's regexp against the given string.
     * @param value
     * @return
     */
    public synchronized Matcher getMatcher(String value) {
        return pat.matcher(value);
    }

    protected RegexParameterFilter readResolve() {
        super.readResolve();
        Preconditions.checkNotNull(regex);
        this.pat = Pattern.compile(regex);
        return this;
    }
    
    @Override
    public String apply(String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return getDefaultValue();
        }
        
        if (getMatcher(str).matches()) {
            return str;
        }

        throw new ParameterException(str + " violates filter for parameter " + getKey());
    }

    @Override
    public @Nullable List<String> getLegalValues() {
        return null;
    }

    /**
     * Checks whether a given parameter value applies to this filter.
     *
     * @param parameterValue
     *            the value to check if applies to this parameter filter
     * @return {@code true} if {@code parameterValue} is valid according to this filter,
     *         {@code false} otherwise
     */
    @Override
    public boolean applies(@Nullable String parameterValue) {
        return getMatcher(parameterValue).matches();
    }

    /**
     * @return the regex
     */
    public String getRegex() {
        return regex;
    }

    /**
     * @param regex
     *            the regex to set.  {@literal null} will be treated as default value.
     */
    public void setRegex(@Nullable String regex) {
        if(regex==null) regex = DEFAULT_EXPRESSION;
        this.regex = regex;
        this.pat = Pattern.compile(this.regex);
    }

    @Override
    public RegexParameterFilter clone() {
        RegexParameterFilter clone = new RegexParameterFilter();
        clone.setDefaultValue(getDefaultValue());
        clone.setKey(getKey());
        clone.regex = regex;
        return clone;
    }
}
