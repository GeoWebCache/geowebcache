/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.service.wmts;

import com.google.common.base.Strings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterException;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.TileJSONProvider;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.HttpErrorCodeException;
import org.geowebcache.service.OWSException;
import org.geowebcache.service.Service;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;
import org.springframework.http.MediaType;

public class WMTSService extends Service {

    public static final String SERVICE_WMTS = "wmts";
    public static final String SERVICE_PATH = "/" + GeoWebCacheDispatcher.TYPE_SERVICE + "/" + SERVICE_WMTS;
    public static final String REST_PATH = SERVICE_PATH + "/rest";
    public static final String GET_CAPABILITIES = "getcapabilities";
    public static final String GET_FEATUREINFO = "getfeatureinfo";
    public static final String GET_TILE = "gettile";
    public static final String GET_TILEJSON = "gettilejson";

    private static final String STYLE_HINT = ";style=";

    enum RequestType {
        TILE,
        CAPABILITIES,
        FEATUREINFO,
        TILEJSON
    }

    static final String buildRestPattern(int numPathElements, boolean hasStyle) {
        if (!hasStyle) {
            return ".*/service/wmts/rest" + Strings.repeat("/([^/]+)", numPathElements);
        } else {
            return ".*/service/wmts/rest/([^/]+)/([^/]*)" + Strings.repeat("/([^/]+)", numPathElements - 2);
        }
    }

    enum RestRequest {
        // "/{layer}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}"
        TILE(buildRestPattern(5, false), RequestType.TILE, false),
        // "/{layer}/{style}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}",
        TILE_STYLE(buildRestPattern(6, true), RequestType.TILE, true),
        // "/{layer}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}/{j}/{i}"
        FEATUREINFO(buildRestPattern(7, false), RequestType.FEATUREINFO, false),
        // "/{layer}/{style}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}/{j}/{i}",
        FEATUREINFO_STYLE(buildRestPattern(8, true), RequestType.FEATUREINFO, true),
        // "/{layer}/tilejson/{tileformat}"
        TILEJSON(buildRestPattern(3, false), RequestType.TILEJSON, false),
        // "/{layer}/{style}/tilejson/{tileformat}"
        TILEJSON_STYLE(buildRestPattern(4, true), RequestType.TILEJSON, true);

        final Pattern pattern;
        final RequestType type;
        final boolean hasStyle;

        RestRequest(String pattern, RequestType type, boolean hasStyle) {
            this.pattern = Pattern.compile(pattern);
            this.type = type;
            this.hasStyle = hasStyle;
        }

        /** Returns the parsed KVP, or null if the path does not match the request pattern */
        public Map<String, String> toKVP(HttpServletRequest request) {
            final Matcher matcher = pattern.matcher(request.getPathInfo());
            if (!matcher.matches()) {
                return null;
            }
            Map<String, String> values = new HashMap<>();
            // go through the pattern and extract the actual request
            // leverage the predictable path structure to use a single parsing sequence for all
            // requests
            int i = 1;
            String req = null;
            switch (type) {
                case FEATUREINFO:
                    req = GET_FEATUREINFO;
                    break;
                case TILEJSON:
                    req = GET_TILEJSON;
                    break;
                default:
                    req = GET_TILE;
                    break;
            }
            final boolean isFeatureInfo = type == RequestType.FEATUREINFO;
            values.put("request", req);
            values.put("layer", matcher.group(i++));
            if (hasStyle) {
                values.put("style", matcher.group(i++));
            }
            if (type != RequestType.TILEJSON) {
                values.put("tilematrixset", matcher.group(i++));
                values.put("tilematrix", matcher.group(i++));
                values.put("tilerow", matcher.group(i++));
                values.put("tilecol", matcher.group(i++));
                if (isFeatureInfo) {
                    values.put("j", matcher.group(i++));
                    values.put("i", matcher.group(i++));
                }
            } else {
                values.put("tileformat", matcher.group(++i));
            }
            if (request.getParameter("format") != null) {
                if (isFeatureInfo) {
                    values.put("infoformat", request.getParameter("format"));
                } else {
                    values.put("format", request.getParameter("format"));
                }
            }
            return values;
        }
    }

