/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.kml;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;

/**
 * The flow through this service is roughly as follows:
 *
 * <p>1) getTile() - inital parsing 2a) Tile completed by layer (raster) 2b) handleRequest(), Tile
 * completed by service 3a) SuperOverlay -> handleSuperOverlay(); -> generates required KML 3b)
 * Overlay (possibly KMZ with packaged data) -> handleOverlay() -> check cache, or call
 * createOverlay and package
 */
public class KMLService extends Service {
    private static Logger log = Logging.getLogger(KMLService.class.getName());

    public static final String SERVICE_KML = "kml";

    public static final String HINT_DEBUGGRID = "debuggrid";

    public static final String HINT_SITEMAP_LAYER = "sitemap";

    public static final String HINT_SITEMAP_GLOBAL = "sitemap_global";

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gsb;

    private RuntimeStats stats;

    private SecurityDispatcher secDispatcher;

    /** Protected no-argument constructor to allow run-time instrumentation */
    protected KMLService() {
        super(SERVICE_KML);
    }

    public KMLService(
            StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb, RuntimeStats stats) {
        super(SERVICE_KML);

        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
        this.stats = stats;
    }

    /**
     * Parses the pathinfo part of an HttpServletRequest into the three components it is (hopefully)
     * made up of.
     *
     * <p>Example 1: /kml/layername.format.extension (superoverlay) Example 2:
     * /kml/layername/tilekey.format.extension (kml or kmz, overlay) Example 3:
     * /kml/layername/tilekey.format (data)
     *
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

        if (typeExtOfst > 0) {
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
        if (ext.equalsIgnoreCase("kml") || ext.equalsIgnoreCase("kmz")) {
            // layername.km[z|l] or layername.format.km[z|l]
            retStrs[0] = filename.substring(0, typeExtOfst);
            retStrs[1] = "";
        } else {
            // layername/key.format.km[z|l]
            retStrs[0] = splitStr[splitStr.length - 2];
            retStrs[1] = filename.substring(0, typeExtOfst);
        }

        return retStrs;
    }

    /**
     * This is the entry point, this is where we tell the dispatcher whether we want to handle the
     * request or forward it to the tile layer (just a PNG).
     */
    @Override
    public ConveyorTile getConveyor(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException {
        String[] parsed = null;
        try {
            // TODO The container is supposed to handle the decoding prior
            // to returning but in Eclipse / Jetty this does not hold true
            parsed = parseRequest(URLDecoder.decode(request.getPathInfo(), "UTF-8"));
        } catch (Exception e) {
            throw new ServiceException("Unable to parse KML request : " + e.getMessage());
        }

        long[] gridLoc = {-1, -1, -1};

        // Do we have a key for the grid location?
        if (parsed[1].length() > 0) {
            gridLoc = KMLService.parseGridLocString(parsed[1]);
        }

        ConveyorKMLTile tile =
                new ConveyorKMLTile(
                        sb,
                        parsed[0],
                        gsb.getWorldEpsg4326().getName(),
                        gridLoc,
                        MimeType.createFromExtension(parsed[2]),
                        null,
                        request,
                        response);

        // Sitemap index ? kml/sitemap.xml
        if (parsed[0].equalsIgnoreCase("sitemap") && parsed[2].equalsIgnoreCase("xml")) {
            tile.setHint(HINT_SITEMAP_GLOBAL);
            String tmpUrl = urlPrefix(request.getRequestURL().toString(), parsed);
            tile.setUrlPrefix(tmpUrl.substring(0, tmpUrl.length() - "sitemap".length()));
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }

        // Sitemap ? kml/prefix:layername/sitemap.xml
        if (parsed[1].equalsIgnoreCase(HINT_SITEMAP_LAYER)) {
            tile.setHint(HINT_SITEMAP_LAYER);
            tile.setUrlPrefix(urlPrefix(request.getRequestURL().toString(), parsed));
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }

        // Is this a [super]overlay?
        if (parsed[3] != null) {
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            tile.setUrlPrefix(urlPrefix(request.getRequestURL().toString(), parsed));
            tile.setWrapperMimeType(MimeType.createFromExtension(parsed[3]));
        }

        // Debug layer?
        if (tile.getLayerId().equalsIgnoreCase(KMLDebugGridLayer.LAYERNAME)) {
            tile.setHint(HINT_DEBUGGRID);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
        }

        // System.out.println(Arrays.toString(tile.getTileIndex()) + " " +
        // tile.servletReq.getHeader("referer"));
        return tile;
    }

    /** Let the service handle the request */
    @Override
    public void handleRequest(Conveyor conv) throws GeoWebCacheException {

        ConveyorKMLTile tile = (ConveyorKMLTile) conv;

        TileLayer layer;
        if (tile.getHint() == HINT_DEBUGGRID) {
            layer = KMLDebugGridLayer.getInstance();

            // Generate random tile for debugging
            if (tile.getWrapperMimeType() == null) {
                tile.setTileLayer(layer);

                try {
                    layer.getTile(tile);
                } catch (Exception e) {
                    log.log(Level.FINE, e.getMessage(), e);
                }

                String mimeStr = getMimeTypeOverride(tile);
                writeTileResponse(tile, false, stats, mimeStr);
                return;
            }
        } else if (tile.getHint() == HINT_SITEMAP_GLOBAL) {
            layer = null;
        } else {
            layer = tld.getTileLayer(tile.getLayerId());

            if (layer == null) {
                throw new ServiceException(
                        "No layer provided, request parsed to: " + tile.getLayerId());
            }
        }
        tile.setTileLayer(layer);

        if (Objects.nonNull(tile.getLayer())) {
            // The KML service uses negative magic numbers in the z coordinate
            // for superoverlays.
            if (tile.getTileIndex()[2] < 0) {
                getSecurityDispatcher().checkSecurity(tile.getLayer(), null, null);
            } else {
                getSecurityDispatcher().checkSecurity(tile);
            }
        }

        // if(tile.getHint() == HINT_SITEMAP_LAYER || tile.getHint() == HINT_SITEMAP_GLOBAL) {
        // KMLSiteMap sm = new KMLSiteMap(tile,tld);
        // try {
        // sm.write();
        // } catch (IOException ioe) {
        // throw new GeoWebCacheException("Unable to write sitemap: " + ioe.getMessage());
        // }
        // return;
        // }

        if (tile.getTileIndex()[2] == -1) {
            // No tile index -> super overlay
            if (log.isLoggable(Level.FINE)) {
                log.fine("Request for super overlay for " + tile.getLayerId() + " received");
            }
            handleSuperOverlay(tile);
        } else {
            if (log.isLoggable(Level.FINE)) {
                log.fine("Request for overlay for " + tile.getLayerId());
            }
            handleOverlay(tile);
        }
    }

    private static String urlPrefix(String requestUrl, String[] parsed) {
        int endOffset = requestUrl.length() - parsed[1].length() - parsed[2].length();

        // Also remove the second extension and the dot
        if (parsed.length > 3 && parsed[3] != null) {
            endOffset -= parsed[3].length() + 1;
        }

        return new String(requestUrl.substring(0, endOffset - 1));
    }

    /** Creates a superoverlay, ie. a short description and network links to the first overlays. */
    private void handleSuperOverlay(ConveyorKMLTile tile) throws GeoWebCacheException {
        TileLayer layer = tile.getLayer();

        GridSubset gridSubset = tile.getGridSubset();

        // int srsIdx = layer.getSRSIndex(srs);
        BoundingBox bbox = gridSubset.getCoverageBestFitBounds();

        String formatExtension = "." + tile.getMimeType().getFileExtension();
        if (tile.getWrapperMimeType() != null) {
            formatExtension = formatExtension + "." + tile.getWrapperMimeType().getFileExtension();
        }

        long[] gridRect = gridSubset.getCoverageBestFit();
        String networkLinks = null;

        // Check whether we need two tiles for world bounds or not
        if (gridRect[4] > 0 && (gridRect[2] != gridRect[0] || gridRect[3] != gridRect[1])) {
            throw new GeoWebCacheException(
                    layer.getName()
                            + " ("
                            + bbox.toString()
                            + ") is too big for the sub grid set for "
                            + gridSubset.getName()
                            + ", allow for smaller zoom levels.");
        } else if (gridRect[0] != gridRect[2]) {
            long[] gridLocWest = {0, 0, 0};
            long[] gridLocEast = {1, 0, 0};

            BoundingBox bboxWest =
                    new BoundingBox(bbox.getMinX(), bbox.getMinY(), 0.0, bbox.getMaxY());
            BoundingBox bboxEast =
                    new BoundingBox(0.0, bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY());

            networkLinks =
                    superOverlayNetworLink(
                                    layer.getName() + " West",
                                    bboxWest,
                                    tile.getUrlPrefix()
                                            + "/"
                                            + gridLocString(gridLocWest)
                                            + formatExtension)
                            + superOverlayNetworLink(
                                    layer.getName() + " East",
                                    bboxEast,
                                    tile.getUrlPrefix()
                                            + "/"
                                            + gridLocString(gridLocEast)
                                            + formatExtension);

        } else {
            long[] gridLoc = {gridRect[0], gridRect[1], gridRect[4]};

            networkLinks =
                    superOverlayNetworLink(
                            layer.getName(),
                            bbox,
                            tile.getUrlPrefix() + "/" + gridLocString(gridLoc) + formatExtension);
        }

        String xml =
                KMLHeader()
                        + "\n<Folder>"
                        + getLookAt(bbox)
                        + networkLinks
                        + "\n</Folder>"
                        + "\n</kml>\n";

        tile.setBlob(new ByteArrayResource(xml.getBytes()));
        tile.setMimeType(XMLMime.kml);
        tile.setStatus(200);
        String mimeStr = getMimeTypeOverride(tile);
        writeTileResponse(tile, true, stats, mimeStr);
    }

    /** Creates a network link to the first tile in the pyramid */
    private static String superOverlayNetworLink(String superString, BoundingBox bbox, String url) {
        String xml =
                "\n<NetworkLink><name>Super-overlay: "
                        + superString
                        + "</name>"
                        + "\n<Region>\n"
                        + bbox.toKMLLatLonAltBox()
                        + "\n<Lod><minLodPixels>128</minLodPixels>"
                        + "\n<maxLodPixels>-1</maxLodPixels></Lod>"
                        + "\n</Region>"
                        + "\n<Link><href>"
                        + url
                        + "</href>"
                        + "\n<viewRefreshMode>onRegion</viewRefreshMode>"
                        + "\n</Link>"
                        + "\n</NetworkLink>";

        return xml;
    }

    protected static String gridLocString(long[] gridLoc) {
        return "x" + gridLoc[0] + "y" + gridLoc[1] + "z" + gridLoc[2];
    }

    protected static long[] parseGridLocString(String key) throws ServiceException {
        // format should be x<x>y<y>z<z>

        long[] ret = {-1, -1, -1};

        int yloc = key.indexOf("y");
        int zloc = key.indexOf("z");

        if (yloc < 2 || zloc < 4) {
            return ret;
        }

        try {
            ret[0] = Long.parseLong(key.substring(1, yloc));
            ret[1] = Long.parseLong(key.substring(yloc + 1, zloc));
            ret[2] = Long.parseLong(key.substring(zloc + 1, key.length()));
        } catch (NumberFormatException | StringIndexOutOfBoundsException nfe) {
            throw new ServiceException("Unable to parse " + key);
        }
        return ret;
    }

    /**
     * These are the main nodes in the KML hierarchy, each overlay contains a set of network links
     * (up to 4) that point to the overlays on the next level.
     *
     * <p>1) KMZ: The cache will contain a zip with overlay and data
     *
     * <p>2) KML: The cache will only contain the overlay itself, the overlay will cause a separate
     * tile request to get the data
     */
    private void handleOverlay(ConveyorKMLTile tile) throws GeoWebCacheException {

        TileLayer tileLayer = tile.getLayer();

        boolean packageData = false;
        if (tile.getWrapperMimeType() == XMLMime.kmz) {
            packageData = true;
        }

        // TODO The 1.1 branch doesn't have a good way of storing the archives.
        // For now we compress on every request

        // Did we get lucky?
        // TODO need to look into expiration here
        // if(tile.retrieve(-1)) {
        // writeResponse(tile,true);
        // return;
        // }

        // Sigh....
        if (!packageData) {
            String overlayXml = createOverlay(tile, false);
            tile.setBlob(new ByteArrayResource(overlayXml.getBytes()));
            tile.setStatus(200);
            // tileLayer.putTile(tile);
        } else {
            // Get the overlay
            String overlayXml = createOverlay(tile, true);

            // Get the data (cheat)
            try {
                tile.setWrapperMimeType(null);
                try {
                    tileLayer.getTile(tile);
                } catch (OutsideCoverageException oce) {
                    log.log(
                            Level.SEVERE,
                            "Out of bounds: "
                                    + Arrays.toString(tile.getTileIndex())
                                    + " should never habe been linked to.");
                    throw oce;
                }
                tile.setWrapperMimeType(XMLMime.kmz);
            } catch (IOException ioe) {
                log.log(Level.SEVERE, ioe.getMessage(), ioe);
                throw new ServiceException(ioe.getMessage());
            }

            byte[] zip =
                    KMZHelper.createZippedKML(
                            gridLocString(tile.getTileIndex()),
                            tile.getMimeType().getFileExtension(),
                            overlayXml.getBytes(),
                            tile.getBlob());

            tile.setBlob(new ByteArrayResource(zip));
            tile.setStatus(200);
            // tileLayer.putTile(tile);

        }

        String mimeStr = getMimeTypeOverride(tile);

        writeTileResponse(tile, true, stats, mimeStr);
    }

    private String getMimeTypeOverride(ConveyorKMLTile tile) {
        String mimeStr = null;
        if (tile.getWrapperMimeType() != null) {
            mimeStr = tile.getWrapperMimeType().getMimeType();
        }
        return mimeStr;
    }

    /**
     * Creates an overlay element: 1) Header 2) Network links to regions where we have more data 3)
     * Overlay (link to data) 4) Footer
     *
     * @return The html for the overlay element
     */
    private String createOverlay(ConveyorKMLTile tile, boolean isPackaged)
            throws ServiceException, GeoWebCacheException {
        boolean isRaster = (tile.getMimeType() instanceof ImageMime);

        TileLayer tileLayer = tile.getLayer();

        GridSubset gridSubset = tile.getGridSubset();

        long[] gridLoc = tile.getTileIndex();

        BoundingBox bbox = gridSubset.boundsFromIndex(gridLoc);

        String refreshTags = "";
        int refreshInterval = tileLayer.getExpireClients((int) gridLoc[2]);
        if (refreshInterval > 0) {
            refreshTags =
                    "\n<refreshMode>onInterval</refreshMode>"
                            + "\n<refreshInterval>"
                            + refreshInterval
                            + "</refreshInterval>";
        }

        StringBuffer buf = new StringBuffer();

        // 1) Header
        boolean setMaxLod = false;
        if (isRaster && gridLoc[2] < gridSubset.getZoomStop()) {
            setMaxLod = true;
        }
        buf.append(createOverlayHeader(bbox, setMaxLod));

        buf.append("\n<!-- Network links to subtiles -->\n");
        // 2) Network links, only to tiles getCoverages();within bounds

        long[][] linkGridLocs = gridSubset.getSubGrid(gridLoc);

        // 3) Apply secondary filter against linking to empty tiles
        linkGridLocs =
                KMZHelper.filterGridLocs(
                        tile.getStorageBroker(),
                        getSecurityDispatcher(),
                        tileLayer,
                        gridSubset.getName(),
                        tile.getMimeType(),
                        linkGridLocs);

        // int moreData = 0;
        for (int i = 0; i < 4; i++) {
            // Only add this link if it is within the bounds
            if (linkGridLocs[i][2] > 0) {
                BoundingBox linkBbox = gridSubset.boundsFromIndex(linkGridLocs[i]);

                String gridLocStr = gridLocString(linkGridLocs[i]);

                // Always use absolute URLs for these
                String gridLocUrl =
                        tile.getUrlPrefix()
                                + gridLocStr
                                + "."
                                + tile.getMimeType().getFileExtension()
                                + "."
                                + tile.getWrapperMimeType().getFileExtension();

                buf.append(
                        createNetworkLinkElement(
                                tileLayer, linkBbox, gridLocUrl, gridLocStr, -1, refreshTags));
                // moreData++;
            }
        }

        buf.append("\n<!-- Network link to actual content -->\n");

        // 5) Overlay, should be relative
        if (isRaster) {
            buf.append(
                    createGroundOverLayElement(
                            gridLoc,
                            tile.getUrlPrefix(),
                            bbox,
                            tile.getMimeType().getFileExtension(),
                            refreshTags));
        } else {
            // KML
            String gridLocStr = gridLocString(gridLoc);
            String gridLocUrl = gridLocStr + "." + tile.getMimeType().getFileExtension();

            if (isPackaged) {
                gridLocUrl = "data_" + gridLocUrl;
            }

            int maxLodPixels = -1;
            if (tile.getLayer() instanceof KMLDebugGridLayer) {
                maxLodPixels = 385;
            }

            buf.append(
                    createNetworkLinkElement(
                            tileLayer, bbox, gridLocUrl, gridLocStr, maxLodPixels, refreshTags));
        }

        // if(moreData > 0) {
        // xml += "</Document>\n<Document>"+moreDataIcon(bbox)+"</Document>\n";
        // } else {
        buf.append("</Document>\n</kml>");
        // }

        return buf.toString();
    }

    /** This creates the header for the overlay */
    private static String createOverlayHeader(BoundingBox bbox, boolean setMaxLod) {
        int maxLodPixels = -1;
        if (setMaxLod) {
            maxLodPixels = 385;
        }

        return KMLHeader()
                + "<Document>\n"
                + "<Region>\n"
                + bbox.toKMLLatLonAltBox()
                + "<Lod><minLodPixels>128</minLodPixels>"
                + "<maxLodPixels>"
                + Integer.toString(maxLodPixels)
                + "</maxLodPixels></Lod>\n"
                + "</Region>\n";
    }

    /**
     * For KML features / vector data OR for the next level
     *
     * @return The network link element html
     */
    private static String createNetworkLinkElement(
            TileLayer layer,
            BoundingBox bbox,
            String gridLocUrl,
            String tileIdx,
            int maxLodPixels,
            String refreshTags) {

        String xml =
                "\n<NetworkLink>"
                        + "\n<name>"
                        + layer.getName()
                        + "</name>"
                        + "\n<Region>"
                        + bbox.toKMLLatLonAltBox()
                        + "\n<Lod><minLodPixels>128</minLodPixels>"
                        + "<maxLodPixels>"
                        + Integer.toString(maxLodPixels)
                        + "</maxLodPixels></Lod>\n"
                        + "</Region>"
                        + "\n<Link>"
                        + "\n<href>"
                        + gridLocUrl
                        + "</href>"
                        + refreshTags
                        + "\n<viewRefreshMode>onRegion</viewRefreshMode>"
                        + "\n</Link>"
                        + "\n</NetworkLink>\n";

        return xml;
    }

    /** Used for linking to a raster image */
    private static String createGroundOverLayElement(
            long[] gridLoc,
            String urlStr,
            BoundingBox bbox,
            String formatExtension,
            String refreshTags) {

        String xml =
                "\n<GroundOverlay>"
                        + "\n<drawOrder>"
                        + gridLoc[2]
                        + "</drawOrder>"
                        + "\n<Icon>"
                        + "\n<href>"
                        + gridLocString(gridLoc)
                        + "."
                        + formatExtension
                        + "</href>"
                        + refreshTags
                        + "\n</Icon>\n"
                        + "\n<altitudeMode>clampToGround</altitudeMode>"
                        + bbox.toKMLLatLonBox()
                        + "\n</GroundOverlay>\n";

        return xml;
    }

    private static String getLookAt(BoundingBox bbox) {
        double lon1 = bbox.getMinX();
        double lat1 = bbox.getMinY();
        double lon2 = bbox.getMaxX();
        double lat2 = bbox.getMaxY();

        double R_EARTH = 6.371 * 1000000; // meters
        // double VIEWER_WIDTH = 22 * Math.PI / 180; // The field of view of the
        // // google maps camera, in
        // // radians
        double[] p1 = getRect(lon1, lat1, R_EARTH);
        double[] p2 = getRect(lon2, lat2, R_EARTH);
        double[] midpoint = {(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2, (p1[2] + p2[2]) / 2};

        midpoint = getGeographic(midpoint[0], midpoint[1], midpoint[2]);

        // averaging the longitudes; using the rectangular coordinates makes the
        // calculated center tend toward the corner that's closer to the
        // equator.
        midpoint[0] = ((lon1 + lon2) / 2);

        double distance = distance(p1, p2);

        // double height = distance / (2 * Math.tan(VIEWER_WIDTH));

        return "<LookAt id=\"superoverlay\">"
                + "\n<longitude>"
                + ((lon1 + lon2) / 2)
                + "</longitude>"
                + "\n<latitude>"
                + midpoint[1]
                + "</latitude>"
                + "\n<altitude>0</altitude>"
                + "\n<heading>0</heading>"
                + "\n<tilt>0</tilt>"
                + "\n<range>"
                + distance
                + "</range>"
                + "\n<altitudeMode>clampToGround</altitudeMode>"
                // + "\n<!--kml:altitudeModeEnum:clampToGround, relativeToGround, absolute -->"
                + "\n</LookAt>\n";
    }

    private static String KMLHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"http://www.opengis.net/kml/2.2 "
                + "http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd\">\n";
    }

    // private static String moreDataIcon(BBOX bbox){
    // return "<Region>\n" +
    // "<Lod><minLodPixels>128</minLodPixels>" +
    // "<maxLodPixels>512</maxLodPixels></Lod>\n" +
    // bbox.toKML() + "</Region>\n" +
    // "<ScreenOverlay><name>More data</name>" +
    // "<visibility>1</visibility>" +
    // "<open>1</open>" +
    // "<Icon><href>http://bbc.blueghost.co.uk/images/bbc_v2.png</href></Icon>" +
    // "<color>ffffffff</color>" +
    // "<drawOrder>0</drawOrder>" +
    // "<overlayXY x=\"1\" y=\"1\" xunits=\"fraction\" yunits=\"fraction\"/>" +
    // "<screenXY x=\"1\" y=\"1\" xunits=\"fraction\" yunits=\"fraction\"/>" +
    // "<rotationXY x=\"0\" y=\"0\" xunits=\"fraction\" yunits=\"fraction\"/>" +
    // "<size x=\"0\" y=\"0\" xunits=\"fraction\" yunits=\"fraction\"/>" +
    // "<rotation>0</rotation>" +
    // "</ScreenOverlay>";
    // }

    private static double[] getRect(double lat, double lon, double radius) {
        double theta = (90 - lat) * Math.PI / 180;
        double phi = (90 - lon) * Math.PI / 180;

        double x = radius * Math.sin(phi) * Math.cos(theta);
        double y = radius * Math.sin(phi) * Math.sin(theta);
        double z = radius * Math.cos(phi);
        return new double[] {x, y, z};
    }

    private static double[] getGeographic(double x, double y, double z) {
        double radius = distance(new double[] {x, y, z}, new double[] {0, 0, 0});
        double theta = Math.atan2(Math.sqrt(x * x + y * y), z);
        double phi = Math.atan2(y, x);

        double lat = 90 - (theta * 180 / Math.PI);
        double lon = 90 - (phi * 180 / Math.PI);

        return new double[] {(lon > 180 ? lon - 360 : lon), lat, radius};
    }

    private static double distance(double[] p1, double[] p2) {
        double dx = p1[0] - p2[0];
        double dy = p1[1] - p2[1];
        double dz = p1[2] - p2[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public void setSecurityDispatcher(SecurityDispatcher secDispatcher) {
        this.secDispatcher = secDispatcher;
    }

    protected SecurityDispatcher getSecurityDispatcher() {
        return secDispatcher;
    }
}
