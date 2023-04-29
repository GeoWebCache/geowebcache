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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.util.ServletUtils;

/** One of the services exposed by GeoWebCache, for example TMS, WMTS, KML, ... */
public abstract class Service {

    private String pathName = null;

    public Service(String pathName) {
        this.pathName = pathName;
    }

    /** Whether this service can handle the given request */
    public boolean handlesRequest(HttpServletRequest request) {
        return request.getPathInfo().equalsIgnoreCase(pathName);
    }

    public String getPathName() {
        return pathName;
    }

    // TODO these should be renamed / removed
    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException, OWSException {
        throw new ServiceException(
                "Service for "
                        + pathName
                        + " needs to override "
                        + "getConveyor(HttpSerlvetRequest,HttpServletResponse)");
    }

    public void handleRequest(Conveyor conv) throws GeoWebCacheException, OWSException {
        throw new RuntimeException(
                "Service for "
                        + pathName
                        + " needs to override "
                        + "handleRequest(TileLayerDispatcher, Tile)");
    }

    protected String getLayersParameter(HttpServletRequest request) throws ServiceException {
        String layers =
                ServletUtils.stringFromMap(
                        request.getParameterMap(), request.getCharacterEncoding(), "layers");
        if (layers == null) {
            throw new ServiceException("Unable to parse layers parameter from request.");
        }
        return layers;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Service) {
            Service other = (Service) obj;
            if (other.pathName != null && other.pathName.equalsIgnoreCase(pathName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return pathName.hashCode();
    }

    protected static void writeTileResponse(ConveyorTile conv, boolean writeExpiration) {
        writeTileResponse(conv, writeExpiration, null, null);
    }

    protected static void writeTileResponse(
            ConveyorTile conv,
            boolean writeExpiration,
            RuntimeStats stats,
            String mimeTypeOverride) {
        HttpServletResponse response = conv.servletResp;
        Resource data = conv.getBlob();

        String mimeStr;
        if (mimeTypeOverride == null) {
            mimeStr = conv.getMimeType().getMimeType();
        } else {
            mimeStr = mimeTypeOverride;
        }

        response.setCharacterEncoding("utf-8");

        response.setStatus(conv.getStatus());

        TileLayer layer = conv.getLayer();
        if (layer != null) {
            layer.setExpirationHeader(conv.servletResp, (int) conv.getTileIndex()[2]);
        }

        if (writeExpiration) {
            conv.getLayer().setExpirationHeader(response, (int) conv.getTileIndex()[2]);
        }

        response.setContentType(mimeStr);

        int size = (int) data.getSize();
        response.setContentLength(size);

        try (OutputStream os = response.getOutputStream();
                WritableByteChannel channel = Channels.newChannel(os)) {
            data.transferTo(channel);

            if (stats != null) {
                stats.log(size, conv.getCacheResult());
            }
        } catch (IOException ioe) {
            // Do nothing...
        }
    }
}
