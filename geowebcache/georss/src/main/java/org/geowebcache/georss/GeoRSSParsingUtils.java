package org.geowebcache.georss;

import java.util.Date;

import org.geotools.feature.type.DateUtil;

class GeoRSSParsingUtils {

    public static Date date(final String dateTimeStr) {
        Date dateTime = DateUtil.deserializeDateTime(dateTimeStr);
        return dateTime;
    }
}
