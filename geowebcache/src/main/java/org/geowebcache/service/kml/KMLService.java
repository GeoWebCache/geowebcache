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
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.tile.KMLTile;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.wms.BBOX;


/**
 * The flow through this service is roughly as follows:
 * 
 *  1) getTile() - inital parsing
 *  2a) Tile completed by layer (raster)
 *  2b) handleRequest(), Tile completed by service
 *  3a) SuperOverlay 
 *  -> handleSuperOverlay();
 *    -> generates required KML
 *  3b) Overlay (possibly KMZ with packaged data)
 *  -> handleOverlay() 
 *    -> check cache, or call createOverlay and package
 */
public class KMLService extends Service {
    private static Log log = LogFactory
            .getLog(org.geowebcache.service.kml.KMLService.class);

    public static final String SERVICE_KML = "kml";
    
    public static final String HINT_DEBUGGRID = "debuggrid";
        
    public KMLService() {
        super(SERVICE_KML);
    }

    /**
     * Parses the pathinfo part of an HttpServletRequest into the three
     * components it is (hopefully) made up of.
     * 
     * Example 1: /kml/layername.format.extension (superoverlay)
     * Example 2: /kml/layername/tilekey.format.extension (kml or kmz, overlay)
     * Example 3: /kml/layername/tilekey.format (data)
     * 
     * @param pathInfo
     * @return {layername, tilekey, format, wrapperformat}
     */
    protected static String[] parseRequest(String pathInfo) {
        String[] retStrs = new String[4];

        String[] splitStr = pathInfo.split("/");
        
        // Deal with the extension
        String filename = splitStr[splitStr.length - 1];
        int extOfst = filename.lastIndexOf(".");
        // This finds the last extension (wrapper)
        String lastExtension = filename.substring(extOfst + 1, filename.length());
        
        // Looks for a payload format
        int typeExtOfst = filename.lastIndexOf(".", extOfst - 1);
        
        if(typeExtOfst > 0) {
            // Wrapper with two extensions
        	retStrs[2] = filename.substring(typeExtOfst + 1, extOfst);
        	retStrs[3] = lastExtension;
        } else {
            // Regular tile
                retStrs[2] = lastExtension;
                retStrs[3] = null;
        	typeExtOfst = extOfst;
        }

        // Three types of requests
        String ext = splitStr[splitStr.length - 2];
        if(ext.equalsIgnoreCase("kml") || ext.equalsIgnoreCase("kmz")) {
            // layername.km[z|l] or layername.format.km[z|l]
            retStrs[0] = filename.substring(0,typeExtOfst);
            retStrs[1] = "";
        } else {
            // layername/key.format.km[z|l]
            retStrs[0] = splitStr[splitStr.length - 2];
            retStrs[1] = filename.substring(0,typeExtOfst);
        }
        
        return retStrs;
    }

    /**
     * This is the entry point, this is where we tell the dispatcher whether we want 
     * to handle the request or forward it to the tile layer (just a PNG).
     */
    public Tile getTile(HttpServletRequest request, HttpServletResponse response) 
    throws GeoWebCacheException  {
        String[] parsed = null;
        try {
            parsed = parseRequest(request.getPathInfo());
        } catch (Exception e) {
            throw new ServiceException("Unable to parse KML request : "+ e.getMessage());
        }
        
        KMLTile tile =  new KMLTile(parsed[0], request, response);
        tile.setMimeType(MimeType.createFromExtension(parsed[2]));
        tile.setSRS(SRS.getEPSG4326());
        
        // Do we have a key for the grid location?
        if(parsed[1].length() > 0) {
            tile.setTileIndex(KMLService.parseGridLocString(parsed[1]));
        }
        
        // Is this a [super]overlay?
        if(parsed[3] != null) {
            tile.setRequestHandler(Tile.RequestHandler.SERVICE);
            tile.setUrlPrefix(urlPrefix(request.getRequestURL().toString(),parsed));
            tile.setWrapperMimeType(MimeType.createFromExtension(parsed[3]));
        }
        
        // Debug layer?
        if(tile.getLayerId().equalsIgnoreCase(KMLDebugGridLayer.LAYERNAME)) {
            tile.setHint(HINT_DEBUGGRID);
        }
        
        return tile;
    }

