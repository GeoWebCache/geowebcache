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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 * 
 */
package org.geowebcache.service.wmts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.OWSException;
import org.geowebcache.service.Service;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class WMTSService extends Service  {

    public static final String SERVICE_WMTS = "wmts";
    public static final String SERVICE_PATH = "/"+GeoWebCacheDispatcher.TYPE_SERVICE+"/"+SERVICE_WMTS;
    public static final String REST_PATH = "/"+GeoWebCacheDispatcher.TYPE_REST+"/"+SERVICE_WMTS;

    enum RequestType {
        TILE, CAPABILITIES, FEATUREINFO
    };

    // private static Log log = LogFactory.getLog(org.geowebcache.service.wmts.WMTSService.class);

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gsb;

    private RuntimeStats stats;
    
    private URLMangler urlMangler = new NullURLMangler();
    
    private GeoWebCacheDispatcher controller = null;

    // list of this service extensions ordered by their priority
    private final List<WMTSExtension> extensions = new ArrayList<>();
    
    private SecurityDispatcher securityDispatcher;

    /**
     * Protected no-argument constructor to allow run-time instrumentation
     */
    protected WMTSService(){
        super(SERVICE_WMTS);
        extensions.addAll(GeoWebCacheExtensions.extensions(WMTSExtension.class));
    }
    
    public WMTSService(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb,
            RuntimeStats stats) {
        super(SERVICE_WMTS);

        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
        this.stats = stats;
        extensions.addAll(GeoWebCacheExtensions.extensions(WMTSExtension.class));
    }
    
    public WMTSService(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb,
            RuntimeStats stats, URLMangler urlMangler, GeoWebCacheDispatcher controller) {
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
        for(WMTSExtension extension : extensions) {
            Conveyor conveyor = extension.getConveyor(request, response, sb);
            if (conveyor != null) {
                // this extension provides a conveyor for this request, we are done
                return conveyor;
            }
        }
        
        String encoding = request.getCharacterEncoding();
        String[] keys = { "layer", "request", "style", "format", "infoformat", "tilematrixset", "tilematrix",
                "tilerow", "tilecol", "i", "j" };
        Map<String, String> values = ServletUtils.selectedStringsFromMap(request.getParameterMap(),
                encoding, keys);
        return getConveyor(request, response, values);
    }
    
    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response, Map<String, String> values)
            throws GeoWebCacheException, OWSException {

        // let's see if we have any extension that wants to provide a conveyor for this request
        for(WMTSExtension extension : extensions) {
            Conveyor conveyor = extension.getConveyor(request, response, sb);
            if (conveyor != null) {
                // this extension provides a conveyor for this request, we are done
                return conveyor;
            }
        }
        
        String req = values.get("request");
        if (req == null) {
            // OWSException(httpCode, exceptionCode, locator, exceptionText);
            throw new OWSException(400, "MissingParameterValue", "request",
                    "Missing Request parameter");
        } else {
            req = req.toLowerCase();
        }

        if (req.equals("gettile")) {
            ConveyorTile tile = getTile(values, request, response, RequestType.TILE);
            return tile;
        } else if (req.equals("getcapabilities")) {
            ConveyorTile tile = new ConveyorTile(sb, values.get("layer"), request, response);
            tile.setHint(req);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        } else if (req.equals("getfeatureinfo")) {
            ConveyorTile tile = getTile(values, request, response, RequestType.FEATUREINFO);
            tile.setHint(req);
            tile.setRequestHandler(Conveyor.RequestHandler.SERVICE);
            return tile;
        } else {
            throw new OWSException(501, "OperationNotSupported", "request", req
                    + " is not implemented");
        }
    }

    private ConveyorTile getTile(Map<String, String> values, HttpServletRequest request,
            HttpServletResponse response, RequestType reqType) throws OWSException {
        String encoding = request.getCharacterEncoding();

        String layer = values.get("layer");
        if (layer == null) {
            throw new OWSException(400, "MissingParameterValue", "LAYER", "Missing LAYER parameter");
        }

        TileLayer tileLayer = null;

        try {
            tileLayer = tld.getTileLayer(layer);
        } catch (GeoWebCacheException e) {
            throw new OWSException(400, "InvalidParameterValue", "LAYER", "LAYER " + layer
                    + " is not known.");
        }

        Map<String, String[]> rawParameters = new HashMap<>(request.getParameterMap());
        Map<String, String> filteringParameters;
        try {
      
            /*
             * Merge values with request parameter
             */
            for (Entry<String, String> e : values.entrySet()) {
                rawParameters.put(e.getKey(), new String[] { e.getValue() });
            }
            
            // WMTS uses the "STYLE" instead of "STYLES"
            for(Entry<String, String[]> e:rawParameters.entrySet()){
                if(e.getKey().equalsIgnoreCase("STYLE")) {
                    rawParameters.put("STYLES", e.getValue());
                    break;
                }
            }
            filteringParameters = tileLayer.getModifiableParameters(rawParameters, encoding);

        } catch (GeoWebCacheException e) {
            throw new OWSException(500, "NoApplicableCode", "", e.getMessage()
                    + " while fetching modifiable parameters for LAYER " + layer);
        }

        MimeType mimeType = null;
        if (reqType == RequestType.TILE) {
            String format = values.get("format");
            if (format == null) {
                throw new OWSException(400, "MissingParameterValue", "FORMAT",
                        "Unable to determine requested FORMAT, " + format);
            }
            try {
                mimeType = MimeType.createFromFormat(format);
            } catch (MimeException me) {
                throw new OWSException(400, "InvalidParameterValue", "FORMAT",
                        "Unable to determine requested FORMAT, " + format);
            }
        } else {
            String infoFormat = values.get("infoformat");
            
            if (infoFormat == null) {
                throw new OWSException(400, "MissingParameterValue", "INFOFORMAT",
                        "Parameter INFOFORMAT was not provided");
            }
            try {
                mimeType = MimeType.createFromFormat(infoFormat);
            } catch (MimeException me) {
                throw new OWSException(400, "InvalidParameterValue", "INFOFORMAT",
                        "Unable to determine requested INFOFORMAT, " + infoFormat);
            }
        }

        final String tilematrixset = values.get("tilematrixset");
        if (tilematrixset == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEMATRIXSET",
                    "No TILEMATRIXSET specified");
        }

        GridSubset gridSubset = tileLayer.getGridSubset(tilematrixset);
        if (gridSubset == null) {
            throw new OWSException(400, "InvalidParameterValue", "TILEMATRIXSET",
                    "Unable to match requested TILEMATRIXSET " + tilematrixset
                            + " to those supported by layer");
        }

        final String tileMatrix = values.get("tilematrix");
        if (tileMatrix == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEMATRIX",
                    "No TILEMATRIX specified");
        }
        long z = gridSubset.getGridIndex(tileMatrix);

        if (z < 0) {
            throw new OWSException(400, "InvalidParameterValue", "TILEMATRIX",
                    "Unknown TILEMATRIX " + tileMatrix);
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
            throw new OWSException(400, "MissingParameterValue", "TILECOL",
                    "No TILECOL specified");
        }
        long x = Long.parseLong(tileCol);

        long[] gridCov = gridSubset.getCoverage((int) z);

        if (x < gridCov[0] || x > gridCov[2]) {
            throw new OWSException(400, "TileOutOfRange", "TILECOLUMN", "Column " + x
                    + " is out of range, min: " + gridCov[0] + " max:" + gridCov[2]);
        }

        if (y < gridCov[1] || y > gridCov[3]) {
            long minRow = tilesHigh - gridCov[3] - 1;
            long maxRow = tilesHigh - gridCov[1] - 1;

            throw new OWSException(400, "TileOutOfRange", "TILEROW", "Row " + tileRow
                    + " is out of range, min: " + minRow + " max:" + maxRow);
        }

        long[] tileIndex = { x, y, z };

        try {
            gridSubset.checkCoverage(tileIndex);
        } catch (OutsideCoverageException e) {

        }

        ConveyorTile convTile = new ConveyorTile(sb, layer, gridSubset.getName(), tileIndex,
                mimeType, rawParameters, filteringParameters, request, response);

        convTile.setTileLayer(tileLayer);

        return convTile;
    }

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
        
        String servletPrefix=null;
        if (controller!=null) servletPrefix=controller.getServletPrefix();
        
        String servletBase = ServletUtils.getServletBaseURL(conv.servletReq, servletPrefix);
        String context = ServletUtils.getServletContextPath(conv.servletReq, new String[]{SERVICE_PATH, REST_PATH}, servletPrefix);

        if (tile.getHint() != null) {
            if (tile.getHint().equals("getcapabilities")) {
                WMTSGetCapabilities wmsGC = new WMTSGetCapabilities(tld, gsb, tile.servletReq, servletBase, context, urlMangler, extensions);
                wmsGC.writeResponse(tile.servletResp, stats);

            } else if (tile.getHint().equals("getfeatureinfo")) {
                getSecurityDispatcher().checkSecurity(tile);
                ConveyorTile convTile = (ConveyorTile) conv;
                WMTSGetFeatureInfo wmsGFI = new WMTSGetFeatureInfo(convTile);
                wmsGFI.writeResponse(stats);
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

}
