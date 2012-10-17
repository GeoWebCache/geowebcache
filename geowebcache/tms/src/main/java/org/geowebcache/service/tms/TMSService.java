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
 * @author Mikael Nyberg, Copyright 2009
 */
package org.geowebcache.service.tms;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class TMSService extends Service {

    public static final String SERVICE_TMS = "tms";

    private StorageBroker sb;

    private TileLayerDispatcher tld;
    
    private GridSetBroker gsb;
    
    private RuntimeStats stats;

    private URLMangler urlMangler;

    /**
     * Protected no-argument constructor to allow run-time instrumentation
     */
    protected TMSService() {
        super(SERVICE_TMS);
    }

    public TMSService(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb,
            RuntimeStats stats, URLMangler urlMangler) {
        super(SERVICE_TMS);
        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
        this.stats = stats;
        this.urlMangler = urlMangler;
    }
    
    @Override
    public ConveyorTile getConveyor(HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException {

        // get all elements of the pathInfo after the leading "/tms/1.0.0/" part.
        String pathInfo = request.getPathInfo();
        pathInfo = pathInfo.substring(pathInfo.indexOf("tms/1.0.0"));
        String[] params = pathInfo.split("/");
        // {"tms", "1.0.0", "img states@EPSG:4326", ... } 
        
        int paramsLength = params.length;
        
        if(params.length < 4) {
            // Not a tile request, lets pass it back out
            ConveyorTile tile = new ConveyorTile(sb, null, request, response);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }
        
        long[] gridLoc = new long[3];
        
        String[] yExt = params[paramsLength - 1].split("\\.");
        
        try {
            gridLoc[0] = Integer.parseInt(params[paramsLength - 2]);
            gridLoc[1] = Integer.parseInt(yExt[0]);
            gridLoc[2] = Integer.parseInt(params[paramsLength - 3]);
        } catch (NumberFormatException nfe) {
            throw new ServiceException("Unable to parse number " + nfe.getMessage() + " from " + pathInfo);
        }

        String layerId;
        String gridSetId;
        
        // For backwards compatibility, we'll look for @s and use defaults if not found
        String layerNameAndSRS = params[2];
        String[] lsf = ServletUtils.URLDecode(layerNameAndSRS, request.getCharacterEncoding()).split("@");
        if(lsf.length < 3) {
            layerId = lsf[0];
            TileLayer layer = tld.getTileLayer(layerId);
            gridSetId = layer.getGridSubsets().iterator().next();
        } else {
           layerId = lsf[0];
           gridSetId = lsf[1];
           // We don't actually care about the format, we'll pick it from the extension
        }

        MimeType mimeType = null;
        try {
            mimeType = MimeType.createFromExtension(yExt[1]);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format based on extension " + yExt[1]);
        }

        ConveyorTile ret = new ConveyorTile(sb, layerId, gridSetId, gridLoc, mimeType, null, request, response);
        
        return ret;
    }
    
    public void handleRequest(Conveyor conv)
    throws GeoWebCacheException {
        // get all elements of the pathInfo after the leading "/tms/1.0.0/" part.
        String pathInfo = conv.servletReq.getPathInfo();
        pathInfo = pathInfo.substring(pathInfo.indexOf("tms/1.0.0"));
        String[] params = pathInfo.split("/");
        // {"tms", "1.0.0", "img states@EPSG:4326" } 
        
        int paramsLength = params.length;
        
        String reqUrl = conv.servletReq.getRequestURL().toString();
        String servletBase = ServletUtils.getServletBaseURL(conv.servletReq);
        int prefixIdx = servletBase.length();
        int suffixIdx = reqUrl.indexOf("/service/tms/1.0.0");
        String context = reqUrl.substring(prefixIdx, suffixIdx);
        
        TMSDocumentFactory tdf = new TMSDocumentFactory(tld, gsb, servletBase, context, urlMangler);
        
        String ret = null;
        
        if(paramsLength < 2) {
            throw new GeoWebCacheException("Path is too short to be a valid TMS path");
        } else if(paramsLength == 2) {
            String version = params[1];
            if(! version.equals("1.0.0")) {
                throw new GeoWebCacheException("Unknown version " + version + ", only 1.0.0 is supported.");
            } else {
                ret = tdf.getTileMapServiceDoc();
            }
        } else {
            String layerNameAndSRS = params[2];
            String layerAtSRS = ServletUtils.URLDecode(layerNameAndSRS, conv.servletReq.getCharacterEncoding());
            String[] layerSRSFormatExtension = layerAtSRS.split("@");
            
            TileLayer tl = tld.getTileLayer(layerSRSFormatExtension[0]);
            GridSubset gridSub = tl.getGridSubset(layerSRSFormatExtension[1]);
            MimeType mimeType =  MimeType.createFromExtension(layerSRSFormatExtension[2]);
            ret = tdf.getTileMapDoc(tl, gridSub, gsb, mimeType);
        }
        
        byte[] data = ret.getBytes();
        stats.log(data.length, CacheResult.OTHER);
        
        conv.servletResp.setStatus(200);
        conv.servletResp.setContentType("text/xml");
        try {
            conv.servletResp.getOutputStream().write(data);
        } catch (IOException e) {
            // TODO log error
        }
    }
    
}
