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
package org.geowebcache.service.wms;

import static org.geowebcache.grid.GridUtil.findBestMatchingGrid;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BaseConfiguration;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridMismatchException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.ProxyLayer;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.TextMime;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class WMSService extends Service {
    public static final String GEOWEBCACHE_WMS_PROXY_REQUEST_WHITELIST =
            "GEOWEBCACHE_WMS_PROXY_REQUEST_WHITELIST";

    public static final String SERVICE_WMS = "wms";

    static final String SERVICE_PATH = "/" + GeoWebCacheDispatcher.TYPE_SERVICE + "/" + SERVICE_WMS;

    private static Logger log =
            Logging.getLogger(org.geowebcache.service.wms.WMSService.class.getName());

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

    private String hintsConfig = "DEFAULT";

    private WMSUtilities utility;

    private SecurityDispatcher securityDispatcher;

    /** Protected no-argument constructor to allow run-time instrumentation */
    protected WMSService() {
        super(SERVICE_WMS);
    }

    public WMSService(StorageBroker sb, TileLayerDispatcher tld, RuntimeStats stats) {
        super(SERVICE_WMS);

        this.sb = sb;
        this.tld = tld;
        this.stats = stats;
    }

    public WMSService(
            StorageBroker sb,
            TileLayerDispatcher tld,
            RuntimeStats stats,
            URLMangler urlMangler,
            GeoWebCacheDispatcher controller) {
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
        final Map<String, String[]> requestParameterMap = request.getParameterMap();

        String[] keys = {"layers", "request", "tiled", "cached", "metatiled", "width", "height"};
        Map<String, String> values =
                ServletUtils.selectedStringsFromMap(requestParameterMap, encoding, keys);

        // Look for layer
        String layers = values.get("layers");

        // Get the TileLayer
        TileLayer tileLayer = null;
        if (layers != null) {
            tileLayer = tld.getTileLayer(layers);
        }
        // Look for requests that are not getmap
        String req = values.get("request");
        if (req != null && !req.equalsIgnoreCase("getmap")) {
            // If no LAYERS specified, try using LAYER.
            if (layers == null || layers.length() == 0) {
                layers = ServletUtils.stringFromMap(requestParameterMap, encoding, "layer");
                values.put("LAYERS", layers);

                if (layers != null) {
                    tileLayer = tld.getTileLayer(layers);
                }
            }

            Map<String, String> filteringParameters = null;
            // If tileLayer is not null, then request parameters are extracted from it-
            if (tileLayer != null) {
                filteringParameters =
                        tileLayer.getModifiableParameters(requestParameterMap, encoding);
            }

            // Creation of a Conveyor Tile with a fake Image/png format and the associated
            // parameters.
            ConveyorTile tile =
                    new ConveyorTile(
                            sb,
                            layers,
                            null,
                            null,
                            ImageMime.png,
                            filteringParameters,
                            request,
                            response);
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

        String[] paramKeys = {"format", "srs", "bbox"};
        final Map<String, String> paramValues =
                ServletUtils.selectedStringsFromMap(requestParameterMap, encoding, paramKeys);

        final Map<String, String> fullParameters =
                tileLayer.getModifiableParameters(requestParameterMap, encoding);

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
                    throw new ServiceException(
                            "The bounding box parameter ("
                                    + requestBbox
                                    + ") is missing or not sane");
                }
            } catch (NumberFormatException nfe) {
                throw new ServiceException(
                        "The bounding box parameter (" + requestBbox + ") is invalid");
            }
        }

        final int tileWidth = Integer.parseInt(values.get("width"));
        final int tileHeight = Integer.parseInt(values.get("height"));

        final List<GridSubset> crsMatchingSubsets = tileLayer.getGridSubsetsForSRS(srs);
        if (crsMatchingSubsets.isEmpty()) {
            throw new ServiceException(
                    "Unable to match requested SRS " + srs + " to those supported by layer");
        }

        long[] tileIndexTarget = new long[3];
        GridSubset gridSubset;
        {
            GridSubset bestMatch =
                    findBestMatchingGrid(
                            bbox, crsMatchingSubsets, tileWidth, tileHeight, tileIndexTarget);
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

            if (tileIndex == null
                    || gridSubset.getTileWidth() != tileWidth
                    || gridSubset.getTileHeight() != tileHeight
                    || !bbox.equals(gridSubset.boundsFromIndex(tileIndex), 0.02)) {
                log.fine("Recombinining tiles to respond to WMS request");
                ConveyorTile tile = new ConveyorTile(sb, layers, request, response);
                tile.setHint("getmap");
                tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
                return tile;
            }
        }

        long[] tileIndex =
                tileIndexTarget == null ? gridSubset.closestIndex(bbox) : tileIndexTarget;

        gridSubset.checkTileDimensions(tileWidth, tileHeight);

        return new ConveyorTile(
                sb,
                layers,
                gridSubset.getName(),
                tileIndex,
                mimeType,
                fullParameters,
                request,
                response);
    }

    @Override
    public void handleRequest(Conveyor conv) throws GeoWebCacheException {

        ConveyorTile tile = (ConveyorTile) conv;

        String servletPrefix = null;
        if (controller != null) servletPrefix = controller.getServletPrefix();

        String servletBase = ServletUtils.getServletBaseURL(conv.servletReq, servletPrefix);
        String context =
                ServletUtils.getServletContextPath(conv.servletReq, SERVICE_PATH, servletPrefix);

        if (tile.getHint() != null) {
            if (tile.getHint().equalsIgnoreCase("getcapabilities")) {
                WMSGetCapabilities wmsCap =
                        new WMSGetCapabilities(
                                tld, tile.servletReq, servletBase, context, urlMangler);
                wmsCap.writeResponse(tile.servletResp);
            } else if (tile.getHint().equalsIgnoreCase("getmap")) {
                getSecurityDispatcher().checkSecurity(tile);
                WMSTileFuser wmsFuser = getFuser(tile.servletReq);
                try {
                    wmsFuser.writeResponse(tile.servletResp, stats);
                } catch (SecurityException e) {
                    throw e;
                } catch (Exception e) {
                    log.log(Level.FINE, e.getMessage(), e);
                }
            } else if (tile.getHint().equalsIgnoreCase("getfeatureinfo")) {
                getSecurityDispatcher().checkSecurity(tile);
                handleGetFeatureInfo(tile);
            } else {
                getSecurityDispatcher().checkSecurity(tile);
                checkProxyRequest(tile.getHint());
                // see if we can proxy the request
                TileLayer tl = tld.getTileLayer(tile.getLayerId());

                if (tl == null) {
                    throw new GeoWebCacheException(tile.getLayerId() + " is unknown.");
                }

                if (tl instanceof ProxyLayer) {
                    ((ProxyLayer) tl).proxyRequest(tile);
                } else {
                    throw new GeoWebCacheException(
                            tile.getLayerId() + " cannot cascade WMS requests.");
                }
            }
        } else {
            throw new GeoWebCacheException(
                    "The WMS Service would love to help, "
                            + "but has no idea what you're trying to do?"
                            + "Please include request URL if you file a bug report.");
        }
    }

    protected WMSTileFuser getFuser(HttpServletRequest servletReq) throws GeoWebCacheException {
        WMSTileFuser wmsFuser = new WMSTileFuser(tld, sb, servletReq);
        wmsFuser.setSecurityDispatcher(getSecurityDispatcher());
        // Setting of the applicationContext
        wmsFuser.setApplicationContext(utility.getApplicationContext());
        // Setting of the hintConfiguration if present
        wmsFuser.setHintsConfiguration(hintsConfig);
        return wmsFuser;
    }

    /** Handles a getfeatureinfo request */
    private void handleGetFeatureInfo(ConveyorTile tile) throws GeoWebCacheException {
        TileLayer tl = tld.getTileLayer(tile.getLayerId());

        if (tl == null) {
            throw new GeoWebCacheException(tile.getLayerId() + " is unknown.");
        }

        String[] keys = {"x", "y", "srs", "info_format", "bbox", "height", "width"};
        Map<String, String> values =
                ServletUtils.selectedStringsFromMap(
                        tile.servletReq.getParameterMap(),
                        tile.servletReq.getCharacterEncoding(),
                        keys);

        // TODO Arent we missing some format stuff here?
        GridSubset gridSubset =
                tl.getGridSubsetsForSRS(SRS.getSRS(values.get("srs"))).iterator().next();

        BoundingBox bbox = null;
        try {
            bbox = new BoundingBox(values.get("bbox"));
        } catch (NumberFormatException nfe) {
            log.fine(nfe.getMessage());
        }

        if (bbox == null || !bbox.isSane()) {
            throw new ServiceException(
                    "The bounding box parameter ("
                            + values.get("srs")
                            + ") is missing or not sane");
        }

        // long[] tileIndex = gridSubset.closestIndex(bbox);

        MimeType mimeType;
        try {
            mimeType = MimeType.createFromFormat(values.get("info_format"));
        } catch (MimeException me) {
            throw new GeoWebCacheException(
                    "The info_format parameter ("
                            + values.get("info_format")
                            + ")is missing or not recognized.");
        }

        if (mimeType == null) {
            if (tl.getInfoMimeTypes().contains(TextMime.txt)) {
                mimeType = TextMime.txt;
            } else {
                // use first as default
                mimeType = tl.getInfoMimeTypes().get(0);
            }
        }
        if (!tl.getInfoMimeTypes().contains(mimeType)) {
            throw new GeoWebCacheException(
                    "The info_format parameter ("
                            + values.get("info_format")
                            + ") is not supported.");
        }

        ConveyorTile gfiConv =
                new ConveyorTile(
                        sb,
                        tl.getName(),
                        gridSubset.getName(),
                        null,
                        mimeType,
                        tile.getFilteringParameters(),
                        tile.servletReq,
                        tile.servletResp);
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
            @SuppressWarnings("PMD.CloseResource") // managed by servlet container
            ServletOutputStream outputStream = tile.servletResp.getOutputStream();
            data.transferTo(Channels.newChannel(outputStream));
            outputStream.flush();
        } catch (IOException ioe) {
            tile.servletResp.setStatus(500);
            log.log(Level.SEVERE, ioe.getMessage());
        }
    }

    public void setFullWMS(String trueFalse) {
        // Selection of the configurations
        List<TileLayerConfiguration> configs =
                new ArrayList<>(GeoWebCacheExtensions.extensions(TileLayerConfiguration.class));
        // Selection of the TileLayerConfiguration file associated to geowebcache.xml
        ServerConfiguration gwcXMLconfig = null;
        for (BaseConfiguration config : configs) {
            if (config instanceof XMLConfiguration) {
                gwcXMLconfig = (ServerConfiguration) config;
                break;
            }
        }
        // From the configuration file the "fullWMS" parameter is searched
        Boolean wmsFull = null;
        if (gwcXMLconfig != null) {
            wmsFull = gwcXMLconfig.isFullWMS();
        }

        if (wmsFull != null) {
            this.fullWMS = wmsFull;
        } else {
            this.fullWMS = Boolean.parseBoolean(trueFalse);
        }
        // Log if fullWMS is enabled
        if (this.fullWMS) {
            log.config("Will recombine tiles for non-tiling clients.");
        } else {
            log.config("Will NOT recombine tiles for non-tiling clients.");
        }
    }

    public void setProxyRequests(String trueFalse) {
        this.proxyRequests = Boolean.parseBoolean(trueFalse);
        if (this.proxyRequests) {
            log.config("Will proxy requests to backend that are not getmap or getcapabilities.");
        } else {
            log.config("Will NOT proxy non-getMap requests to backend.");
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

    public void setHintsConfig(String hintsConfig) {
        this.hintsConfig = hintsConfig;
    }

    public void setUtility(WMSUtilities utility) {
        this.utility = utility;
    }

    protected Collection<String> getDefaultProxyRequestWhitelist() {
        if (getSecurityDispatcher().isSecurityEnabled()) {
            return Arrays.asList("getlegendgraphic");
        } else {
            return Arrays.asList("*");
        }
    }

    protected Collection<String> getProxyRequestWhitelist() {
        return Optional.ofNullable(
                        GeoWebCacheExtensions.getProperty(GEOWEBCACHE_WMS_PROXY_REQUEST_WHITELIST))
                .map(list -> list.split(";"))
                .map(Arrays::stream)
                .map(
                        stream ->
                                stream.map(String::toLowerCase)
                                        .map(String::trim)
                                        .collect(Collectors.toList()))
                .map(x -> (Collection<String>) x)
                .orElse(getDefaultProxyRequestWhitelist());
    }

    protected void checkProxyRequest(String request) {
        if (getProxyRequestWhitelist().stream()
                .noneMatch(pattern -> pattern.equals("*") || pattern.equals(request))) {
            throw new SecurityException(
                    "WMS Request " + request + " is not on request proxy whitelist");
        }
    }

    public void setSecurityDispatcher(SecurityDispatcher securityDispatcher) {
        this.securityDispatcher = securityDispatcher;
    }

    protected SecurityDispatcher getSecurityDispatcher() {
        return securityDispatcher;
    }
}
