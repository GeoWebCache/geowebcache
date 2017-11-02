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
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 * 
 */

package org.geowebcache.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.request.RequestFilterException;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.http.MediaType;
/**
 * Utility methods that can be used to write a string as http response</br>
 * The response can be a valid one or contains an error status code</br>
 * The HTTP response can be rendered as HTML or XML
 * @author sandr
 *
 */
public final class ResponseUtils {

    private static Log log = LogFactory.getLog(ResponseUtils.class);

    private ResponseUtils() {
    }

    /**
     * Helper method that will get a tile from the target service that correspond to the conveyor data.
     * Security permissions will be checked and the tile will be directly wrote to the output stream.
     *
     * @param secDispatcher security dispatcher
     * @param conv tile request information
     * @param layerName layer name
     * @param tileLayerDispatcher tiles dispatcher
     * @param defaultStorageFinder storage finder
     * @param runtimeStats runtime statistics
     *
     * @throws GeoWebCacheException
     * @throws RequestFilterException
     * @throws IOException
     */
    public static void writeTile(SecurityDispatcher secDispatcher, Conveyor conv, String layerName,
                                 TileLayerDispatcher tileLayerDispatcher, DefaultStorageFinder defaultStorageFinder,
                                 RuntimeStats runtimeStats)
            throws GeoWebCacheException, RequestFilterException, IOException {
        ConveyorTile convTile = (ConveyorTile) conv;

        // Get the configuration that has to respond to this request
        TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);

        // Apply the filters
        layer.applyRequestFilters(convTile);

        // Throw an exception if not authorized
        secDispatcher.checkSecurity(convTile);

        // Keep the URI
        // tile.requestURI = request.getRequestURI();

