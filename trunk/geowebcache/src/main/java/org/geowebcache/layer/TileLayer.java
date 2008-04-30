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
package org.geowebcache.layer;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.wms.BBOX;

public interface TileLayer {

    public String supportsProjection(SRS srs);
    public String supportsFormat(String formatStr);
    public String supportsBbox(SRS srs, BBOX bounds);
    
    public TileResponse getResponse(TileRequest tileRequest, 
           ServiceRequest servReq, HttpServletResponse response) 
    throws GeoWebCacheException, IOException;
    
    public SRS[] getProjections();
    public int getSRSIndex(SRS reqSRS);
    public BBOX getBounds(int srsIdx);
    public int[] getMetaTilingFactors();
    public int[][] getCoveredGridLevels(int srsIdx, BBOX bounds);
    public MimeType getDefaultMimeType();
    public String getName();
    public void destroy();
    public int[] getGridLocForBounds(int srsIdx, BBOX bounds);
    public BBOX getBboxForGridLoc(int srsIdx, int[] gridLoc);
    public int[][] getZoomInGridLoc(int srsIdx, int[] gridLoc);
    public int[] getZoomedOutGridLoc(int srsIdx);
}
