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

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.ServletUtils;

/**
 * Builds WMS requests to gather a certain tile or meta tile. The actual communication with the
 * server is delegated to subclasses (might be a real HTTP request, but also an in process one in
 * the case of GeoServer)
 */
public abstract class WMSSourceHelper {

    abstract protected byte[] makeRequest(TileResponseReceiver tileRespRecv,
            WMSLayer layer, String wmsParams, String expectedMimeType)
            throws GeoWebCacheException;

    
    public byte[] makeRequest(WMSMetaTile metaTile) throws GeoWebCacheException {

        String wmsParams = metaTile.getWMSParams();
        WMSLayer layer = metaTile.getLayer();

        return makeRequest(metaTile, layer, wmsParams, metaTile.getRequestFormat().getFormat());
    }
    
    public byte[] makeRequest(ConveyorTile tile) throws GeoWebCacheException {
        WMSLayer layer = (WMSLayer) tile.getLayer();

        GridSubset gridSubset = tile.getGridSubset();

        String wmsParams = layer.getWMSRequestTemplate(tile.getMimeType(), WMSLayer.RequestType.MAP);

        StringBuilder strBuilder = new StringBuilder(wmsParams);

        strBuilder.append("&FORMAT=").append(tile.getMimeType().getFormat());
        strBuilder.append("&SRS=").append(layer.backendSRSOverride(gridSubset.getSRS()));
        strBuilder.append("&HEIGHT=").append(gridSubset.getTileHeight());
        strBuilder.append("&WIDTH=").append(gridSubset.getTileWidth());
        // strBuilder.append("&TILED=").append(requestTiled);

        BoundingBox bbox = gridSubset.boundsFromIndex(tile.getTileIndex());

        strBuilder.append("&BBOX=").append(bbox);

        strBuilder.append(tile.getFullParameters());
        
        if(tile.getMimeType() == XMLMime.kml) {
            // This is a hack for GeoServer to produce regionated KML, 
            // but it is unlikely to do much harm, especially since nobody
            // else appears to produce regionated KML at this point
            strBuilder.append("&format_options=").append(ServletUtils.URLEncode("mode:superoverlay;overlaymode:auto")); 
        }

        return makeRequest(tile, layer, strBuilder.toString(), tile.getMimeType().getMimeType());
    }

    
    public byte[] makeFeatureInfoRequest(ConveyorTile tile, int x, int y)
            throws GeoWebCacheException {
        WMSLayer layer = (WMSLayer) tile.getLayer();

        GridSubset gridSubset = tile.getGridSubset();

        String wmsParams = layer.getWMSRequestTemplate(tile.getMimeType(),WMSLayer.RequestType.FEATUREINFO);

        StringBuilder strBuilder = new StringBuilder(wmsParams);
        strBuilder.append("&INFO_FORMAT=").append(tile.getMimeType().getFormat());
        strBuilder.append("&FORMAT=").append(tile.getMimeType().getFormat());
        strBuilder.append("&SRS=").append(layer.backendSRSOverride(gridSubset.getSRS()));
        strBuilder.append("&HEIGHT=").append(gridSubset.getTileHeight());
        strBuilder.append("&WIDTH=").append(gridSubset.getTileWidth());

        BoundingBox bbox = gridSubset.boundsFromIndex(tile.getTileIndex());

        strBuilder.append("&BBOX=").append(bbox);

        strBuilder.append(tile.getFullParameters());

        strBuilder.append("&X=").append(x);
        strBuilder.append("&Y=").append(y);

        return makeRequest(tile, layer, strBuilder.toString(), tile.getMimeType().getMimeType());
    }
    
    protected boolean mimeStringCheck(String requestMime, String responseMime) {
        if (responseMime.equalsIgnoreCase(requestMime)) {
            return true;
        } else if (responseMime.startsWith(requestMime)) {
            return true;
        } else if (requestMime.startsWith("image/png")
                && responseMime.startsWith("image/png")) {
            return true;
        }
        return false;
    }
}
