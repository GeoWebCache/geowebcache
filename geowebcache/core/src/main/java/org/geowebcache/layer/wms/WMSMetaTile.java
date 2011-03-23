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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;

public class WMSMetaTile extends MetaTile {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSMetaTile.class);

    protected WMSLayer wmsLayer = null;

    protected boolean requestTiled = false;

    protected String fullParameters;

    /**
     * Used for requests by clients
     * 
     * @param profile
     * @param initGridPosition
     */
    protected WMSMetaTile(WMSLayer layer, GridSubset gridSubset, MimeType responseFormat,
            FormatModifier formatModifier, long[] tileGridPosition, int metaX, int metaY,
            String fullParameters) {
        super(gridSubset, responseFormat, formatModifier, tileGridPosition, metaX, metaY,
                (layer == null ? null : layer.gutter));
        this.wmsLayer = layer;
        this.fullParameters = fullParameters;

        // ImageUtilities.allowNativeCodec("png", ImageReaderSpi.class, false);
    }

    protected String getWMSParams() throws GeoWebCacheException {
        String baseParameters = wmsLayer.getWMSRequestTemplate(this.getResponseFormat(),
                WMSLayer.RequestType.MAP);

        // Fill in the blanks
        StringBuilder strBuilder = new StringBuilder(baseParameters);
        if (formatModifier == null) {
            strBuilder.append("&FORMAT=").append(responseFormat.getFormat());
        } else {
            strBuilder.append("&FORMAT=").append(formatModifier.getRequestFormat().getFormat());
        }

        strBuilder.append("&SRS=").append(wmsLayer.backendSRSOverride(gridSubset.getSRS()));
        strBuilder.append("&WIDTH=").append(getMetaTileWidth());
        strBuilder.append("&HEIGHT=").append(getMetaTileHeight());
        strBuilder.append("&BBOX=").append(getMetaTileBounds());

        strBuilder.append(fullParameters);

        return strBuilder.toString();
    }

    public int[] getGutter() {
        return gutter.clone();
    }

    protected WMSLayer getLayer() {
        return wmsLayer;
    }

}
