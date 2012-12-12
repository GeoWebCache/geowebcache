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
 * 
 */
package org.geowebcache.service.wms;

import static org.geowebcache.grid.GridUtil.findBestMatchingGrid;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridMismatchException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class WMSService extends Service {
    public static final String SERVICE_WMS = "wms";
    
    static final String SERVICE_PATH = "/"+GeoWebCacheDispatcher.TYPE_SERVICE+"/"+SERVICE_WMS;

    private static Log log = LogFactory.getLog(org.geowebcache.service.wms.WMSService.class);

    // Recombine tiles to support regular WMS clients?
    private boolean fullWMS = false;

    // Proxy requests that are not getmap or getcapabilities?
    private boolean proxyRequests = false;

    // Proxy requests that are not tiled=true?
    private boolean proxyNonTiledRequests = false;

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private RuntimeStats stats;
    
    private URLMangler urlMangler = new NullURLMangler();
    
    private GeoWebCacheDispatcher controller = null;
    

    /**
     * Protected no-argument constructor to allow run-time instrumentation
     */
    protected WMSService() {
        super(SERVICE_WMS);
    }

    public WMSService(StorageBroker sb, TileLayerDispatcher tld, RuntimeStats stats) {
        super(SERVICE_WMS);

        this.sb = sb;
        this.tld = tld;
        this.stats = stats;
    }
    
    public WMSService(StorageBroker sb, TileLayerDispatcher tld, RuntimeStats stats, URLMangler urlMangler, GeoWebCacheDispatcher controller) {
        super(SERVICE_WMS);

        this.sb = sb;
        this.tld = tld;
        this.stats = stats;
        this.urlMangler = urlMangler;
        this.controller = controller;
    }

    @Override
    public ConveyorTile getConveyor(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException {
        final String encoding = request.getCharacterEncoding();
        final Map requestParameterMap = request.getParameterMap();

        String[] keys = { "layers", "request", "tiled", "cached", "metatiled", "width", "height" };
        Map<String, String> values = ServletUtils.selectedStringsFromMap(requestParameterMap,
                encoding, keys);

        // Look for layer
        String layers = values.get("layers");
        // Look for requests that are not getmap
        String req = values.get("request");
        if (req != null && !req.equalsIgnoreCase("getmap")) {
            // One more chance
            if (layers == null || layers.length() == 0) {
                layers = ServletUtils.stringFromMap(requestParameterMap, encoding, "layer");
                values.put("LAYERS", layers);
            }

            ConveyorTile tile = new ConveyorTile(sb, layers, request, response);
            tile.setHint(req.toLowerCase());
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }
        if (layers == null) {
            throw new ServiceException("Unable to parse layers parameter from request.");
        }

        // Check whether this request is missing tiled=true
        final boolean tiled = Boolean.valueOf(values.get("tiled"));
        if (proxyNonTiledRequests && tiled) {
            ConveyorTile tile = new ConveyorTile(sb, layers, request, response);
            tile.setHint(req);
            tile.setRequestHandler(Conveyor.RequestHandler.SERVICE);
            return tile;
        }

        TileLayer tileLayer = tld.getTileLayer(layers);

        String[] paramKeys = { "format", "srs", "bbox" };
        final Map<String, String> paramValues = ServletUtils.selectedStringsFromMap(
                requestParameterMap, encoding, paramKeys);

        final Map<String, String> fullParameters = tileLayer.getModifiableParameters(
                requestParameterMap, encoding);

        final MimeType mimeType;
        String format = paramValues.get("format");
        try {
            mimeType = MimeType.createFromFormat(format);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, " + format);
        }

        final SRS srs;
        {
            String requestSrs = paramValues.get("srs");
            if (requestSrs == null) {
                throw new ServiceException("No SRS specified");
            }
            srs = SRS.getSRS(requestSrs);
        }

        final BoundingBox bbox;
        {
            String requestBbox = paramValues.get("bbox");
            try {
                bbox = new BoundingBox(requestBbox);
                if (bbox == null || !bbox.isSane()) {
                    throw new ServiceException("The bounding box parameter (" + requestBbox
                            + ") is missing or not sane");
                }
            } catch (NumberFormatException nfe) {
                throw new ServiceException("The bounding box parameter (" + requestBbox
                        + ") is invalid");
            }
        }

        final int tileWidth = Integer.parseInt(values.get("width"));
        final int tileHeight = Integer.parseInt(values.get("height"));

        final List<GridSubset> crsMatchingSubsets = tileLayer.getGridSubsetsForSRS(srs);
        if (crsMatchingSubsets.isEmpty()) {
            throw new ServiceException("Unable to match requested SRS " + srs
                    + " to those supported by layer");
        }

        long[] tileIndexTarget = new long[3];
        GridSubset gridSubset;
        {
            GridSubset bestMatch = findBestMatchingGrid(bbox, crsMatchingSubsets, tileWidth,
                    tileHeight, tileIndexTarget);
            if (bestMatch == null) {
                // proceed as it used to be
                gridSubset = crsMatchingSubsets.get(0);
                tileIndexTarget = null;
            } else {
                gridSubset = bestMatch;
            }
        }
        
        if (fullWMS) {
            // If we support full WMS we need to do a few tests to determine whether
            // this is a request that requires us to recombine tiles to respond.
            long[] tileIndex = null;
            if (tileIndexTarget == null) {
                try {
                    tileIndex = gridSubset.closestIndex(bbox);
                } catch (GridMismatchException gme) {
                    // Do nothing, the null is info enough
                }
            } else {
                tileIndex = tileIndexTarget;
            }

            if (tileIndex == null || gridSubset.getTileWidth() != tileWidth
                    || gridSubset.getTileHeight() != tileHeight
                    || !bbox.equals(gridSubset.boundsFromIndex(tileIndex), 0.02)) {
                log.debug("Recombinining tiles to respond to WMS request");
                ConveyorTile tile = new ConveyorTile(sb, layers, request, response);
                tile.setHint("getmap");
                tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
                return tile;
            }
        }

        long[] tileIndex = tileIndexTarget == null ? gridSubset.closestIndex(bbox)
                : tileIndexTarget;

        gridSubset.checkTileDimensions(tileWidth, tileHeight);

        return new ConveyorTile(sb, layers, gridSubset.getName(), tileIndex, mimeType,
                fullParameters, request, response);
    }

    public void handleRequest(Conveyor conv) throws GeoWebCacheException {

        ConveyorTile tile = (ConveyorTile) conv;
        
        String servletPrefix=null;
        if (controller!=null) servletPrefix=controller.getServletPrefix();
        
        String servletBase = ServletUtils.getServletBaseURL(conv.servletReq, servletPrefix);
        String context = ServletUtils.getServletContextPath(conv.servletReq, SERVICE_PATH, servletPrefix);

        if (tile.getHint() != null) {
            if (tile.getHint().equalsIgnoreCase("getcapabilities")) {
                WMSGetCapabilities wmsCap = new WMSGetCapabilities(tld, tile.servletReq, servletBase, context, urlMangler);
                wmsCap.writeResponse(tile.servletResp);
            } else if (tile.getHint().equalsIgnoreCase("getmap")) {
                WMSTileFuser wmsFuser = new WMSTileFuser(tld, sb, tile.servletReq);
                try {
                    wmsFuser.writeResponse(tile.servletResp, stats);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else if (tile.getHint().equalsIgnoreCase("getfeatureinfo")) {
                handleGetFeatureInfo(tile);
            } else {
                WMSRequests.handleProxy(tld, tile);
            }
        } else {
            throw new GeoWebCacheException("The WMS Service would love to help, "
                    + "but has no idea what you're trying to do?"
                    + "Please include request URL if you file a bug report.");
        }
    }

    /**
     * Handles a getfeatureinfo request
     * 
     * @param conv
     */
    private void handleGetFeatureInfo(ConveyorTile tile) throws GeoWebCacheException {
        TileLayer tl = tld.getTileLayer(tile.getLayerId());

        if (tl == null) {
            throw new GeoWebCacheException(tile.getLayerId() + " is unknown.");
        }

        String[] keys = { "x", "y", "srs", "info_format", "bbox", "height", "width" };
        Map<String, String> values = ServletUtils.selectedStringsFromMap(
                tile.servletReq.getParameterMap(), tile.servletReq.getCharacterEncoding(), keys);

        // TODO Arent we missing some format stuff here?
        GridSubset gridSubset = tl.getGridSubsetForSRS(SRS.getSRS(values.get("srs")));

        BoundingBox bbox = null;
        try {
            bbox = new BoundingBox(values.get("bbox"));
        } catch (NumberFormatException nfe) {
            log.debug(nfe.getMessage());
        }

        if (bbox == null || !bbox.isSane()) {
            throw new ServiceException("The bounding box parameter (" + values.get("srs")
                    + ") is missing or not sane");
        }

        // long[] tileIndex = gridSubset.closestIndex(bbox);

        MimeType mimeType;
        try {
            mimeType = MimeType.createFromFormat(values.get("info_format"));
        } catch (MimeException me) {
            throw new GeoWebCacheException("The info_format parameter ("
                    + values.get("info_format") + ")is missing or not recognized.");
        }

        ConveyorTile gfiConv = new ConveyorTile(sb, tl.getName(), gridSubset.getName(), null,
                mimeType, null, tile.servletReq, tile.servletResp);
        gfiConv.setTileLayer(tl);

        int x, y;
        try {
            x = Integer.parseInt(values.get("x"));
            y = Integer.parseInt(values.get("y"));
        } catch (NumberFormatException nfe) {
            throw new GeoWebCacheException(
                    "The parameters for x and y must both be positive integers.");
        }

        int height, width;
        try {
            height = Integer.parseInt(values.get("height"));
            width = Integer.parseInt(values.get("width"));
        } catch (NumberFormatException nfe) {
            throw new GeoWebCacheException(
                    "The parameters for height and width must both be positive integers.");
        }

        Resource data = tl.getFeatureInfo(gfiConv, bbox, height, width, x, y);

        try {
            tile.servletResp.setContentType(mimeType.getMimeType());
            ServletOutputStream outputStream = tile.servletResp.getOutputStream();
            data.transferTo(Channels.newChannel(outputStream));
            outputStream.flush();
        } catch (IOException ioe) {
            tile.servletResp.setStatus(500);
            log.error(ioe.getMessage());
        }

    }

    public void setFullWMS(String trueFalse) {
        this.fullWMS = Boolean.parseBoolean(trueFalse);
        if (this.fullWMS) {
            log.info("Will recombine tiles for non-tiling clients.");
        } else {
            log.info("Will NOT recombine tiles for non-tiling clients.");
        }
    }

    public void setProxyRequests(String trueFalse) {
        this.proxyRequests = Boolean.parseBoolean(trueFalse);
        if (this.proxyRequests) {
            log.info("Will proxy requests to backend that are not getmap or getcapabilities.");
        } else {
            log.info("Will NOT proxy non-getMap requests to backend.");
        }
    }

    public void setProxyNonTiledRequests(String trueFalse) {
        this.proxyNonTiledRequests = Boolean.parseBoolean(trueFalse);
        if (this.proxyNonTiledRequests) {
            log.info("Will proxy requests that miss tiled=true to backend.");
        } else {
            log.info("Will NOT proxy requests that miss tiled=true to backend.");
        }
    }
}