    // private static Logger log =
    // Logging.getLogger(org.geowebcache.service.wmts.WMTSService.class);

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gsb;

    private RuntimeStats stats;

    private URLMangler urlMangler = new NullURLMangler();

    private GeoWebCacheDispatcher controller = null;

    private ServerConfiguration mainConfiguration;

    // list of this service extensions ordered by their priority
    private final List<WMTSExtension> extensions = new ArrayList<>();

    private SecurityDispatcher securityDispatcher;

    /** Protected no-argument constructor to allow run-time instrumentation */
    protected WMTSService() {
        super(SERVICE_WMTS);
        extensions.addAll(GeoWebCacheExtensions.extensions(WMTSExtension.class));
    }

    public WMTSService(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb, RuntimeStats stats) {
        super(SERVICE_WMTS);

        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
        this.stats = stats;
        extensions.addAll(GeoWebCacheExtensions.extensions(WMTSExtension.class));
    }

    public WMTSService(
            StorageBroker sb,
            TileLayerDispatcher tld,
            GridSetBroker gsb,
            RuntimeStats stats,
            URLMangler urlMangler,
            GeoWebCacheDispatcher controller) {
        super(SERVICE_WMTS);

        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
        this.stats = stats;
        this.urlMangler = urlMangler;
        this.controller = controller;
        extensions.addAll(GeoWebCacheExtensions.extensions(WMTSExtension.class));
    }

    @Override
    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException, OWSException {

        // let's see if we have any extension that wants to provide a conveyor for this request
        for (WMTSExtension extension : extensions) {
            Conveyor conveyor = extension.getConveyor(request, response, sb);
            if (conveyor != null) {
                // this extension provides a conveyor for this request, we are done
                return conveyor;
            }
        }

        if (request.getPathInfo() != null && request.getPathInfo().contains("service/wmts/rest")) {
            return getRestConveyor(request, response);
        }

        String[] keys = {
            "layer",
            "request",
            "style",
            "format",
            "infoformat",
            "tilematrixset",
            "tilematrix",
            "tilerow",
            "tilecol",
            "tileformat",
            "i",
            "j"
        };
        String encoding = request.getCharacterEncoding();
        Map<String, String> values = ServletUtils.selectedStringsFromMap(request.getParameterMap(), encoding, keys);
        return getKvpConveyor(request, response, values);
    }

