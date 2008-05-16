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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.wms.BBOX;

public class KMLService extends Service {
    private static Log log = LogFactory
            .getLog(org.geowebcache.service.kml.KMLService.class);

    public static final String SERVICE_KML = "kml";

    public static final String EXTENSION_KML = "kml";

    // public static final int EXTENSION_IMAGE_LENGTH = 4;

    public KMLService() {
        super(SERVICE_KML);
    }

    /**
     * Parses the pathinfo part of an HttpServletRequest into the three
     * components it is (hopefully) made up of.
     * 
     * Example 1: /kml/layername.extension
     * Example 2: /kml/layername/tilekey.extension
     * 
     * @param pathInfo
     * @return {layername, tilekey, extension}
     */
    protected static String[] parseRequest(String pathInfo) {
        String[] retStrs = new String[4];

        String[] splitStr = pathInfo.split("/");
        
        // Deal with the extension
        String filename = splitStr[splitStr.length - 1];
        int extOfst = filename.lastIndexOf(".");
        retStrs[2] = filename.substring(extOfst + 1, filename.length());
        
        // If it contains a hint about the format topp:states.png.kml
        int typeExtOfst = filename.lastIndexOf(".", extOfst - 1);
        
        if(typeExtOfst > 0) {
        	retStrs[3] = filename.substring(typeExtOfst + 1, extOfst);
        } else {
        	typeExtOfst = extOfst;
        }

        	
        // Two types of requests
        if(splitStr[splitStr.length - 2].equals(KMLService.SERVICE_KML)) {
            // layername.kml
            retStrs[0] = filename.substring(0,typeExtOfst);
            retStrs[1] = "";
        } else {
            // layername/key.extension
            retStrs[0] = splitStr[splitStr.length - 2];
            retStrs[1] = filename.substring(0,typeExtOfst);
        }
        
        return retStrs;
    }

    public ServiceRequest getServiceRequest(HttpServletRequest request)
            throws ServiceException {
        String[] parsed = null;
        try {
            parsed = parseRequest(request.getPathInfo());
        } catch (Exception e) {
            throw new ServiceException("Unable to parse KML request : "
                    + e.getMessage());
        }

        ServiceRequest servReq = new ServiceRequest(parsed[0], parsed);
       
        // If it does not end in .kml it is a tile request
        if (parsed[2].equalsIgnoreCase(EXTENSION_KML)) {
            servReq.setFlag(true, ServiceRequest.SERVICE_REQUEST_DIRECT);
        }
        return servReq;
    }

    public void handleRequest(TileLayer tileLayer, HttpServletRequest request,
            ServiceRequest servReq, HttpServletResponse response) throws ServiceException {

        String[] parsed = servReq.getData();

        if(tileLayer == null) {
            throw new ServiceException("No layer provided, request parsed to: " + parsed[0]);
        }
        
        String urlStr = request.getRequestURL().toString();
        int endOffset = urlStr.length() - parsed[1].length()
                - parsed[2].length();
        
        // Also remove the second extension and the dot
        if(parsed.length > 3 && parsed[3] != null) {
            endOffset -= parsed[3].length() + 1;
        }
        urlStr = new String(urlStr.substring(0, endOffset - 1));
        
        //TODO this needs to be done more nicely
        boolean isRaster = true;
        if(parsed[3] != null && parsed[3].equalsIgnoreCase("geosearch-kml")) {
            isRaster = false;
        }
        
        
        if (parsed[1].length() == 0) {
            // There's no room for an quadkey -> super overlay
            if(log.isDebugEnabled()) { 
                log.debug("Request for super overlay for " + parsed[0]
                    + " received");
            }
            handleSuperOverlay(tileLayer, urlStr, parsed[3], isRaster, response);
        } else {
            if(log.isDebugEnabled()) { 
                log.debug("Request for overlay for " + parsed[0] 
                  + " received, key " + parsed[1] + ", format hint " + parsed[3]);
            }
                
            handleOverlay(tileLayer, urlStr, parsed[1], parsed[3], isRaster, response);
        }
    }

    public TileRequest getTileRequest(TileLayer tileLayer, ServiceRequest servReq,
            HttpServletRequest request) throws MimeException,ServiceException {
        String[] parsed = parseRequest(request.getPathInfo());

        int[] gridLoc = parseGridLocString(parsed[1]);
        SRS srs = new SRS(4326);

        MimeType mime = MimeType.createFromExtension(parsed[2]);
        return new TileRequest(gridLoc, mime, srs);
    }

