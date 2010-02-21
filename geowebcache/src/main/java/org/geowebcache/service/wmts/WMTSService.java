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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 * 
 */
package org.geowebcache.service.wmts;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.OWSException;
import org.geowebcache.service.Service;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ServletUtils;

public class WMTSService extends Service {
    public static final String SERVICE_WMTS = "wmts";

    enum RequestType {TILE, CAPABILITIES, FEATUREINFO};
    
    //private static Log log = LogFactory.getLog(org.geowebcache.service.wmts.WMTSService.class);
    
    private StorageBroker sb;
    
    private TileLayerDispatcher tld; 
    
    private GridSetBroker gsb;
    
    private RuntimeStats stats;
    
    public WMTSService(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb, RuntimeStats stats) {
        super(SERVICE_WMTS);
        
        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
        this.stats = stats;
    }

    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response) 
    throws OWSException {
        String encoding = request.getCharacterEncoding();
        String[] keys = { "layer", "request", "style", "format", "tilematrixset", "tilematrix", "tilerow", "tilecol" };
        String[] values = ServletUtils.selectedStringsFromMap(request.getParameterMap(), encoding, keys);
        
        String req = values[1];
        if(req == null) {
            //OWSException(httpCode, exceptionCode, locator, exceptionText);
            throw new OWSException(400, "MissingParameterValue", "request", "Missing Request parameter");
        } else {
            req = req.toLowerCase();
        }
        
        if(req.equals("gettile")) {
            ConveyorTile tile = getTile(values, request, response, RequestType.TILE);
            return tile;
        } else if(req.equals("getcapabilities")) {
            ConveyorTile tile = new ConveyorTile(sb, values[0], request, response);
            tile.setHint(req);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        } else if(req.equals("getfeatureinfo")) {
            ConveyorTile tile = getTile(values, request, response, RequestType.FEATUREINFO);
            tile.setHint(req);
            tile.setRequestHandler(Conveyor.RequestHandler.SERVICE);
            return tile;
        } else {
            throw new OWSException(501, "OperationNotSupported", "request", req + " is not implemented");
        }
    }
    
    private ConveyorTile getTile(String[] values, HttpServletRequest request, 
            HttpServletResponse response, RequestType reqType) throws OWSException {
        String encoding = request.getCharacterEncoding();
        
        String layer = values[0];
        if (layer == null) {
            throw new OWSException(400, "MissingParameterValue", "LAYER", "Missing LAYER parameter");
        }

        TileLayer tileLayer = null;
        
        try {
            tileLayer = tld.getTileLayer(layer);
        } catch (GeoWebCacheException e) {
            throw new OWSException(500, "NoApplicableCode", "LAYER", e.getMessage() +" while fetching LAYER " + layer);
        }
        if(tileLayer == null) {
            throw new OWSException(400, "InvalidParameterValue", "LAYER", "LAYER " + layer + " is not known.");
        }
        
        String[] modStrs = null;
        if(tileLayer instanceof WMSLayer) {
            try {
                modStrs = ((WMSLayer) tileLayer).getModifiableParameters(request.getParameterMap(), encoding);
            } catch (GeoWebCacheException e) {
                throw new OWSException(500, "NoApplicableCode", "",e.getMessage() + " while fetching modifiable parameters for LAYER " + layer);
            }
        }
         
        if(modStrs == null) {
            modStrs = new String[2];
            modStrs[0] = null;
            modStrs[1] = null;
        }
        
        MimeType mimeType = null;
        if(reqType == RequestType.TILE) {
            if(values[3] == null) {
                throw new OWSException(400, "MissingParameterValue", "FORMAT", "Unable to determine requested FORMAT, " + values[3]);
            }
            try {
                mimeType = MimeType.createFromFormat(values[3]);
            } catch (MimeException me) {
                throw new OWSException(400, "InvalidParameterValue", "FORMAT", "Unable to determine requested FORMAT, " + values[3]);
            } 
        } else {
            String infoFormat = ServletUtils.stringFromMap(
                    request.getParameterMap(), 
                    request.getCharacterEncoding(), 
                    "infoformat" );
            
            try {
                mimeType = MimeType.createFromFormat(infoFormat);
            } catch (MimeException me) {
                throw new OWSException(400, "MissingParameterValue", "INFOFORMAT", "Unable to determine requested INFOFORMAT, " + infoFormat);
            }
        }
        
        if (values[4] == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEMATRIXSET", "No TILEMATRIXSET specified");
        }
        
        GridSubset gridSubset = tileLayer.getGridSubset(values[4]);
        if (gridSubset == null) {
            throw new OWSException(400, "InvalidParameterValue", "TILEMATRIXSET", 
                    "Unable to match requested TILEMATRIXSET " + values[4] + " to those supported by layer");
        }
        
        if(values[5] == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEMATRIX", "No TILEMATRIX specified");
        }
        long z = gridSubset.getGridIndex(values[5]);
        
        if(z < 0) {
            throw new OWSException(400, "InvalidParameterValue", "TILEMATRIX", "Unknown TILEMATRIX " + values[5]);
        }
        
        // WMTS has 0 in the top left corner -> flip y value
        if(values[6] == null) {
            throw new OWSException(400, "MissingParameterValue", "TILEROW", "No TILEROW specified");
        }
        long[] gridExtent = gridSubset.getGridSetExtent((int) z);
        long y = gridExtent[1] - Long.parseLong(values[6]) - 1;

        if(values[7] == null) {
            throw new OWSException(400, "MissingParameterValue", "TILECOLUMN", "No TILECOLUMN specified");
        }
        long x = Long.parseLong(values[7]);
        
        long[] gridCov = gridSubset.getCoverage((int) z);
        
        if(x < gridCov[0] || x > gridCov[2]) {
            throw new OWSException(400, "TileOutOfRange", "TILECOLUMN", 
                    "Column " + x + " is out of range, min: " + gridCov[0] + " max:" + gridCov[2]);
        }
        
        if(y < gridCov[1] || y > gridCov[3]) {
            long minRow = gridExtent[1] - gridCov[3] - 1;
            long maxRow = gridExtent[1] - gridCov[1] - 1;
            
            throw new OWSException(400, "TileOutOfRange", "TILECOLUMN", 
                    "Row " + values[6] + " is out of range, min: " + minRow + " max:" + maxRow);
        }
        
        long[] tileIndex = {x,y,z};
        
        
        
        try {
            gridSubset.checkCoverage(tileIndex);
        } catch (OutsideCoverageException e) {
            
            
           
        }

        ConveyorTile convTile = new ConveyorTile(
                sb, layer, gridSubset.getName(), tileIndex, mimeType, 
                modStrs[0], modStrs[1], request, response);

        convTile.setTileLayer(tileLayer);
        
        return convTile;
    }

    public void handleRequest(Conveyor conv)
            throws OWSException {

        ConveyorTile tile = (ConveyorTile) conv;
        
        if (tile.getHint() != null) {
            if(tile.getHint().equals("getcapabilities")) {
                WMTSGetCapabilities wmsGC = new WMTSGetCapabilities(tld, gsb, tile.servletReq);      
                wmsGC.writeResponse(tile.servletResp, stats);
                
            } else if(tile.getHint().equals("getfeatureinfo")) {
                ConveyorTile convTile = (ConveyorTile) conv;
                WMTSGetFeatureInfo wmsGFI = new WMTSGetFeatureInfo(convTile);      
                wmsGFI.writeResponse(stats);
            }
        }
    }
    
    public static String decodeDimensionValue(String value) {
        if(value != null && value.startsWith("_")) {
            if(value.equals("_null")) {
                return null;
            } else if( value.equals("_empty")) {
                return "";
            } else {
                return value;
            }
        } else {
            return value;
        }
    }
    
    public static String encodeDimensionValue(String value) {
        if(value == null) {
            return "_null";
        } else if(value.length() == 0) {
            return "_empty";
        } else {
            return value;
        }
    }
}
