package org.geowebcache.service.kml;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.layer.wms.BBOX;
import org.geowebcache.layer.wms.TileLayer;

public class KMLService {

    /*	
     <?xml version="1.0" encoding="UTF-8"?>
     <kml xmlns="http://earth.google.com/kml/2.1">
     <NetworkLink>
     <name>SuperOverlay: MV DOQQ</name>
     <Region>
     <LatLonAltBox>
     <north>37.44140625</north>
     <south>37.265625</south>
     <east>-121.9921875</east>
     <west>-122.16796875</west>
     </LatLonAltBox>
     <Lod>
     <minLodPixels>128</minLodPixels>
     <maxLodPixels>-1</maxLodPixels>
     </Lod>
     </Region>
     <Link>
     <href>http://mw1.google.com/mw-earth-vectordb/kml-samples/mv-070501/1.kml</href>
     <viewRefreshMode>onRegion</viewRefreshMode>
     </Link>
     </NetworkLink>
     </kml>
     */
    
    public static void getOverlayKML(TileLayer layer, HttpServletResponse response){
        
    }
    
    private static void SuperOverlay(TileLayer layer, HttpServletResponse response) {

    }
    
    private static void Overlay(TileLayer layer, HttpServletResponse response) {

    }
}
