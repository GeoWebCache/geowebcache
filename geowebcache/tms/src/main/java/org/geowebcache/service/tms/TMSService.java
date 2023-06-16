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
 * @author Mikael Nyberg, Copyright 2009
 */
package org.geowebcache.service.tms;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.HttpErrorCodeException;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class TMSService extends Service {

    public static final String SERVICE_TMS = "tms";

    private static final String FLIP_Y = "FLIPY";

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private RuntimeStats stats;

    private GeoWebCacheDispatcher controller = null;

    private TMSDocumentFactory tmsFactory;

    /** Protected no-argument constructor to allow run-time instrumentation */
    protected TMSService() {
        super(SERVICE_TMS);
    }

    public TMSService(
            StorageBroker sb,
            TileLayerDispatcher tld,
            GridSetBroker gsb,
            RuntimeStats stats,
            URLMangler urlMangler,
            GeoWebCacheDispatcher controller) {
        this(sb, stats, controller, new TMSDocumentFactory(tld, gsb, urlMangler));
    }

    public TMSService(
            StorageBroker sb,
            RuntimeStats stats,
            GeoWebCacheDispatcher controller,
            TMSDocumentFactory tmsFactory) {
        super(SERVICE_TMS);
        this.sb = sb;
        this.stats = stats;
        this.controller = controller;
        this.tmsFactory = tmsFactory;
        if (tmsFactory == null) {
            throw new IllegalArgumentException("Specified TMSFactory should not be null ");
        }
        this.tld = tmsFactory.tld;
    }

    public TMSService(
            StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb, RuntimeStats stats) {
        this(sb, tld, gsb, stats, new NullURLMangler(), null);
    }

    @Override
    public ConveyorTile getConveyor(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException {
        final String pathInfo = request.getPathInfo();
        Optional<Map<String, String>> possibleSplit = splitParams(request);
        if (possibleSplit.isPresent()) {
            Map<String, String> split = possibleSplit.get();
            long[] gridLoc = new long[3];
            try {
                gridLoc[0] = Integer.parseInt(split.get("x"));
                gridLoc[1] = Integer.parseInt(split.get("y"));
                gridLoc[2] = Integer.parseInt(split.get("z"));
            } catch (NumberFormatException nfe) {
                throw new ServiceException(
                        "Unable to parse number " + nfe.getMessage() + " from " + pathInfo);
            }
            String layerId = split.get("layerId");

            String gridSetId = split.get("gridSetId");
            if (Objects.isNull(gridSetId)) {
                gridSetId = tld.getTileLayer(layerId).getGridSubsets().iterator().next();
            }
            MimeType mimeType = null;
            String fileExtension = split.get("fileExtension");
            try {
                mimeType = MimeType.createFromExtension(fileExtension);
                if (mimeType == null) {
                    throw new HttpErrorCodeException(400, "Unsupported format: " + fileExtension);
                }
            } catch (MimeException me) {
                throw new ServiceException(
                        "Unable to determine requested format based on extension " + fileExtension);
            }
            try {
                TileLayer tileLayer = tld.getTileLayer(layerId);
                GridSubset gridSubset = tileLayer.getGridSubset(gridSetId);
                if (gridSubset == null) {
                    throw new HttpErrorCodeException(400, "Unsupported gridset: " + gridSetId);
                }

                if (hasFlipY(request)) {
                    final long tilesHigh = gridSubset.getNumTilesHigh((int) gridLoc[2]);
                    gridLoc[1] = tilesHigh - gridLoc[1] - 1;
                }

                gridSubset.checkCoverage(gridLoc);
            } catch (OutsideCoverageException e) {
                throw new HttpErrorCodeException(404, e.getMessage(), e);
            } catch (GeoWebCacheException e) {
                throw new HttpErrorCodeException(400, e.getMessage(), e);
            }
            ConveyorTile ret =
                    new ConveyorTile(
                            sb, layerId, gridSetId, gridLoc, mimeType, null, request, response);
            return ret;
        } else {
            // Not a tile request, lets pass it back out
            ConveyorTile tile = new ConveyorTile(sb, null, request, response);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }
    }

    /** Look for the presence of the flipY parameter */
    private boolean hasFlipY(HttpServletRequest request) {
        Enumeration<String> parameterNames = request.getParameterNames();
        if (parameterNames != null) {
            while (parameterNames.hasMoreElements()) {
                String name = parameterNames.nextElement();
                if (FLIP_Y.equalsIgnoreCase(name)) {
                    String flipY = request.getParameter(name);
                    return Boolean.parseBoolean(flipY);
                }
            }
        }
        return false;
    }

    /**
     * Split the TMS parameters out of the given request
     *
     * @return A map of the parameters with keys {@literal "layerId"}, {@literal "gridSetId"},
     *     {@literal "x"}, {@literal "y"}, {@literal "z"}, and {@literal "fileExtension"}.
     *     Optionally also {@literal "gridSetId"} and {@literal "format"}. Returns an empty Optional
     *     if it can not fill the mandatory entries
     */
    public static Optional<Map<String, String>> splitParams(HttpServletRequest request) {

        // get all elements of the pathInfo after the leading "/tms/1.0.0/" part.
        String pathInfo = request.getPathInfo();
        pathInfo =
                pathInfo.substring(pathInfo.indexOf(TMSDocumentFactory.TILEMAPSERVICE_LEADINGPATH));
        String[] params = pathInfo.split("/");
        // {"tms", "1.0.0", "img states@EPSG:4326", ... }

        int paramsLength = params.length;

        Map<String, String> parsed = new HashMap<>();

        if (params.length < 4) {
            return Optional.empty();
        }

        String[] yExt = params[paramsLength - 1].split("\\.");
        parsed.put("x", params[paramsLength - 2]);
        parsed.put("y", yExt[0]);
        parsed.put("z", params[paramsLength - 3]);

        String layerNameAndSRS = params[2];
        String[] lsf =
                ServletUtils.URLDecode(layerNameAndSRS, request.getCharacterEncoding()).split("@");
        parsed.put("layerId", lsf[0]);
        if (lsf.length >= 3) {
            parsed.put("gridSetId", lsf[1]);
            parsed.put("format", lsf[2]);
        }

        parsed.put("fileExtension", yExt[1]);

        return Optional.of(parsed);
    }

    @Override
    public void handleRequest(Conveyor conv) throws GeoWebCacheException {
        // get all elements of the pathInfo after the leading "/tms/1.0.0/" part.
        String pathInfo = conv.servletReq.getPathInfo();
        pathInfo =
                pathInfo.substring(pathInfo.indexOf(TMSDocumentFactory.TILEMAPSERVICE_LEADINGPATH));
        String[] params = pathInfo.split("/");
        // {"tms", "1.0.0", "img states@EPSG:4326" }

        int paramsLength = params.length;

        String servletPrefix = null;
        if (controller != null) servletPrefix = controller.getServletPrefix();

        String servletBase = ServletUtils.getServletBaseURL(conv.servletReq, servletPrefix);
        String context =
                ServletUtils.getServletContextPath(
                        conv.servletReq, TMSDocumentFactory.SERVICE_PATH, servletPrefix);

        final Charset encoding = StandardCharsets.UTF_8;
        String ret = null;

        if (paramsLength < 2) {
            throw new GeoWebCacheException("Path is too short to be a valid TMS path");
        } else if (paramsLength == 2) {
            String version = params[1];
            if (!version.equals("1.0.0")) {
                throw new GeoWebCacheException(
                        "Unknown version " + version + ", only 1.0.0 is supported.");
            } else {
                ret = tmsFactory.getTileMapServiceDoc(servletBase, context);
            }
        } else {
            String layerNameAndSRS = params[2];
            String layerAtSRS =
                    ServletUtils.URLDecode(layerNameAndSRS, conv.servletReq.getCharacterEncoding());
            String[] layerSRSFormatExtension = layerAtSRS.split("@");

            TileLayer tl = tld.getTileLayer(layerSRSFormatExtension[0]);
            GridSubset gridSub = tl.getGridSubset(layerSRSFormatExtension[1]);
            MimeType mimeType = MimeType.createFromExtension(layerSRSFormatExtension[2]);
            ret = tmsFactory.getTileMapDoc(tl, gridSub, mimeType, servletBase, context);
        }

        byte[] data = ret.getBytes(encoding);
        stats.log(data.length, CacheResult.OTHER);

        conv.servletResp.setStatus(200);
        conv.servletResp.setContentType("text/xml");
        conv.servletResp.setHeader(
                "content-disposition", "inline;filename=tms-getcapabilities.xml");
        try {
            conv.servletResp.getOutputStream().write(data);
        } catch (IOException e) {
            // TODO log error
        }
    }
}