    /**
     * Let the service handle the request 
     */
    public void handleRequest(TileLayerDispatcher tLD, Tile kmlTile) 
    throws GeoWebCacheException {
        KMLTile tile = (KMLTile) kmlTile;
        
        TileLayer layer; 
        if(tile.getHint() == HINT_DEBUGGRID) {
            layer = KMLDebugGridLayer.getInstance();
        } else {
            layer = tLD.getTileLayer(tile.getLayerId());
            
            if(layer == null) {
                throw new ServiceException(
                        "No layer provided, request parsed to: " + tile.getLayerId());
            } else if(! layer.isInitialized()){
                // ?
            }
        }
        tile.setTileLayer(layer);
        
        
        //TODO this needs to be done more nicely
        //TODO debuggrid should not have skipped this one [debuggrid, x1y0z0, kml, kmz]
        //boolean isRaster = true;
        //if(tile.getWrapperMimeType() != null || tile.getMimeType() ) {
        //    isRaster = false;
        //}

        if(tile.getTileIndex() == null) {
            // No tile index -> super overlay
            if(log.isDebugEnabled()) { 
                log.debug("Request for super overlay for "+tile.getLayerId()+" received");
            }
            handleSuperOverlay(tile);
        } else {
            if(log.isDebugEnabled()) { 
                log.debug("Request for overlay for "+tile.getLayerId());
            }   
            handleOverlay(tile);
        }
    }
    
    private static String urlPrefix(String requestUrl, String[] parsed) {
        int endOffset = requestUrl.length() - parsed[1].length() - parsed[2].length();
        
        // Also remove the second extension and the dot
        if(parsed.length > 3 && parsed[3] != null) {
            endOffset -= parsed[3].length() + 1;
        }
        
        return new String(requestUrl.substring(0, endOffset - 1));
    }

