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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.service.ve.VEConverter;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class KMLService extends Service {
    private static Log log = LogFactory
    .getLog(org.geowebcache.service.kml.KMLService.class);
	
	public static final String SERVICE_KML = "/kml";
	public static final String EXTENSION_KML = ".kml";
	public static final int EXTENSION_IMAGE_LENGTH = 4;
	
    public KMLService() {
		super(SERVICE_KML);
	}
    
    /**
     * Parses the pathinfo part of an HttpServletRequest into
     * the three components it is (hopefully) made up of.
     * 
     * TODO deal better with malformed input
     * 
     * @param pathInfo
     * @return {layername, tilekey, extension}
     */
    private String[] parseRequest(String pathInfo) {
    	String[] retStrs = new String[3];
    	
    	pathInfo = new String(pathInfo);
    	int startOffset = SERVICE_KML.length() + 1;
    	int endOffset = pathInfo.length() - 4;
    	retStrs[2] = new String(pathInfo.substring(endOffset, pathInfo.length()));
    	String midSection = new String(pathInfo.substring(startOffset, endOffset));
    	
    	// Midsection can either be "layername/quadkey" or "layername"
    	String[] midParts = midSection.split("/"); 
    	
    	// Gather the rest of the results
    	retStrs[0] = midParts[0];
    	if(midParts.length > 1) {
    		retStrs[1] = midParts[1];
    	} else {
    		retStrs[1] = "";
    	}
    	
    	return retStrs;
    }
    
    public ServiceRequest getServiceRequest(HttpServletRequest request)  {
    	String[] parsed = parseRequest(request.getPathInfo());
    	
    	// If it does not end in .kml it is a tile request
    	int type = ServiceRequest.SERVICE_REQUEST_TILE;
    	
    	if(parsed[2].equalsIgnoreCase(EXTENSION_KML)){
    		type = ServiceRequest.SERVICE_REQUEST_DIRECT;
    	}
    	
    	return new ServiceRequest(parsed[0], type);
    }
    
    public void handleRequest(TileLayer tileLayer, 
    		HttpServletRequest request, HttpServletResponse response) {
    	
    	// Have to parse it again... that's a bit silly
    	// TODO extend ServiceRequest object
    	String[] parsed = parseRequest(request.getPathInfo());
    	
    	String urlStr = request.getRequestURL().toString();
    	int endOffset = urlStr.length()
    		 - parsed[1].length() - parsed[2].length();
    	urlStr = new String(urlStr.substring(0, endOffset));
    	if(parsed[1].length() == 0) {
    		// There's no room for an quadkey -> super overlay
    		log.debug("Request for super overlay for "
    				+parsed[0]+" received");
    		handleSuperOverlay(tileLayer, urlStr, response);
    	} else {
    		log.debug("Request for overlay for "
    				+parsed[0]+" received, key " + parsed[1]);
    		handleOverlay(tileLayer, parsed[1], urlStr, response);
    	}
    }
    
    public TileRequest getTileRequest(TileLayer tileLayer,
            HttpServletRequest request) {
        Map params = request.getParameterMap();
        String strQuadKey = ServletUtils.stringFromMap(params, "quadkey");
        String mimeType = ServletUtils.stringFromMap(params, "format");

        int[] gridLoc = VEConverter.convert(strQuadKey);

        SRS srs = new SRS(4326);
        return new TileRequest(gridLoc, mimeType, srs);
    }
    
    private static void handleSuperOverlay(TileLayer layer, 
    	String urlStr, HttpServletResponse response) {
    	SRS srs = new SRS(4326);
    	int srsIdx = layer.getSRSIndex(srs);
    	
    	String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        +"<kml xmlns=\"http://earth.google.com/kml/2.1\">"
        +"<NetworkLink>"
        +"<name>SuperOverlay:"+layer.getName()+"</name>"
        +"<Region>"
        +layer.getBounds(srsIdx).toKML()
        +"<Lod>"
        +"<minLodPixels>128</minLodPixels>"
        +"<maxLodPixels>-1</maxLodPixels>"
        +"</Lod>"
        +"</Region>"
        +"<Link>"
        +"<href>"+urlStr+"/"+gridLocString(layer,srsIdx)+".kml</href>"
        +"<viewRefreshMode>onRegion</viewRefreshMode>"
        +"</Link>"
        +"</NetworkLink>"
        +"</kml>";
    	log.info(xml);
    	writeXml(xml, response);
    }
    
    private static String gridLocString(TileLayer tileLayer, int srsIdx) {
    	int[] gridLoc = tileLayer.getGridLocForBounds(srsIdx, tileLayer.getBounds(srsIdx));
    	return gridLocString(gridLoc);
    }
    
    private static String gridLocString(int[] gridLoc) {
    	return "x"+gridLoc[0]+"y"+gridLoc[1]+"z"+gridLoc[2];
    }
    
    private static int[] parseGridLocString(String key) {
    	// format should be x<x>y<y>z<z>
    	// TODO fail gracefully when it is not
    	int[] ret = new int[3];
    	int yloc = key.indexOf("y");
    	int zloc = key.indexOf("z");
    	
    	ret[0] = Integer.parseInt(key.substring(1,yloc));
    	ret[1] = Integer.parseInt(key.substring(yloc+1,zloc));
    	ret[2] = Integer.parseInt(key.substring(zloc+1,key.length()));
    	
    	return ret;
    }
    
    /**
     * 1) Header
     * 2) Network links
     * 3) Overlay
     * 4) Footer
     * 
     * @param tileLayer
     * @param key
     * @param urlStr
     * @param response
     */
    private static void handleOverlay(TileLayer tileLayer, String key, String urlStr, HttpServletResponse response) {
    	int[] gridLoc = parseGridLocString(key);
    	
    	SRS srs = new SRS(4326);
    	int srsIdx = tileLayer.getSRSIndex(srs);
    	
    	BBOX bbox = tileLayer.getBboxForGridLoc(srsIdx, gridLoc);
    	
    	log.info("handle overlay");
    }
    
    private static String createNetworkLinkElement(TileLayer layer, int[] gridLoc, BBOX bbox, String urlStr) {
        String gridLocString = gridLocString(gridLoc);
    	String xml = "<NetworkLink>"
        +"<name>"+layer.getName() + gridLocString+"</name>"
        +"<Region>"
        +"<Lod><minLodPixels>128</minLodPixels><maxLodPixels>-1</maxLodPixels></Lod>"
        +bbox.toKML()
        +"</Region>"
        +"<Link>"
          +"<href>"+urlStr + "/" + gridLocString+".kml</href>"
          +"<viewRefreshMode>onRegion</viewRefreshMode>"
        +"</Link>"
    	+"</NetworkLink>";
    	
    	return xml;
    }
    
    private static String createGroundOverLayElement(int[] gridLoc, BBOX bbox, String urlStr){
    	String xml = "<GroundOverlay>"
        +"<drawOrder>5</drawOrder>"
        +"<Icon>"
          +"<href>"+ urlStr + "/" + gridLocString(gridLoc) +".png</href>"
        +"</Icon>"
          +bbox.toKML() 
      +"</GroundOverlay>";
    	
    	return xml;
    }
    
    private static void writeXml(String xml, HttpServletResponse response) {
    	try {
    		OutputStream os = response.getOutputStream();
    		os.write(xml.getBytes());
    	} catch(IOException ioe) {
    		// Do nothing...
    	}
    }
    
    
}

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