    private static void handleSuperOverlay(TileLayer layer, String urlStr,
            String formatExtension, boolean isRaster, HttpServletResponse response) {
        SRS srs = new SRS(4326);
        int srsIdx = layer.getSRSIndex(srs);
        BBOX bbox = layer.getBounds(srsIdx);
        
        if(formatExtension == null) {
        	formatExtension = "";
        } else {
        	formatExtension = "." + formatExtension;
        }
        
        int[] gridLoc = layer.getZoomedOutGridLoc(srsIdx);
        
        String networkLinks = null;
        
        // Check whether we need two tiles for world bounds or not
        if(gridLoc[2] < 0) {
            int[] gridLocWest = {0,0,0};
            int[] gridLocEast = {1,0,0};
            
            BBOX bboxWest = new BBOX(
                    bbox.coords[0], bbox.coords[1], 0.0, bbox.coords[3] );
            BBOX bboxEast = new BBOX(
                    0.0, bbox.coords[1], bbox.coords[2], bbox.coords[3] );
            
            networkLinks = 
                superOverlayNetworLink(
                    layer.getName() + " West", 
                    bboxWest, 
                    urlStr + "/" + gridLocString(gridLocWest) + formatExtension + ".kml")
                    +
                superOverlayNetworLink(
                    layer.getName() + " East", 
                    bboxEast, 
                    urlStr + "/" + gridLocString(gridLocEast) + formatExtension + ".kml");
            
        } else {
            networkLinks = superOverlayNetworLink(
                    layer.getName(), 
                    bbox, 
                    urlStr + "/" + gridLocString(gridLoc) + formatExtension + ".kml");
        }
        
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "\n<kml xmlns=\"http://earth.google.com/kml/2.1\">\n"
                + "\n<Folder>"
                + networkLinks
                + getLookAt(bbox)
                + "\n</Folder>"
                + "\n</kml>\n";

        writeXml(xml, response);
    }

    private static String superOverlayNetworLink(String superString, BBOX bbox, String url) {
        String xml = "\n<NetworkLink><name>SuperOverlay:"+superString+"</name>" 
        + "\n<Region>\n" 
        + bbox.toKML()
        + "\n<Lod><minLodPixels>64</minLodPixels>"
        + "\n<maxLodPixels>-1</maxLodPixels></Lod>"
        + "\n</Region>"
        + "\n<Link><href>"+url+"</href>"
        + "\n<viewRefreshMode>onRegion</viewRefreshMode>" 
        + "\n</Link>"
        + "\n</NetworkLink>";
        
        return xml;
    }
    //private static String gridLocString(TileLayer tileLayer, int srsIdx) {
    //    int[] gridLoc = tileLayer.getZoomedOutGridLoc(srsIdx);
    //    return gridLocString(gridLoc);
    //}

    private static String gridLocString(int[] gridLoc) {
        return "x" + gridLoc[0] + "y" + gridLoc[1] + "z" + gridLoc[2];
    }

    private static int[] parseGridLocString(String key) throws ServiceException {
        // format should be x<x>y<y>z<z>

        int[] ret = new int[3];
        int yloc = key.indexOf("y");
        int zloc = key.indexOf("z");

        try {
            ret[0] = Integer.parseInt(key.substring(1, yloc));
            ret[1] = Integer.parseInt(key.substring(yloc + 1, zloc));
            ret[2] = Integer.parseInt(key.substring(zloc + 1, key.length()));
        } catch (NumberFormatException nfe) {
            throw new ServiceException("Unable to parse " + key);
        } catch (StringIndexOutOfBoundsException sobe) {
        	throw new ServiceException("Unable to parse " + key);
        }
        return ret;
    }

    /**
     * 1) Header 2) Network links 3) Overlay 4) Footer
     * 
     * @param tileLayer
     * @param key
     * @param urlStr
     * @param response
     */
    private static void handleOverlay(TileLayer tileLayer, String urlStr, String key,
            String formatExtension, boolean isRaster, 
            HttpServletResponse response) throws ServiceException {
        int[] gridLoc = parseGridLocString(key);

        SRS srs = new SRS(4326);
        int srsIdx = tileLayer.getSRSIndex(srs);
        BBOX bbox = tileLayer.getBboxForGridLoc(srsIdx, gridLoc);

        // 1) Header
        String xml = createOverlayHeader(bbox);

        // 2) Network links
        int[][] linkGridLocs = tileLayer.getZoomInGridLoc(srsIdx, gridLoc);
        
        if(formatExtension == null) {
        	formatExtension = "" + tileLayer.getDefaultMimeType().getFileExtension();
        }

        for (int i = 0; i < 4; i++) {
            // Only add this link if it is within the bounds
            if (linkGridLocs[i][2] > 0) {
                BBOX linkBbox = tileLayer.getBboxForGridLoc(srsIdx,
                        linkGridLocs[i]);
                xml += createNetworkLinkElement(tileLayer, urlStr, linkGridLocs[i],
                        linkBbox, formatExtension+".kml");
            }
        }

        // 3) Overlay
        if(isRaster) {
            xml += createGroundOverLayElement(gridLoc, urlStr, bbox, formatExtension);
        } else {
            xml += createNetworkLinkElement(tileLayer, urlStr, gridLoc,
                    bbox, formatExtension);
        }

        // 4) Footer
        xml += "</Document>\n</kml>";

        // log.info("handle overlay");
        writeXml(xml, response);
    }