    /**
     * Creates a superoverlay,
     * ie. a short description and network links to the first overlays.
     * 
     * @param tile
     */
    private static void handleSuperOverlay(KMLTile tile) throws GeoWebCacheException {
        SRS srs = SRS.getEPSG4326();
        TileLayer layer = tile.getLayer();
        
        //int srsIdx = layer.getSRSIndex(srs);
        BBOX bbox = layer.getGrid(srs).getBounds();
        
        String formatExtension = "."+tile.getMimeType().getFileExtension();
        if(tile.getWrapperMimeType() != null) {
            formatExtension = formatExtension + "." + tile.getWrapperMimeType().getFileExtension();
        }
        
        int[] gridLoc = layer.getZoomedOutGridLoc(srs);
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
                    tile.getUrlPrefix() + "/" + gridLocString(gridLocWest) +formatExtension)
              + superOverlayNetworLink(
                    layer.getName() + " East", 
                    bboxEast, 
                    tile.getUrlPrefix() + "/" + gridLocString(gridLocEast) +formatExtension);
            
        } else {
            networkLinks = superOverlayNetworLink(
                    layer.getName(), 
                    bbox, 
                    tile.getUrlPrefix() + "/" + gridLocString(gridLoc) + formatExtension);
        }
        
        String xml = KMLHeader()
                + "\n<Folder>"
                + networkLinks
                //+ getLookAt(bbox)
                + "\n</Folder>"
                + "\n</kml>\n";

        tile.setContent(xml.getBytes());
        tile.setMimeType(XMLMime.kml);
        tile.setStatus(200);
        writeResponse(tile);
    }

    /**
     * Creates a network link to the first tile in the pyramid
     * 
     * @param superString
     * @param bbox
     * @param url
     * @return
     */
    private static String superOverlayNetworLink(String superString, BBOX bbox, String url) {
        String xml = "\n<NetworkLink><name>Super-overlay: "+superString+"</name>"
        + "\n<Region>\n"
        + bbox.toKML()
        + "\n<Lod><minLodPixels>128</minLodPixels>"
        + "\n<maxLodPixels>-1</maxLodPixels></Lod>"
        + "\n</Region>"
        + "\n<Link><href>"+url+"</href>"
        + "\n<viewRefreshMode>onRegion</viewRefreshMode>" 
        + "\n</Link>"
        + "\n</NetworkLink>";
        
        return xml;
    }

    private static String gridLocString(int[] gridLoc) {
        return "x" + gridLoc[0] + "y" + gridLoc[1] + "z" + gridLoc[2];
    }

    protected static int[] parseGridLocString(String key) throws ServiceException {
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
     * These are the main nodes in the KML hierarchy, each overlay
     * contains a set of network links (up to 4) that point to the
     * overlays on the next level.
     * 
     * 1) KMZ:
     *    The cache will contain a zip with overlay and data
     *    
     * 2) KML:
     *    The cache will only contain the overlay itself, 
     *    the overlay will cause a separate tile request to get the data
     */
    private static void handleOverlay(KMLTile tile) 
    throws GeoWebCacheException {
        
        TileLayer tileLayer = tile.getLayer();
      
        boolean packageData = false;
        if(tile.getWrapperMimeType() == XMLMime.kmz) {
            packageData = true;
        }

        // Did we get lucky?
        if(tileLayer.tryCacheFetch(tile)) {
            writeResponse(tile);
            return;
        }
        
        // Sigh.... 
        if(packageData) {
            // Get the overlay
            String overlayXml = createOverlay(tile, true);
            
            // Get the data (cheat)
            try {
                tile.setWrapperMimeType(null);
                tileLayer.getResponse(tile);
                tile.setWrapperMimeType(XMLMime.kmz);
            } catch (IOException ioe) {
                log.error(ioe.getMessage());
                ioe.printStackTrace();
                throw new ServiceException(ioe.getMessage());
            }
            
            byte[] zip = KMZHelper.createZippedKML(
                    gridLocString(tile.getTileIndex()), tile.getMimeType().getFileExtension(), 
                    overlayXml.getBytes(), tile.getContent());
            
            tile.setContent(zip);
            tile.setStatus(200);
            tileLayer.putTile(tile);

        } else {
            String overlayXml = createOverlay(tile, false);
            tile.setContent(overlayXml.getBytes());
            tile.setStatus(200);
            tileLayer.putTile(tile);
        }

        writeResponse(tile);
    }
    
    /**
     * Creates an overlay element:
     * 1) Header 
     * 2) Network links to regions where we have more data 
     * 3) Overlay (link to data) 
     * 4) Footer
     * 
     * @param tileLayer
     * @param urlStr
     * @param key
     * @param extension
     * @param formatExtension
     * @param isRaster
     * @param response
     * @return
     * @throws ServiceException
     */
    private static String createOverlay(KMLTile tile, boolean isPackaged)
    throws ServiceException,GeoWebCacheException {

        TileLayer tileLayer = tile.getLayer();
        int[] gridLoc = tile.getTileIndex();
        
        SRS srs = SRS.getEPSG4326();
        //int srsIdx = tileLayer.getSRSIndex();
        BBOX bbox = tileLayer.getBboxForGridLoc(srs, gridLoc);

        StringBuffer buf = new StringBuffer();
        // 1) Header
        buf.append(createOverlayHeader(bbox, 
                tile.getMimeType() instanceof ImageMime));

        // 2) Network links, only to tiles within bounds
        int[][] linkGridLocs = tileLayer.getZoomInGridLoc(srs, gridLoc);

        // 3) Apply secondary filter against linking to empty tiles
        if (tile.getMimeType() == XMLMime.kml) {
            linkGridLocs = KMZHelper.filterGridLocs(tileLayer, tile.getMimeType(),linkGridLocs);
        }

        //int moreData = 0;
        for (int i = 0; i < 4; i++) {
            // Only add this link if it is within the bounds
            if (linkGridLocs[i][2] > 0) {
                BBOX linkBbox = tileLayer.getBboxForGridLoc(srs,
                        linkGridLocs[i]);
                
                // Always use absolute URLs for these
                String gridLocUrl = tile.getUrlPrefix() + gridLocString(linkGridLocs[i]) 
                +"." +tile.getMimeType().getFileExtension()+ "." + tile.getWrapperMimeType().getFileExtension();

                String gridLocStr = gridLocString(linkGridLocs[i]);
                
                buf.append(createNetworkLinkElement(tileLayer, linkBbox, gridLocUrl, gridLocStr));
                //moreData++;
            }
        }

        // 3) Overlay, should be relative 
        if (tile.getMimeType() instanceof ImageMime) {
            buf.append(
                    createGroundOverLayElement(
                    gridLoc, tile.getUrlPrefix(), 
                    bbox, tile.getMimeType().getFileExtension()));
        } else {
            // KML
            String gridLocStr = gridLocString(gridLoc);
            String gridLocUrl = gridLocStr + "." + tile.getMimeType().getFileExtension();
            if(isPackaged) {
                gridLocUrl = "data_" + gridLocUrl;
            }
            buf.append(createNetworkLinkElement(tileLayer, bbox, gridLocUrl, gridLocStr));
        }

        //if(moreData > 0) {
        //    xml += "</Document>\n<Document>"+moreDataIcon(bbox)+"</Document>\n";
        //} else {
            buf.append("</Document>\n</kml>");
        //}
        
        return buf.toString();
    }

    /**
     * This creates the header for the overlay
     * 
     * @param bbox
     * @return
     */
    private static String createOverlayHeader(BBOX bbox, boolean isRaster) {
        int maxLodPixels = -1;
        if(isRaster) {
            maxLodPixels = 385;
        }
        
        return  KMLHeader()
                + "<Document>\n"
                + "<Region>\n"
                + bbox.toKML()
                + "<Lod><minLodPixels>128</minLodPixels>"
                + "<maxLodPixels>"+Integer.toString(maxLodPixels)+"</maxLodPixels></Lod>\n"
                + "</Region>\n";
    }

    /**
     * For KML features  / vector data OR for the next level
     * 
     * @param layer
     * @param urlStr
     * @param gridLoc
     * @param bbox
     * @param extension
     * @return
     */
    private static String createNetworkLinkElement(
            TileLayer layer, BBOX bbox, String gridLocUrl, String tileIdx) {
        
        int maxLodPixels = -1;
        
        
        // Hack
        if(layer instanceof KMLDebugGridLayer && gridLocUrl.startsWith("data_")) {
            maxLodPixels = 385;
        }
      
        String xml = "\n<NetworkLink>"
                + "\n<name>"
                + layer.getName()
                + "</name>"
                + "\n<Region>"
                + bbox.toKML()
                + "\n<Lod><minLodPixels>128</minLodPixels>"
                + "<maxLodPixels>"+Integer.toString(maxLodPixels)+"</maxLodPixels></Lod>\n"
                + "</Region>" + "\n<Link>" 
                + "\n<href>"
                +  gridLocUrl
                +"</href>"
                + "\n<viewRefreshMode>onRegion</viewRefreshMode>" + "</Link>"
                + "\n</NetworkLink>\n";

        return xml;
    }

    /**
     * Used for linking to a raster image 
     * 
     * @param gridLoc
     * @param urlStr
     * @param bbox
     * @param formatExtension
     * @return
     */
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
                + "\n<heading>0</heading>"
                + "\n<tilt>0</tilt>"
                + "\n<range>"+distance+"</range>"
                + "\n<altitudeMode>clampToGround</altitudeMode>"
                //+ "\n<!--kml:altitudeModeEnum:clampToGround, relativeToGround, absolute -->"
                + "\n</LookAt>\n";
    }
    
    private static String KMLHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<kml xmlns=\"http://www.opengis.net/kml/2.2\" "
        +"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        +"xsi:schemaLocation=\"http://www.opengis.net/kml/2.2 "
        +"http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd\">\n";
    }

