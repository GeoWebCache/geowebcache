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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StringParameterFilter extends ParameterFilter {

    private static final long serialVersionUID = 7383381085250203901L;

    private List<String> values;

    public StringParameterFilter() {
        readResolve();
    }

    private StringParameterFilter readResolve() {
        if (values == null) {
            values = new ArrayList<String>(2);
        }
        return this;
    }

    @Override
    public String apply(String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return "";
        }

        Iterator<String> iter = values.iterator();
        while (iter.hasNext()) {
            if (iter.next().equals(str)) {
                return str;
            }
        }

        throw new ParameterException(str + " violates filter for parameter " + getKey());
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public List<String> getLegalValues() {
        return values;
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
    public boolean applies(String parameterValue) {
        return getLegalValues().contains(parameterValue);
    }

    @Override
    public StringParameterFilter clone() {
        StringParameterFilter clone = new StringParameterFilter();
        clone.setDefaultValue(getDefaultValue());
        clone.setKey(getKey());
        if (values != null) {
            clone.values = new ArrayList<String>(values);
        }
        return clone;
    }
}