    private static String createOverlayHeader(BBOX bbox) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://earth.google.com/kml/2.1\">\n"
                + "<Document>\n"
                + "<Region>\n"
                + "<Lod><minLodPixels>192</minLodPixels><maxLodPixels>512</maxLodPixels></Lod>\n"
                + bbox.toKML() + "</Region>\n";
    }

    private static String createNetworkLinkElement(TileLayer layer, String urlStr,
            int[] gridLoc, BBOX bbox, String extension) {
        String gridLocString = gridLocString(gridLoc);
        String xml = "\n<NetworkLink>"
                + "\n<name>"
                + layer.getName()
                + " - "
                + gridLocString
                + "</name>"
                + "\n<Region>"
                // Chould technically be 192 to 384, centered around 256, but this creates gaps
                + "\n<Lod><minLodPixels>64</minLodPixels><maxLodPixels>-1</maxLodPixels></Lod>\n"
                //+ "\n<minFadeExtent>128</minFadeExtent>" 
                //+ "\n<maxFadeExtent>256</maxFadeExtent>"
                + bbox.toKML() + "\n</Region>" + "\n<Link>" 
                + "\n<href>"
                + gridLocString + "." + extension
                +"</href>"
                + "\n<viewRefreshMode>onRegion</viewRefreshMode>" + "</Link>"
                + "\n</NetworkLink>\n";

        return xml;
    }

    private static String createGroundOverLayElement(int[] gridLoc, String urlStr,
            BBOX bbox, String formatExtension) {
        String xml = "\n<GroundOverlay>"
                + "\n<drawOrder>"+gridLoc[2]+"</drawOrder>"
                + "\n<altitudeMode>clampToGround</altitudeMode>"
                + "\n<Icon>" 
                + "\n<href>" 
                +  gridLocString(gridLoc) + "." + formatExtension 
                + "</href>" + "</Icon>\n" + bbox.toKML()
                + "\n</GroundOverlay>\n";

        return xml;
    }
    
    //private static String lookAtPlaceMark(BBOX bbox) {
    //    getLookAt(bbox)
    //}
    
    private static String getLookAt(BBOX bbox) {
        double lon1 = bbox.coords[0];
        double lat1 = bbox.coords[1];
        double lon2 = bbox.coords[2];
        double lat2 = bbox.coords[3];

        double R_EARTH = 6.371 * 1000000; // meters
        //double VIEWER_WIDTH = 22 * Math.PI / 180; // The field of view of the
        //                                            // google maps camera, in
        //                                            // radians
        double[] p1 = getRect(lon1, lat1, R_EARTH);
        double[] p2 = getRect(lon2, lat2, R_EARTH);
        double[] midpoint = new double[] { (p1[0] + p2[0]) / 2,
                (p1[1] + p2[1]) / 2, (p1[2] + p2[2]) / 2 };

        midpoint = getGeographic(midpoint[0], midpoint[1], midpoint[2]);

        // averaging the longitudes; using the rectangular coordinates makes the
        // calculated center tend toward the corner that's closer to the
        // equator.
        midpoint[0] = ((lon1 + lon2) / 2);

        double distance = distance(p1, p2);

        //double height = distance / (2 * Math.tan(VIEWER_WIDTH));

        return "<LookAt id=\"superoverlay\">"
                + "\n<longitude>"+ ((lon1 + lon2) / 2)+ "</longitude>"
                + "\n<latitude>"+midpoint[1]+"</latitude>"
                + "\n<altitude>0</altitude>"
                + "\n<range>"+distance+"</range>"
                + "\n<tilt>0</tilt>"
                + "\n<heading>0</heading>"
                + "\n<altitudeMode>clampToGround</altitudeMode>"
                //+ "\n<!--kml:altitudeModeEnum:clampToGround, relativeToGround, absolute -->"
                + "\n</LookAt>\n";
    }

    private static double[] getRect(double lat, double lon, double radius) {
        double theta = (90 - lat) * Math.PI / 180;
        double phi = (90 - lon) * Math.PI / 180;

        double x = radius * Math.sin(phi) * Math.cos(theta);
        double y = radius * Math.sin(phi) * Math.sin(theta);
        double z = radius * Math.cos(phi);
        return new double[] { x, y, z };
    }

    private static double[] getGeographic(double x, double y, double z) {
        double theta, phi, radius;
        radius = distance(new double[] { x, y, z }, new double[] { 0, 0, 0 });
        theta = Math.atan2(Math.sqrt(x * x + y * y), z);
        phi = Math.atan2(y, x);

        double lat = 90 - (theta * 180 / Math.PI);
        double lon = 90 - (phi * 180 / Math.PI);

        return new double[] { (lon > 180 ? lon - 360 : lon), lat, radius };
    }

    private static double distance(double[] p1, double[] p2) {
        double dx = p1[0] - p2[0];
        double dy = p1[1] - p2[1];
        double dz = p1[2] - p2[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void writeXml(String xml, HttpServletResponse response) {
        byte[] xmlData = xml.getBytes();
        response.setContentType("application/vnd.google-earth.kml+xml");
        response.setContentLength(xmlData.length);
        try {
            OutputStream os = response.getOutputStream();
            os.write(xmlData);
        } catch (IOException ioe) {
            // Do nothing...
        }
    }

}