//    private static String moreDataIcon(BBOX bbox){ 
//        return "<Region>\n" +
//             "<Lod><minLodPixels>128</minLodPixels>" +
//             "<maxLodPixels>512</maxLodPixels></Lod>\n" +
//             bbox.toKML() + "</Region>\n" +
//             "<ScreenOverlay><name>More data</name>" +
//                "<visibility>1</visibility>" +
//                "<open>1</open>" +
//                "<Icon><href>http://bbc.blueghost.co.uk/images/bbc_v2.png</href></Icon>" +
//                "<color>ffffffff</color>" + 
//                "<drawOrder>0</drawOrder>" +
//                "<overlayXY x=\"1\" y=\"1\" xunits=\"fraction\" yunits=\"fraction\"/>" +
//                "<screenXY x=\"1\" y=\"1\" xunits=\"fraction\" yunits=\"fraction\"/>" +
//                "<rotationXY x=\"0\" y=\"0\" xunits=\"fraction\" yunits=\"fraction\"/>" +
//                "<size x=\"0\" y=\"0\" xunits=\"fraction\" yunits=\"fraction\"/>" +
//                "<rotation>0</rotation>" +
//                "</ScreenOverlay>";
//    }
    
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
    
    private static void writeResponse(Tile tile) {
        HttpServletResponse response = tile.servletResp;
        byte[] data = tile.getContent();
        
        response.setStatus((int) tile.getStatus());
        
        TileLayer layer = tile.getLayer();
        if(layer != null) {
            layer.setExpirationHeader(tile.servletResp);
        }
        
        if(tile.getWrapperMimeType() != null) {
            response.setContentType(tile.getWrapperMimeType().getMimeType());
        } else {
            response.setContentType(tile.getMimeType().getMimeType());
        }
        
        response.setContentLength(data.length);
        tile.getLayer().setExpirationHeader(response);
        
        try {
            OutputStream os = response.getOutputStream();
            os.write(data);
        } catch (IOException ioe) {
            // Do nothing...
        }
    }
}