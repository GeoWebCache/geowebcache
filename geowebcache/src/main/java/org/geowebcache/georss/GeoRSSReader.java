package org.geowebcache.georss;

import java.io.IOException;

interface GeoRSSReader {

    public Entry nextEntry() throws IOException;
}
