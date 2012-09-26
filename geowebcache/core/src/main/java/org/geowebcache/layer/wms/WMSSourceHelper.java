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
package org.geowebcache.layer.wms;

import java.util.Map;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.ServletUtils;

/**
 * Builds WMS requests to gather a certain tile or meta tile. The actual communication with the
 * server is delegated to subclasses (might be a real HTTP request, but also an in process one in
 * the case of GeoServer)
 */
public abstract class WMSSourceHelper {

    private int concurrency = 32;
    private int backendTimetout;

    abstract protected void makeRequest(TileResponseReceiver tileRespRecv, WMSLayer layer,
            Map<String, String> wmsParams, String expectedMimeType, Resource target)
            throws GeoWebCacheException;

    public void makeRequest(WMSMetaTile metaTile, Resource target) throws GeoWebCacheException {

        Map<String, String> wmsParams = metaTile.getWMSParams();
        WMSLayer layer = metaTile.getLayer();
        String format = metaTile.getRequestFormat().getFormat();

        makeRequest(metaTile, layer, wmsParams, format, target);
    }

    public void makeRequest(ConveyorTile tile, Resource target) throws GeoWebCacheException {
        WMSLayer layer = (WMSLayer) tile.getLayer();

        GridSubset gridSubset = layer.getGridSubset(tile.getGridSetId());

        Map<String, String> wmsParams = layer.getWMSRequestTemplate(tile.getMimeType(),
                WMSLayer.RequestType.MAP);

        wmsParams.put("FORMAT", tile.getMimeType().getMimeType());
        wmsParams.put("SRS", layer.backendSRSOverride(gridSubset.getSRS()));
        wmsParams.put("HEIGHT", String.valueOf(gridSubset.getTileHeight()));
        wmsParams.put("WIDTH", String.valueOf(gridSubset.getTileWidth()));
        // strBuilder.append("&TILED=").append(requestTiled);

        BoundingBox bbox = gridSubset.boundsFromIndex(tile.getTileIndex());

        wmsParams.put("BBOX", bbox.toString());

        Map<String, String> fullParameters = tile.getFullParameters();
        if (fullParameters.isEmpty()) {
            fullParameters = layer.getDefaultParameterFilters();
        }
        wmsParams.putAll(fullParameters);

        if (tile.getMimeType() == XMLMime.kml) {
            // This is a hack for GeoServer to produce regionated KML,
            // but it is unlikely to do much harm, especially since nobody
            // else appears to produce regionated KML at this point
            wmsParams.put("format_options", "mode:superoverlay;overlaymode:auto");
        }

        String mimeType = tile.getMimeType().getMimeType();
        makeRequest(tile, layer, wmsParams, mimeType, target);
    }

    public Resource makeFeatureInfoRequest(ConveyorTile tile, BoundingBox bbox, int height,
            int width, int x, int y) throws GeoWebCacheException {
        WMSLayer layer = (WMSLayer) tile.getLayer();

        GridSubset gridSubset = tile.getGridSubset();

        Map<String, String> wmsParams = layer.getWMSRequestTemplate(tile.getMimeType(),
                WMSLayer.RequestType.FEATUREINFO);

        wmsParams.put("INFO_FORMAT", tile.getMimeType().getFormat());
        wmsParams.put("FORMAT", layer.getDefaultMimeType().getMimeType());
        wmsParams.put("SRS", layer.backendSRSOverride(gridSubset.getSRS()));
        wmsParams.put("HEIGHT", String.valueOf(height));
        wmsParams.put("WIDTH", String.valueOf(width));

        wmsParams.put("BBOX", bbox.toString());

        Map<String, String> fullParameters = tile.getFullParameters();
        if (fullParameters.isEmpty()) {
            fullParameters = layer.getDefaultParameterFilters();
        }
        wmsParams.putAll(fullParameters);

        wmsParams.put("X", String.valueOf(x));
        wmsParams.put("Y", String.valueOf(y));
        
        String featureCount;
        {
            Map<String, String> values = ServletUtils.selectedStringsFromMap(
                tile.servletReq.getParameterMap(), tile.servletReq.getCharacterEncoding(), "feature_count");
            featureCount = values.get("feature_count");
        }
        if(featureCount != null){
            wmsParams.put("FEATURE_COUNT", featureCount);
        }
        
        String mimeType = tile.getMimeType().getMimeType();
        Resource target = new ByteArrayResource(2048);
        makeRequest(tile, layer, wmsParams, mimeType, target);
        return target;
    }

    protected boolean mimeStringCheck(String requestMime, String responseMime) {
        if (responseMime.equalsIgnoreCase(requestMime)) {
            return true;
        } else if (responseMime.startsWith(requestMime)) {
            return true;
        } else if (requestMime.startsWith("image/png") && responseMime.startsWith("image/png")) {
            return true;
        }
        return false;
    }

    /**
     * The levels of concurrent requests this source helper is allowing
     * @return
     */
    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Sets the maximum amount of concurrent requests this source helper will issue
     * @param concurrency
     */
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    /**
     * Sets the backend timeout for HTTP calls
     * @param backendTimeout
     */
    public void setBackendTimeout(int backendTimeout) {
        this.backendTimetout = backendTimeout;
    }
    
    /**
     * Returns the backend timeout for HTTP calls
     */
    public int getBackendTimeout() {
        return this.backendTimetout;
    }
}
