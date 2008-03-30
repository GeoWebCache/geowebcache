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
package org.geowebcache.service.kml;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.layer.wms.WMSLayer;

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
    
    public static void getOverlayKML(WMSLayer layer, HttpServletResponse response){
        
    }
    
    private static void SuperOverlay(WMSLayer layer, HttpServletResponse response) {

    }
    
    private static void Overlay(WMSLayer layer, HttpServletResponse response) {

    }
}