/*
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://earth.google.com/kml/2.1">
  <Document>
    <Region>
      <Lod>
        <minLodPixels>128</minLodPixels><maxLodPixels>-1</maxLodPixels>
      </Lod>
      <LatLonAltBox>
        <north>37.430419921875</north><south>37.41943359375</south>
        <east>-122.091064453125</east><west>-122.10205078125</west>
      </LatLonAltBox>
    </Region>
    <NetworkLink>
      <name>001120</name>
      <Region>
        <Lod>
          <minLodPixels>128</minLodPixels><maxLodPixels>-1</maxLodPixels>
        </Lod>
        <LatLonAltBox>
          <north>37.430419921875</north><south>37.4249267578125</south>
          <east>-122.0965576171875</east><west>-122.10205078125</west>
        </LatLonAltBox>
      </Region>
      <Link>
        <href>180.kml</href>
        <viewRefreshMode>onRegion</viewRefreshMode>
      </Link>
    </NetworkLink>
    <NetworkLink>
      <name>001121</name>
      <Region>
        <Lod>
          <minLodPixels>128</minLodPixels><maxLodPixels>-1</maxLodPixels>
        </Lod>
        <LatLonAltBox>
          <north>37.430419921875</north><south>37.4249267578125</south>
          <east>-122.091064453125</east><west>-122.0965576171875</west>
        </LatLonAltBox>
      </Region>
      <Link>
        <href>185.kml</href>
        <viewRefreshMode>onRegion</viewRefreshMode>
      </Link>
    </NetworkLink>

    <NetworkLink>
      <name>001123</name>
      <Region>
        <Lod>
          <minLodPixels>128</minLodPixels><maxLodPixels>-1</maxLodPixels>
        </Lod>
        <LatLonAltBox>
          <north>37.4249267578125</north><south>37.41943359375</south>
          <east>-122.091064453125</east><west>-122.0965576171875</west>
        </LatLonAltBox>
      </Region>
      <Link>
        <href>195.kml</href>
        <viewRefreshMode>onRegion</viewRefreshMode>
      </Link>
    </NetworkLink>
    <GroundOverlay>
      <drawOrder>5</drawOrder>
      <Icon>
        <href>179.JPEG</href>
      </Icon>
      <LatLonBox>
        <north>37.430419921875</north><south>37.41943359375</south>
        <east>-122.091064453125</east><west>-122.10205078125</west>
      </LatLonBox>
    </GroundOverlay>
  </Document>
</kml>
*/
