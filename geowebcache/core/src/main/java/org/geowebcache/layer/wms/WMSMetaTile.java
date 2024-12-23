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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer.wms;

import java.util.Map;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;

public class WMSMetaTile extends MetaTile {
    protected WMSLayer wmsLayer = null;

    protected Map<String, String> fullParameters;

    /** Used for requests by clients */
    protected WMSMetaTile(
            WMSLayer layer,
            GridSubset gridSubset,
            MimeType responseFormat,
            FormatModifier formatModifier,
            long[] tileGridPosition,
            int metaX,
            int metaY,
            Map<String, String> fullParameters) {
        super(
                gridSubset,
                responseFormat,
                formatModifier,
                tileGridPosition,
                metaX,
                metaY,
                (layer == null ? null : layer.gutter));
        this.wmsLayer = layer;
        this.fullParameters = fullParameters;

        // ImageUtilities.allowNativeCodec("png", ImageReaderSpi.class, false);
    }

    protected Map<String, String> getWMSParams() throws GeoWebCacheException {
        Map<String, String> params = wmsLayer.getWMSRequestTemplate(this.getResponseFormat(), WMSLayer.RequestType.MAP);

        // Fill in the blanks
        String format;
        if (formatModifier == null) {
            format = responseFormat.getFormat();
        } else {
            MimeType requestFormat = formatModifier.getRequestFormat();
            format = requestFormat.getFormat();
        }
        params.put("FORMAT", format);

        params.put("SRS", wmsLayer.backendSRSOverride(gridSubset.getSRS()));
        params.put("WIDTH", String.valueOf(getMetaTileWidth()));
        params.put("HEIGHT", String.valueOf(getMetaTileHeight()));
        params.put("BBOX", String.valueOf(getMetaTileBounds()));

        params.putAll(fullParameters);

        return params;
    }

    public int[] getGutter() {
        return gutter.clone();
    }

    protected WMSLayer getLayer() {
        return wmsLayer;
    }
}
