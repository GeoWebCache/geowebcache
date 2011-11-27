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

import org.apache.commons.lang.ObjectUtils;

public abstract class ParameterFilter {
    public String key;

    public String defaultValue;

    public ParameterFilter() {
        // Empty for XStream
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Checks whether a given parameter value applies to this filter
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

    public String apply(String str) throws ParameterException {
        return null;
    }

    /**
     * @return null if the legal values cannot be enumerated
     */
    public abstract List<String> getLegalValues();

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }

        ParameterFilter p = (ParameterFilter) o;
        if (!ObjectUtils.equals(key, p.getKey())) {
            return false;
        }
        if (!ObjectUtils.equals(defaultValue, p.getDefaultValue())) {
            return false;
        }
        if (!ObjectUtils.equals(getLegalValues(), p.getLegalValues())) {
            return false;
        }
        return true;
    }
}
