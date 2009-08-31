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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.storage.StorageBroker;

public class TMSConverter extends Service {

    public static final String SERVICE_TMS = "tms";

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    public TMSConverter(StorageBroker sb, TileLayerDispatcher tld) {
        super(SERVICE_TMS);
        this.sb = sb;
        this.tld = tld;
    }

    public ConveyorTile getConveyor(HttpServletRequest request,
            HttpServletResponse response) throws ServiceException {

        // get all elements of the pathInfo after the leading "/tms/1.0.0/" part.
        String[] params = request.getPathInfo().split("/");
        
        int paramsLength = params.length;
        
        if(params.length < 5) {
            throw new ServiceException("Expected at least 5 parameters, found " + params.length);
        }
        
        long[] gridLoc = new long[3];
        
        String[] yExt = params[paramsLength - 1].split("\\.");
        
        try {
            gridLoc[0] = Integer.parseInt(params[paramsLength - 2]);
            gridLoc[1] = Integer.parseInt(yExt[0]);
            gridLoc[2] = Integer.parseInt(params[paramsLength - 3]);
        } catch (NumberFormatException nfe) {
            throw new ServiceException("Unable to parse number " + nfe.getMessage() + " from " + request.getPathInfo());
        }

        String layerId = params[paramsLength - 4];
        TileLayer tileLayer;
        try {
            tileLayer = tld.getTileLayer(layerId);
        } catch (Exception ex) {
            throw new ServiceException(ex);
        }
        // TMS does not specify projection, simply choose the first available
        // SRS for the layer.
        String gridSubsetId = tileLayer.getGridSubsets().keySet().iterator().next();

        // TMS specifies only the extension of the format, assume that the mime
        // type is image/something
        MimeType mimeType = null;
        try {
            mimeType = MimeType.createFromExtension(yExt[1]);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format based on extension " + yExt[1]);
        }

        ConveyorTile ret = new ConveyorTile(sb, layerId, gridSubsetId, gridLoc, mimeType, null, null, request, response);
        return ret;
    }

}