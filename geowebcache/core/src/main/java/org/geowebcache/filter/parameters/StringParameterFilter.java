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

import java.util.Iterator;
import java.util.List;

public class StringParameterFilter extends ParameterFilter {

    List<String> values;
        
    public String apply(String str) throws ParameterException {
        if(str == null || str.length() == 0) {
            return "";
        }
        
        Iterator<String> iter = values.iterator();
        while(iter.hasNext()) {
            if(iter.next().equals(str)) {
                return str;
            }
        }
        
        throw new ParameterException(str + " violates filter for parameter " + key);
    }

    public List<String> getLegalValues() {
        return values;
    }
}
