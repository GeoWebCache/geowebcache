package org.geowebcache.util;

import java.util.Iterator;

/**
 * Consider replacing this class with org.apache.common.lang.StringUtils - adding a dependency but
 * removing this class.
 */
public class StringUtils {

    public static String join(Iterable<? extends Object> pColl, String separator) {
        Iterator<? extends Object> oIter;
        if (pColl == null || (!(oIter = pColl.iterator()).hasNext()))
            return "";
        StringBuilder oBuilder = new StringBuilder(String.valueOf(oIter.next()));
        while (oIter.hasNext())
            oBuilder.append(separator).append(oIter.next());
        return oBuilder.toString();
    }

}
