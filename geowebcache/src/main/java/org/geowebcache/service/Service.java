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
 */
package org.geowebcache.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorKMLTile;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.conveyor.ConveyorWFS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.util.ServletUtils;

public abstract class Service {
    
    private String pathName = null;
    
    public Service(String pathName){
        this.pathName = pathName;
    }
    
    /**
     * Whether this service can handle the given request
     * 
     * @return
     */
    public boolean handlesRequest(HttpServletRequest request) {
        return request.getPathInfo().equalsIgnoreCase(pathName);
    }
    
    public String getPathName(){
        return pathName;
    }
    
    // TODO these should be renamed / removed
    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response) 
    throws GeoWebCacheException, OWSException {
        throw new ServiceException (
                "Service for " + pathName  + " needs to override "
                +"getConveyor(HttpSerlvetRequest,HttpServletResponse)" );
    }
    
    public void handleRequest(Conveyor conv) 
    throws GeoWebCacheException, OWSException {
        throw new RuntimeException(
                "Service for " + pathName  + " needs to override "
                +"handleRequest(TileLayerDispatcher, Tile)" );
    }
    
    protected String getLayersParameter(HttpServletRequest request) throws ServiceException {
    	String layers = ServletUtils.stringFromMap(request.getParameterMap(), request.getCharacterEncoding(), "layers");
    	if(layers == null) {
    		throw new ServiceException("Unable to parse layers parameter from request.");
    	} 
    	return layers;
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof Service) {
            Service other = (Service) obj;
            if(other.pathName != null 
                    && other.pathName.equalsIgnoreCase(pathName)) {
                return true;
            }
        }
        
        return false;
    }
    
    public int hashCode() {
        return pathName.hashCode();
    }
    
    protected static void writeTileResponse(ConveyorTile conv, boolean writeExpiration) {
        writeTileResponse(conv, writeExpiration, null);
    }
    
    protected static void writeTileResponse(ConveyorTile conv, boolean writeExpiration, RuntimeStats stats) {
        HttpServletResponse response = conv.servletResp;
        byte[] data = conv.getContent();

        String mimeStr = conv.getMimeType().getMimeType();

        response.setCharacterEncoding("utf-8");

        response.setStatus((int) conv.getStatus());

        TileLayer layer = conv.getLayer();
        if (layer != null) {
            layer.setExpirationHeader(conv.servletResp);
        }

        if (writeExpiration) {
            conv.getLayer().setExpirationHeader(response);
        }

        if (conv instanceof ConveyorKMLTile) {
            ConveyorKMLTile kmlTile = (ConveyorKMLTile) conv;
            if (kmlTile.getWrapperMimeType() != null) {
                mimeStr = kmlTile.getWrapperMimeType().getMimeType();
            }
        }

        response.setContentType(mimeStr);

        response.setContentLength(data.length);

        try {
            OutputStream os = response.getOutputStream();
            os.write(data);
            
            if(stats != null) {
                stats.log(data.length);
            }
        } catch (IOException ioe) {
            // Do nothing...
        }
    }
    
    protected static void writeWFSResponse(ConveyorWFS conv,
            boolean writeExpiration) {
        HttpServletResponse response = conv.servletResp;
        
        String mimeStr = ((ConveyorWFS) conv).getMimeTypeString();

        response.setCharacterEncoding("utf-8");

        response.setContentType(mimeStr);

        InputStream is = null;
        OutputStream os = null;
        try {
            is = conv.getInputStream();
            os = response.getOutputStream();

            byte[] buffer = new byte[2048];
            int read = 0;

            while (read != -1) {
                read = is.read(buffer);
                if(read != -1) {
                    os.write(buffer, 0, read);
                }
            }
        } catch (IOException ioe) {
            // Do nothing...
        } finally {
            try{ if(is != null) is.close(); } catch (IOException ioe) {   }
            try{ if(os != null) os.close(); } catch (IOException ioe) {   }
        }
    }
    
}
