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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.util;

import java.util.Iterator;
import java.util.Map;

public class ServletUtils {
    public static String stringFromMap(Map map, String key) {
        String[] strArray = (String[]) map.get(key);

        if (strArray != null) {
            return strArray[0];
        } else {
            // In case there is a case mismatch
            Iterator iter = map.keySet().iterator();
            while (iter.hasNext()) {
                Object objKey = iter.next();
                if (objKey.getClass() == String.class) {
                    String strKey = (String) objKey;
                    if (strKey.compareToIgnoreCase(key) == 0) {
                        strArray = (String[]) map.get(strKey);
                        return strArray[0];
                    }
                }
            }
        }
        return null;
    }
}
