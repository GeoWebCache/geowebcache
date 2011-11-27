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

import org.apache.commons.lang.ObjectUtils;

public class RegexParameterFilter extends ParameterFilter {
    public String regex;

    transient Pattern pat;

    public RegexParameterFilter() {
        super();
    }

    public synchronized Matcher getMatcher(String value) {
        if (pat == null) {
            pat = Pattern.compile(regex);
        }

        return pat.matcher(value);
    }

    public String apply(String str) throws ParameterException {
        if (getMatcher(str).matches()) {
            return str;
        }

        throw new ParameterException(str + " violates filter for parameter " + key);
    }

    public List<String> getLegalValues() {
        return null;
    }

    @Override
    public boolean applies(String parameterValue) {
        return getMatcher(parameterValue).matches();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }
        return ObjectUtils.equals(pat, ((RegexParameterFilter) o).pat);
    }
}
