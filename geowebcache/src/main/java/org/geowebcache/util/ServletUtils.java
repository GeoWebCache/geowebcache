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
