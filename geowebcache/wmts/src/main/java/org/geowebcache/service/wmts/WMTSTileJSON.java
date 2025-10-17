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
 * <p>Copyright 2021
 */
package org.geowebcache.service.wmts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geotools.util.logging.Logging;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileJSONProvider;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.meta.TileJSON;
import org.geowebcache.layer.meta.VectorLayerMetadata;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.URLMangler;

public class WMTSTileJSON {

    private static Logger log = Logging.getLogger(WMTSTileJSON.class.getName());
    private final String restBaseUrl;
    private String style;
    private ConveyorTile convTile;
    private static final String ENDING_DIGITS_REGEX = "([0-9]+)$";
    private static final Pattern PATTERN_DIGITS = Pattern.compile(ENDING_DIGITS_REGEX);

    public WMTSTileJSON(
            ConveyorTile convTile, String baseUrl, String contextPath, String style, URLMangler urlMangler) {
        this.convTile = convTile;
        this.style = style;
        this.restBaseUrl = urlMangler.buildURL(baseUrl, contextPath, WMTSService.REST_PATH);
    }

    public void writeResponse(TileLayer layer) {
        TileJSONProvider provider = (TileJSONProvider) layer;
        TileJSON json = provider.getTileJSON();

        List<String> urls = new ArrayList<>();
        MimeType mimeType = convTile.getMimeType();
        Set<String> gridSubSets = layer.getGridSubsets();
        for (String gridSubSet : gridSubSets) {
            addTileUrl(layer, gridSubSet, mimeType, urls);
        }
        List<VectorLayerMetadata> vectorLayers = json.getLayers();
        if (vectorLayers != null && !vectorLayers.isEmpty() && !mimeType.isVector()) {
            // Removing vectorLayers info when requesting a raster format
            json.setLayers(null);
        }

        String[] tileUrls = urls.toArray(new String[urls.size()]);
        json.setTiles(tileUrls);
        convTile.servletResp.setStatus(HttpServletResponse.SC_OK);
        convTile.servletResp.setContentType(ApplicationMime.json.getMimeType());

        try {
            @SuppressWarnings("PMD.CloseResource") // managed by servlet container
            OutputStream os = convTile.servletResp.getOutputStream();

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.writeValue(os, json);
            os.flush();
        } catch (IOException ioe) {
            log.fine("Caught IOException" + ioe.getMessage());
        }
    }

    private void addTileUrl(TileLayer layer, String gridSubSet, MimeType mimeType, List<String> urls) {
        GridSubset grid = layer.getGridSubset(gridSubSet);
        int zoomLevelStart = -1;
        int start = -1;
        String zoomLevelPrefix = "";
        for (String gridName : grid.getGridNames()) {
            Matcher matcherName = PATTERN_DIGITS.matcher(gridName);
            if (!matcherName.find()) {
                throw new IllegalArgumentException("Zoom level has no numeric value:" + gridName);
            } else {
                start = matcherName.start(0);
            }
            if (zoomLevelStart != -1 && start != zoomLevelStart) {
                throw new IllegalArgumentException("Zoom levels are not sharing the same not-numeric prefix");
            }
            if (zoomLevelStart == -1) {
                zoomLevelPrefix = gridName.substring(0, start);
            }
            zoomLevelStart = start;
        }

        String tileUrl = restBaseUrl
                + "/"
                + layer.getName()
                + "/"
                + (style != null ? (style + "/") : "")
                + gridSubSet
                + "/"
                + zoomLevelPrefix
                + "{z}"
                + "/{y}/{x}"
                + "?format="
                + mimeType;
        urls.add(tileUrl);
    }
}