    public Conveyor getRestConveyor(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException, OWSException {
        // CITE compliance, if the representation is not available a 406 should be returned
        // This is also the behavior mandated by the HTTP standard
        String accept = request.getHeader("Accept");
        if (accept != null) {
            List<MediaType> mediaTypes = MediaType.parseMediaTypes(accept);
            boolean representationAvailable = false;
            for (MediaType mediaType : mediaTypes) {
                if (mediaType.includes(MediaType.APPLICATION_XML)) {
                    representationAvailable = true;
                    break;
                }
            }
            if (!representationAvailable) throw new HttpErrorCodeException(406, "Representation not available");
        }

        final String path = request.getPathInfo();

        // special simpler case for GetCapabilities
        if (path.endsWith("/service/wmts/rest/WMTSCapabilities.xml")) {
            ConveyorTile tile = new ConveyorTile(sb, null, request, response);
            tile.setHint(GET_CAPABILITIES);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }

        // all other paths are handled via the RestRequest enumeration, matching patterns and
        // extracting variables
        for (RestRequest restRequest : RestRequest.values()) {
            Map<String, String> values = restRequest.toKVP(request);
            if (values != null) {
                return getKvpConveyor(request, response, values);
            }
        }

        // we implement all WMTS supported request, this means that the provided request name is
        // invalid
        throw new HttpErrorCodeException(404, "Unknown resource " + request.getPathInfo());
    }

    public Conveyor getKvpConveyor(HttpServletRequest request, HttpServletResponse response, Map<String, String> values)
            throws GeoWebCacheException, OWSException {
        // let's see if we have any extension that wants to provide a conveyor for this request
        for (WMTSExtension extension : extensions) {
            Conveyor conveyor = extension.getConveyor(request, response, sb);
            if (conveyor != null) {
                // this extension provides a conveyor for this request, we are done
                return conveyor;
            }
        }

        // check if we need to be CITE strictly compliant
        boolean isCitecompliant = isCiteCompliant();
        if (isCitecompliant) {
            performCiteValidation(request);
        }

        String req = values.get("request");
        if (req == null) {
            // OWSException(httpCode, exceptionCode, locator, exceptionText);
            throw new OWSException(400, "MissingParameterValue", "request", "Missing Request parameter");
        } else {
            req = req.toLowerCase();
        }

        if (isCitecompliant) {
            String acceptedVersions = getParameterValue("AcceptVersions", request);
            // if provided handle accepted versions parameter
            if (acceptedVersions != null) {
                // we only support version 1.0.0, so make sure that's one of the accepted versions
                List<String> versions = Arrays.asList(acceptedVersions.split("\\s*,\\s*"));
                if (!versions.contains("1.0.0")) {
                    // no supported version is accepted
                    throw new OWSException(
                            400,
                            "VersionNegotiationFailed",
                            "null",
                            "List of versions in AcceptVersions parameter value, in GetCapabilities "
                                    + "operation request, did not include any version supported by this server.");
                }
            }
        }

        if (req.equals(GET_TILE)) {
            if (isCitecompliant) {
                boolean isRestRequest = isRestRequest(request);
                // we need to make sure that a style was provided, otherwise GWC will just assume
                // the default one
                if (!isRestRequest && getParameterValue("Style", request) == null) {
                    // mandatory STYLE query parameter is missing
                    throw new OWSException(
                            400, "MissingParameterValue", "Style", "Mandatory Style query parameter not provided.");
                }
            }
            ConveyorTile tile = getTile(values, request, response, RequestType.TILE);
            return tile;
        } else if (req.equals(GET_CAPABILITIES)) {
            ConveyorTile tile = new ConveyorTile(sb, values.get("layer"), request, response);
            tile.setHint(req);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        } else if (req.equals(GET_FEATUREINFO)) {
            ConveyorTile tile = getTile(values, request, response, RequestType.FEATUREINFO);
            tile.setHint(req);
            tile.setRequestHandler(Conveyor.RequestHandler.SERVICE);
            return tile;
        } else if (req.equals(GET_TILEJSON)) {
            ConveyorTile tile = new ConveyorTile(sb, values.get("layer"), request, response);
            String format = values.get("tileformat");
            tile.setMimeType(MimeType.createFromExtension(format));
            String hint = req;
            // I Will need the style when setting up the TileJSON tiles url
            String style = values.get("style");
            if (style != null) {
                hint += (STYLE_HINT + style);
            }
            tile.setHint(hint);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        } else {
            // we implement all WMTS supported request, this means that the provided request name is
            // invalid
            throw new OWSException(
                    400, "InvalidParameterValue", "request", "Invalid request name '%s'.".formatted(req));
        }
    }

    private ConveyorTile getTile(
            Map<String, String> values, HttpServletRequest request, HttpServletResponse response, RequestType reqType)
            throws OWSException {
        String encoding = request.getCharacterEncoding();

        String layer = values.get("layer");
        if (layer == null) {
            throw new OWSException(400, "MissingParameterValue", "LAYER", "Missing LAYER parameter");
        }

        TileLayer tileLayer = null;

        try {
            tileLayer = tld.getTileLayer(layer);
        } catch (GeoWebCacheException e) {
            throw new OWSException(400, "InvalidParameterValue", "LAYER", "LAYER " + layer + " is not known.");
        }

        Map<String, String[]> rawParameters = new HashMap<>(request.getParameterMap());
        Map<String, String> filteringParameters;
        try {

            /*
             * Merge values with request parameter
             */
            for (Entry<String, String> e : values.entrySet()) {
                rawParameters.put(e.getKey(), new String[] {e.getValue()});
            }

            // WMTS uses the "STYLE" instead of "STYLES"
            for (Entry<String, String[]> e : rawParameters.entrySet()) {
                if (e.getKey().equalsIgnoreCase("STYLE")) {
                    rawParameters.put("STYLES", e.getValue());
                    break;
                }
            }
            filteringParameters = tileLayer.getModifiableParameters(rawParameters, encoding);

        } catch (ParameterException e) {
            throw new OWSException(e.getHttpCode(), e.getExceptionCode(), e.getLocator(), e.getMessage());
        } catch (GeoWebCacheException e) {
            throw new OWSException(
                    500,
                    "NoApplicableCode",
                    "",
                    e.getMessage() + " while fetching modifiable parameters for LAYER " + layer);
        }

        MimeType mimeType = null;
        // the format should be present and valid also for GetFeatureInfo, while in CITE compliance
        // mode
        if (reqType == RequestType.TILE) {
            String format = values.get("format");
            if (format == null) {
                throw new OWSException(
                        400, "MissingParameterValue", "FORMAT", "Unable to determine requested FORMAT, " + format);
            }
            try {
                mimeType = MimeType.createFromFormat(format);
            } catch (MimeException me) {
                throw new OWSException(
                        400, "InvalidParameterValue", "FORMAT", "Unable to determine requested FORMAT, " + format);
            }
        } else {
            String infoFormat = values.get("infoformat");

            if (infoFormat == null) {
                throw new OWSException(
                        400, "MissingParameterValue", "INFOFORMAT", "Parameter INFOFORMAT was not provided");
            }
            try {
                mimeType = MimeType.createFromFormat(infoFormat);
            } catch (MimeException me) {
                throw new OWSException(
                        400,
                        "InvalidParameterValue",
                        "INFOFORMAT",
                        "Unable to determine requested INFOFORMAT, " + infoFormat);
            }

            if (isCiteCompliant() && !isRestRequest(request)) {
                String format = values.get("format");
                if (format == null) {
                    throw new OWSException(
                            400, "MissingParameterValue", "FORMAT", "Unable to determine requested FORMAT, " + format);
                }
            }
        }

        final String tilematrixset = values.get("tilematrixset");
        if (tilematrixset == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEMATRIXSET", "No TILEMATRIXSET specified");
        }

        GridSubset gridSubset = tileLayer.getGridSubset(tilematrixset);
        if (gridSubset == null) {
            throw new OWSException(
                    400,
                    "InvalidParameterValue",
                    "TILEMATRIXSET",
                    "Unable to match requested TILEMATRIXSET " + tilematrixset + " to those supported by layer");
        }

        final String tileMatrix = values.get("tilematrix");
        if (tileMatrix == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEMATRIX", "No TILEMATRIX specified");
        }
        long z = gridSubset.getGridIndex(tileMatrix);

        if (z < 0) {
            throw new OWSException(400, "InvalidParameterValue", "TILEMATRIX", "Unknown TILEMATRIX " + tileMatrix);
        }

        // WMTS has 0 in the top left corner -> flip y value
        final String tileRow = values.get("tilerow");
        if (tileRow == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEROW", "No TILEROW specified");
        }

        final long tilesHigh = gridSubset.getNumTilesHigh((int) z);

        long y = tilesHigh - Long.parseLong(tileRow) - 1;

        String tileCol = values.get("tilecol");
        if (tileCol == null) {
            throw new OWSException(400, "MissingParameterValue", "TILECOL", "No TILECOL specified");
        }
        long x = Long.parseLong(tileCol);

        long[] gridCov = gridSubset.getCoverage((int) z);

        if (x < gridCov[0] || x > gridCov[2]) {
            throw new OWSException(
                    400,
                    "TileOutOfRange",
                    "TILECOL",
                    "Column " + x + " is out of range, min: " + gridCov[0] + " max:" + gridCov[2]);
        }

        if (y < gridCov[1] || y > gridCov[3]) {
            long minRow = tilesHigh - gridCov[3] - 1;
            long maxRow = tilesHigh - gridCov[1] - 1;

            throw new OWSException(
                    400,
                    "TileOutOfRange",
                    "TILEROW",
                    "Row " + tileRow + " is out of range, min: " + minRow + " max:" + maxRow);
        }

        long[] tileIndex = {x, y, z};

        try {
            gridSubset.checkCoverage(tileIndex);
        } catch (OutsideCoverageException e) {

        }

        ConveyorTile convTile = new ConveyorTile(
                sb,
                layer,
                gridSubset.getName(),
                tileIndex,
                mimeType,
                rawParameters,
                filteringParameters,
                request,
                response);

        convTile.setTileLayer(tileLayer);

        return convTile;
    }

    @Override
    public void handleRequest(Conveyor conv) throws OWSException, GeoWebCacheException {

        // let's see if any extension wants to handle this request
        for (WMTSExtension extension : extensions) {
            if (extension.handleRequest(conv)) {
                // the request was handled by this extension
                return;
            }
        }

        // no extension wants to handle this request, so let's proceed with a normal execution
        ConveyorTile tile = (ConveyorTile) conv;

        String servletPrefix = null;
        if (controller != null) servletPrefix = controller.getServletPrefix();

        String servletBase = ServletUtils.getServletBaseURL(conv.servletReq, servletPrefix);
        String context = ServletUtils.getServletContextPath(
                conv.servletReq, new String[] {SERVICE_PATH, REST_PATH}, servletPrefix);

        if (tile.getHint() != null) {
            if (tile.getHint().equals(GET_CAPABILITIES)) {
                WMTSGetCapabilities wmsGC = new WMTSGetCapabilities(
                        tld, gsb, tile.servletReq, servletBase, context, urlMangler, extensions);
                wmsGC.writeResponse(tile.servletResp, stats);

            } else if (tile.getHint().equals(GET_FEATUREINFO)) {
                getSecurityDispatcher().checkSecurity(tile);
                ConveyorTile convTile = (ConveyorTile) conv;
                WMTSGetFeatureInfo wmsGFI = new WMTSGetFeatureInfo(convTile);
                wmsGFI.writeResponse(stats);
            } else if (tile.getHint().startsWith(GET_TILEJSON)) {
                getSecurityDispatcher().checkSecurity(tile);
                ConveyorTile convTile = (ConveyorTile) conv;
                TileLayer layer = convTile.getLayer();
                String hint = tile.getHint();
                String style = null;
                int styleIndex = hint.indexOf(STYLE_HINT);
                if (styleIndex != -1) {
                    style = hint.substring(styleIndex + STYLE_HINT.length());
                }

                if (layer instanceof TileJSONProvider provider) {
                    // in GetCapabilities we are adding a TileJSON resource URL
                    // only when the layer supports TileJSON.
                    // That information allows us to return a 404 when
                    // someone is asking a TileJSON when not supported.
                    if (!provider.supportsTileJSON()) {
                        throw new HttpErrorCodeException(404, "TileJSON Not supported");
                    }
                    WMTSTileJSON wmtsTileJSON = new WMTSTileJSON(convTile, servletBase, context, style, urlMangler);
                    wmtsTileJSON.writeResponse(layer);
                }
            }
        }
    }

    void addExtension(WMTSExtension extension) {
        extensions.add(extension);
    }

    public Collection<WMTSExtension> getExtensions() {
        return Collections.unmodifiableCollection(extensions);
    }

    public void setSecurityDispatcher(SecurityDispatcher secDisp) {
        this.securityDispatcher = secDisp;
    }

    protected SecurityDispatcher getSecurityDispatcher() {
        return securityDispatcher;
    }

    /**
     * Sets GWC main configuration.
     *
     * @param mainConfiguration GWC main configuration
     */
    public void setMainConfiguration(ServerConfiguration mainConfiguration) {
        this.mainConfiguration = mainConfiguration;
    }

    /**
     * Return the GWC configuration used by this WMTS service instance.
     *
     * @return GWC main configuration
     */
    ServerConfiguration getMainConfiguration() {
        return mainConfiguration;
    }

    /**
     * Helper method that checks if WMTS implementation should be CITE strictly compliant.
     *
     * @return TRUE if GWC main configuration or at least one of the WMTS extensions forces CITE compliance
     */
    protected boolean isCiteCompliant() {
        // let's see if main GWC configuration forces WMTS implementation to be CITE compliant
        if (mainConfiguration != null && mainConfiguration.isWmtsCiteCompliant()) {
            return true;
        }
        // let's see if at least one of the extensions forces CITE compliant mode
        for (WMTSExtension extension : extensions) {
            if (extension.getServiceInformation() != null
                    && extension.getServiceInformation().isCiteCompliant()) {
                return true;
            }
        }
        // we are not in CITE compliant mode
        return false;
    }

    /** Helper method that performs CITE tests mandatory validations. */
    private static void performCiteValidation(HttpServletRequest request) throws OWSException {
        // paths validation are not done for WMTS REST API
        if (isRestRequest(request)) {
            return;
        }
        // base path should end with WMTS
        String basePath = request.getPathInfo();
        String[] paths = basePath.split("/");
        String lastPath = paths[paths.length - 1];
        if (!lastPath.equalsIgnoreCase("WMTS")) {
            // invalid base path, not found should be returned
            throw new OWSException(404, "NoApplicableCode", "request", "Service or request not found");
        }
        // service query parameter is mandatory and should be equal to WMTS
        validateWmtsServiceName("wmts", request);
    }

    /**
     * Helper method that just checks if current WMTS request is in the context of a REST API call, certain OGC
     * validations don't make sense in that context.
     */
    private static boolean isRestRequest(HttpServletRequest request) {
        // rest/wmts is always lowercase
        return request.getPathInfo().contains("service/wmts/rest");
    }

    /**
     * Checks if the URL base path extracted service name matches the HTTP request SERVICE query parameter value. If the
     * HTTP request doesn't contains any SERVICE query parameter an OWS exception will be returned.
     *
     * <p>This validation only happens for WMTS service and if CITE strict compliance is activated.
     *
     * @param pathServiceName service name extracted from the URL base path
     * @param request the original HTTP request
     * @throws OWSException if the URL path extracted service name and the HTTP request service name don't match
     */
    private static void validateWmtsServiceName(String pathServiceName, HttpServletRequest request)
            throws OWSException {
        if (pathServiceName == null || !pathServiceName.equalsIgnoreCase("WMTS")) {
            // not an OGC service, so nothing to do
            return;
        }
        // let's see if the service path and requested service match
        String requestedServiceName = getParameterValue("SERVICE", request);
        if (requestedServiceName == null) {
            // mandatory service query parameter not provided
            throw new OWSException(
                    400, "MissingParameterValue", "service", "Mandatory SERVICE query parameter not provided.");
        }
        if (!pathServiceName.equalsIgnoreCase(requestedServiceName)) {
            // bad request, the URL path service and the requested service don't match
            throw new OWSException(
                    400,
                    "InvalidParameterValue",
                    "service",
                    "URL path service '%s' don't match the requested service '%s'."
                            .formatted(pathServiceName, requestedServiceName));
        }
    }

    /**
     * Search in a non case sensitive way for a query parameter in the provided HTTP request. If the query parameter is
     * found is first value is returned otherwise NULL is returned.
     *
     * @param parameterName query parameter name to search
     * @param request HTTP request
     * @return the first value of the query parameter if it exists otherwise NULL
     */
    private static String getParameterValue(String parameterName, HttpServletRequest request) {
        if (parameterName == null) {
            // nothing to do
            return null;
        }
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(parameterName)) {
                // we found our parameter
                String[] values = entry.getValue();
                return values != null ? values[0] : null;
            }
        }
        // parameter not found
        return null;
    }
}
