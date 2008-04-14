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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ServletUtils {
    
    /**
     * Case insensitive lookup
     * 
     * @param map
     * @param key
     * @return
     */
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
    
    /**
     * Case insensitive lookup for a couple of strings,
     * drops everything else
     * 
     * @param map
     * @param keys
     * @return
     */
    public static Map selectedStringsFromMap(Map map, String[] keys) {
        HashMap retMap = new HashMap();
        for(int i=0; i<keys.length; i++) {
            retMap.put(keys[i], stringFromMap(map,keys[i]));
        }
        
        return retMap;
    }
}