        try {
            // A5) Ask the layer to provide the content for the tile
            convTile = layer.getTile(convTile);

            // A6) Write response
            writeData(convTile, runtimeStats);

            // Alternatively:
        } catch (OutsideCoverageException e) {
            writeEmpty(defaultStorageFinder, convTile, e.getMessage(), runtimeStats);
        }
    }

    /**
     * Happy ending, sets the headers and writes the response back to the client.
     */
    private static void writeData(ConveyorTile tile, RuntimeStats runtimeStats) throws IOException {
        HttpServletResponse servletResp = tile.servletResp;
        final HttpServletRequest servletReq = tile.servletReq;

        final CacheResult cacheResult = tile.getCacheResult();
        int httpCode = HttpServletResponse.SC_OK;
        Resource blob = tile.getBlob();
        String mimeType = tile.getMimeType().getMimeType(blob);

        servletResp.setHeader("geowebcache-cache-result", String.valueOf(cacheResult));
        servletResp.setHeader("geowebcache-tile-index", Arrays.toString(tile.getTileIndex()));
        long[] tileIndex = tile.getTileIndex();
        TileLayer layer = tile.getLayer();
        GridSubset gridSubset = layer.getGridSubset(tile.getGridSetId());
        BoundingBox tileBounds = gridSubset.boundsFromIndex(tileIndex);
        servletResp.setHeader("geowebcache-tile-bounds", tileBounds.toString());
        servletResp.setHeader("geowebcache-gridset", gridSubset.getName());
        servletResp.setHeader("geowebcache-crs", gridSubset.getSRS().toString());

        final long tileTimeStamp = tile.getTSCreated();
        final String ifModSinceHeader = servletReq.getHeader("If-Modified-Since");
        // commons-httpclient's DateUtil can encode and decode timestamps formatted as per RFC-1123,
        // which is one of the three formats allowed for Last-Modified and If-Modified-Since headers
        // (e.g. 'Sun, 06 Nov 1994 08:49:37 GMT'). See
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1

        final String lastModified = org.apache.commons.httpclient.util.DateUtil
                .formatDate(new Date(tileTimeStamp));
        servletResp.setHeader("Last-Modified", lastModified);

        final Date ifModifiedSince;
        if (ifModSinceHeader != null && ifModSinceHeader.length() > 0) {
            try {
                ifModifiedSince = DateUtil.parseDate(ifModSinceHeader);
                // the HTTP header has second precision
                long ifModSinceSeconds = 1000 * (ifModifiedSince.getTime() / 1000);
                long tileTimeStampSeconds = 1000 * (tileTimeStamp / 1000);
                if (ifModSinceSeconds >= tileTimeStampSeconds) {
                    httpCode = HttpServletResponse.SC_NOT_MODIFIED;
                    blob = null;
                }
            } catch (DateParseException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Can't parse client's If-Modified-Since header: '" + ifModSinceHeader
                            + "'");
                }
            }
        }

        if (httpCode == HttpServletResponse.SC_OK && tile.getLayer().useETags()) {
            String ifNoneMatch = servletReq.getHeader("If-None-Match");
            String hexTag = Long.toHexString(tileTimeStamp);

            if (ifNoneMatch != null) {
                if (ifNoneMatch.equals(hexTag)) {
                    httpCode = HttpServletResponse.SC_NOT_MODIFIED;
                    blob = null;
                }
            }

            // If we get here, we want ETags but the client did not have the tile.
            servletResp.setHeader("ETag", hexTag);
        }

        int contentLength = (int) (blob == null ? -1 : blob.getSize());
        writeFixedResponse(servletResp, httpCode, mimeType, blob, cacheResult, contentLength,
                runtimeStats);
    }

    /**
     * Writes a transparent, 8 bit PNG to avoid having clients like OpenLayers showing lots of pink
     * tiles
     */
    private static void writeEmpty(DefaultStorageFinder defaultStorageFinder, ConveyorTile tile,
            String message, RuntimeStats runtimeStats) {
        tile.servletResp.setHeader("geowebcache-message", message);
        TileLayer layer = tile.getLayer();
        if (layer != null) {
            layer.setExpirationHeader(tile.servletResp, (int) tile.getTileIndex()[2]);

            if (layer.useETags()) {
                String ifNoneMatch = tile.servletReq.getHeader("If-None-Match");
                if (ifNoneMatch != null && ifNoneMatch.equals("gwc-blank-tile")) {
                    tile.servletResp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                } else {
                    tile.servletResp.setHeader("ETag", "gwc-blank-tile");
                }
            }
        }

        writeFixedResponse(tile.servletResp, 200, ImageMime.png.getMimeType(),
                loadBlankTile(defaultStorageFinder), CacheResult.OTHER, runtimeStats);
    }

    /**
     * Helper method that writes an HTTP response setting the provided HTTP code.
     *
     * @param response HTTP response
     * @param httpCode HTTP status code
     * @param contentType HTTP response content type
     * @param resource HTTP response resource
     * @param cacheRes provides information about the tile retrieving
     * @param runtimeStats runtime statistics
     */
    public static void writeFixedResponse(HttpServletResponse response, int httpCode,
            String contentType, Resource resource, CacheResult cacheRes,
            RuntimeStats runtimeStats) {

        int contentLength = (int) (resource == null ? -1 : resource.getSize());
        writeFixedResponse(response, httpCode, contentType, resource, cacheRes, contentLength,
                runtimeStats);
    }

    /**
     * Helper method that writes an HTTP response setting the provided HTTP code. Using the provided
     * content length.
     *
     * @param response HTTP response
     * @param httpCode HTTP status code
     * @param contentType HTTP response content type
     * @param resource HTTP response resource
     * @param cacheRes provides information about the tile retrieving
     * @param contentLength HTTP response content length
     * @param runtimeStats runtime statistics
     */
    public static void writeFixedResponse(HttpServletResponse response, int httpCode,
            String contentType, Resource resource, CacheResult cacheRes, int contentLength,
            RuntimeStats runtimeStats) {

        response.setStatus(httpCode);
        response.setContentType(contentType);

        response.setContentLength((int) contentLength);
        if (resource != null) {
            try {
                OutputStream os = response.getOutputStream();
                resource.transferTo(Channels.newChannel(os));

                runtimeStats.log(contentLength, cacheRes);

            } catch (IOException ioe) {
                log.debug("Caught IOException: " + ioe.getMessage() + "\n\n" + ioe.toString());
            }
        }
    }

    private static ByteArrayResource loadBlankTile(DefaultStorageFinder defaultStorageFinder) {
        ByteArrayResource blankTile = null;
        String blankTilePath = defaultStorageFinder
                .findEnvVar(DefaultStorageFinder.GWC_BLANK_TILE_PATH);

        if (blankTilePath != null) {
            File fh = new File(blankTilePath);
            if (fh.exists() && fh.canRead() && fh.isFile()) {
                long fileSize = fh.length();
                blankTile = new ByteArrayResource(new byte[(int) fileSize]);
                try {
                    loadBlankTile(blankTile, fh.toURI().toURL());
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }

                if (fileSize == blankTile.getSize()) {
                    log.info("Loaded blank tile from " + blankTilePath);
                } else {
                    log.error("Failed to load blank tile from " + blankTilePath);
                }

            } else {
                log.error("" + blankTilePath + " does not exist or is not readable.");
            }
        }

        // Use the built-in one:
        if (blankTile == null) {
            try {
                URL url = GeoWebCacheDispatcher.class.getResource("blank.png");
                blankTile = new ByteArrayResource();
                loadBlankTile(blankTile, url);
                int ret = (int) blankTile.getSize();
                log.info("Read " + ret + " from blank PNG file (expected 425).");
            } catch (IOException ioe) {
                log.error(ioe.getMessage());
            }
        }

        return blankTile;
    }

    private static void loadBlankTile(Resource blankTile, URL source) throws IOException {
        InputStream inputStream = source.openStream();
        ReadableByteChannel ch = Channels.newChannel(inputStream);
        try {
            blankTile.transferFrom(ch);
        } catch (IOException e) {
            log.debug(e);
        } finally {
            ch.close();
        }
    }

    /**
     * Wrapper method for writing an error back to the client, and logging it at the same time.
     * 
     * @param response
     *            where to write to
     * @param httpCode
     *            the HTTP code to provide
     * @param errorMsg
     *            the actual error message, human readable
     */
    public static void writeErrorPage(HttpServletResponse response, int httpCode, String errorMsg,
            RuntimeStats runtimeStats) {
        log.debug(errorMsg);
        errorMsg = "<html>\n" + ServletUtils.gwcHtmlHeader("../", "GWC Error") + "<body>\n"
                + ServletUtils.gwcHtmlLogoLink("../") + "<h4>" + httpCode + ": "
                + ServletUtils.disableHTMLTags(errorMsg) + "</h4>" + "</body></html>\n";
        writePage(response, httpCode, errorMsg, runtimeStats, MediaType.TEXT_HTML_VALUE);
    }

    /**
     * Writes an HTTP response setting as it content the provided exception message encoded in XML.
     * The original error is also logged.
     *
     * @param response HTTP response
     * @param httpCode HTTP status code
     * @param errorMsg error message encoded in XML
     * @param runtimeStats runtime statistics
     */
    public static void writeErrorAsXML(HttpServletResponse response, int httpCode, String errorMsg,
            RuntimeStats runtimeStats) {
        log.debug(errorMsg);
        writePage(response, httpCode, errorMsg, runtimeStats, MediaType.APPLICATION_XML_VALUE);
    }

    /**
     * Writes an HTTP response setting as it content the provided message and using the provided content type.
     *
     * @param response HTTP response
     * @param httpCode HTTP status code
     * @param message HTTP response content
     * @param runtimeStats runtime statistics
     * @param contentType HTTP response content type
     */
    public static void writePage(HttpServletResponse response, int httpCode, String message,
            RuntimeStats runtimeStats, String contentType) {
        Resource res = null;
        if (message != null) {
            res = new ByteArrayResource(message.getBytes());
        }
        ResponseUtils.writeFixedResponse(response, httpCode, contentType, res, CacheResult.OTHER,
                runtimeStats);
    }

}
