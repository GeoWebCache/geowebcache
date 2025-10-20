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
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.client5.http.utils.DateUtils;
import org.geotools.util.logging.Logging;
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
import org.geowebcache.layer.EmptyTileException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.http.MediaType;

/**
 * Utility methods that can be used to write a string as http response</br> The response can be a valid one or contains
 * an error status code</br> The HTTP response can be rendered as HTML or XML
 *
 * @author sandr
 */
public final class ResponseUtils {

    private static Logger log = Logging.getLogger(ResponseUtils.class);

    private ResponseUtils() {}

    /**
     * Helper method that will get a tile from the target service that correspond to the conveyor data. Security
     * permissions will be checked and the tile will be directly wrote to the output stream.
     *
     * @param secDispatcher security dispatcher
     * @param conv tile request information
     * @param layerName layer name
     * @param tileLayerDispatcher tiles dispatcher
     * @param defaultStorageFinder storage finder
     * @param runtimeStats runtime statistics
     */
    public static void writeTile(
            SecurityDispatcher secDispatcher,
            Conveyor conv,
            String layerName,
            TileLayerDispatcher tileLayerDispatcher,
            DefaultStorageFinder defaultStorageFinder,
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
        } catch (EmptyTileException e) {
            writeEmpty(
                    defaultStorageFinder,
                    convTile,
                    e.getMessage(),
                    runtimeStats,
                    e.getMime().getMimeType(),
                    e.getContents());
        } catch (OutsideCoverageException e) {
            writeEmpty(defaultStorageFinder, convTile, e.getMessage(), runtimeStats);
        }
    }

    /** Happy ending, sets the headers and writes the response back to the client. */
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

        final String lastModified = DateUtils.formatStandardDate(new Date(tileTimeStamp).toInstant());
        servletResp.setHeader("Last-Modified", lastModified);

        final Date ifModifiedSince;
        if (ifModSinceHeader != null && ifModSinceHeader.length() > 0) {

            ifModifiedSince = Date.from(DateUtils.parseStandardDate(ifModSinceHeader));
            // the HTTP header has second precision
            long ifModSinceSeconds = 1000 * (ifModifiedSince.getTime() / 1000);
            long tileTimeStampSeconds = 1000 * (tileTimeStamp / 1000);
            if (ifModSinceSeconds >= tileTimeStampSeconds) {
                httpCode = HttpServletResponse.SC_NOT_MODIFIED;
                blob = null;
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
        writeFixedResponse(servletResp, httpCode, mimeType, blob, cacheResult, contentLength, runtimeStats);
    }

    private static void writeEmpty(
            DefaultStorageFinder defaultStorageFinder,
            ConveyorTile tile,
            String message,
            RuntimeStats runtimeStats,
            String mimeType,
            ByteArrayResource emptyTileContents) {
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

        // handle no-content in case we have to return no result at all (e.g., expected for pbf)
        int status = emptyTileContents == null ? 204 : 200;

        writeFixedResponse(tile.servletResp, status, mimeType, emptyTileContents, CacheResult.OTHER, runtimeStats);
    }

    /** Writes a transparent, 8 bit PNG to avoid having clients like OpenLayers showing lots of pink tiles */
    private static void writeEmpty(
            DefaultStorageFinder defaultStorageFinder, ConveyorTile tile, String message, RuntimeStats runtimeStats) {
        writeEmpty(
                defaultStorageFinder,
                tile,
                message,
                runtimeStats,
                ImageMime.png.getMimeType(),
                loadBlankTile(defaultStorageFinder));
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
    public static void writeFixedResponse(
            HttpServletResponse response,
            int httpCode,
            String contentType,
            Resource resource,
            CacheResult cacheRes,
            RuntimeStats runtimeStats) {

        int contentLength = (int) (resource == null ? -1 : resource.getSize());
        writeFixedResponse(response, httpCode, contentType, resource, cacheRes, contentLength, runtimeStats);
    }

    /**
     * Helper method that writes an HTTP response setting the provided HTTP code. Using the provided content length.
     *
     * @param response HTTP response
     * @param httpCode HTTP status code
     * @param contentType HTTP response content type
     * @param resource HTTP response resource
     * @param cacheRes provides information about the tile retrieving
     * @param contentLength HTTP response content length
     * @param runtimeStats runtime statistics
     */
    public static void writeFixedResponse(
            HttpServletResponse response,
            int httpCode,
            String contentType,
            Resource resource,
            CacheResult cacheRes,
            int contentLength,
            RuntimeStats runtimeStats) {

        response.setStatus(httpCode);
        response.setContentType(contentType);

        response.setContentLength(contentLength);
        if (resource != null) {
            try (OutputStream os = response.getOutputStream();
                    WritableByteChannel channel = Channels.newChannel(os)) {
                resource.transferTo(channel);
                runtimeStats.log(contentLength, cacheRes);

            } catch (IOException ioe) {
                log.fine("Caught IOException: " + ioe.getMessage() + "\n\n" + ioe.toString());
            }
        }
    }

    private static ByteArrayResource loadBlankTile(DefaultStorageFinder defaultStorageFinder) {
        ByteArrayResource blankTile = null;
        String blankTilePath = defaultStorageFinder.findEnvVar(DefaultStorageFinder.GWC_BLANK_TILE_PATH);

        if (blankTilePath != null) {
            File fh = new File(blankTilePath);
            if (fh.exists() && fh.canRead() && fh.isFile()) {
                long fileSize = fh.length();
                blankTile = new ByteArrayResource(new byte[(int) fileSize]);
                try {
                    loadBlankTile(blankTile, fh.toURI().toURL());
                } catch (IOException e) {
                    log.log(Level.SEVERE, e.getMessage(), e);
                }

                if (fileSize == blankTile.getSize()) {
                    log.info("Loaded blank tile from " + blankTilePath);
                } else {
                    log.log(Level.SEVERE, "Failed to load blank tile from " + blankTilePath);
                }

            } else {
                log.log(Level.SEVERE, "" + blankTilePath + " does not exist or is not readable.");
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
                log.log(Level.SEVERE, ioe.getMessage());
            }
        }

        return blankTile;
    }

    private static void loadBlankTile(Resource blankTile, URL source) throws IOException {
        try (InputStream inputStream = source.openStream();
                ReadableByteChannel ch = Channels.newChannel(inputStream)) {
            blankTile.transferFrom(ch);
        } catch (IOException e) {
            log.log(Level.FINE, e.getMessage(), e);
        }
    }

    /**
     * Wrapper method for writing an error back to the client, and logging it at the same time.
     *
     * @param response where to write to
     * @param httpCode the HTTP code to provide
     * @param errorMsg the actual error message, human readable
     */
    public static void writeErrorPage(
            HttpServletResponse response, int httpCode, String errorMsg, RuntimeStats runtimeStats) {
        log.fine(errorMsg);
        errorMsg = "<html>\n"
                + ServletUtils.gwcHtmlHeader("../", "GWC Error")
                + "<body>\n"
                + ServletUtils.gwcHtmlLogoLink("../")
                + "<h4>"
                + httpCode
                + ": "
                + ServletUtils.disableHTMLTags(errorMsg)
                + "</h4>"
                + "</body></html>\n";
        writePage(response, httpCode, errorMsg, runtimeStats, MediaType.TEXT_HTML_VALUE);
    }

    /**
     * Writes an HTTP response setting as it content the provided exception message encoded in XML. The original error
     * is also logged.
     *
     * @param response HTTP response
     * @param httpCode HTTP status code
     * @param errorMsg error message encoded in XML
     * @param runtimeStats runtime statistics
     */
    public static void writeErrorAsXML(
            HttpServletResponse response, int httpCode, String errorMsg, RuntimeStats runtimeStats) {
        log.fine(errorMsg);
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
    public static void writePage(
            HttpServletResponse response, int httpCode, String message, RuntimeStats runtimeStats, String contentType) {
        Resource res = null;
        if (message != null) {
            res = new ByteArrayResource(message.getBytes());
        }
        ResponseUtils.writeFixedResponse(response, httpCode, contentType, res, CacheResult.OTHER, runtimeStats);
    }
}
