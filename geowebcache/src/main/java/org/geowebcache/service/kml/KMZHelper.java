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
package org.geowebcache.service.kml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;

import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceException;
import org.geowebcache.tile.Tile;

/**
 * Just a helper class for KMZ experimentation stuff
 * 
 * 
 * @author ak
 */
public class KMZHelper {
    
    private static Log log = 
        LogFactory.getLog(org.geowebcache.service.kml.KMZHelper.class);
    
    /**
     * Filters the given gridlocation 
     * 
     *  Note that this does an actual reques to the WMS backend and then
     *  throws the result way. Some may consider this a bit wasteful ;)
     *
     * @param tileLayer
     * @param srs
     * @param formatStr
     * @param linkGridLocs
     * @return
     */
    public static int[][] filterGridLocs(TileLayer tileLayer,
            MimeType mime, int[][] linkGridLocs) 
    throws GeoWebCacheException {
        
        for(int i=0;i<linkGridLocs.length; i++) {
            if(linkGridLocs[i][2] > 0) {
                
                Tile tile = new Tile(
                        tileLayer.getName(), SRS.getEPSG4326(), 
                        linkGridLocs[i], mime, null, null);
                tile.setTileLayer(tileLayer);
                
                try {                                        
                    tileLayer.getTile(tile);
                    
                } catch (IOException ioe) {
                    log.error(ioe.getMessage());
                    linkGridLocs[i][2] = -1;
                } catch (GeoWebCacheException gwce) {
                    log.error(gwce.getMessage());
                    gwce.printStackTrace();
                    linkGridLocs[i][2] = -1;
                }
                             
                // If it's a 204 it means no content -> don't link to it
                if(tile.getStatus() == 204) {
                    linkGridLocs[i][2] = -1;
                } else if(tile.getStatus() != 200) {
                    throw new GeoWebCacheException(
                            "Unexpected response code from server " + tile.getStatus());
                }
            }
        }
        
        return linkGridLocs;
    }
    
    /**
     * 
     * 
     * @param namePfx
     * @param overlayXml
     * @param dataXml
     * @param response
     * @return
     * @throws ServiceException
     */
    protected static byte[] createZippedKML(
            String namePfx , String formatExtension,
            byte[] overlayXml, byte[] dataXml) 
    throws ServiceException {
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        try {
            writeZippedKML(namePfx, formatExtension, overlayXml, dataXml, out);
        } catch (IOException ioe) {
            throw new ServiceException(
                    "Encountered problem writing zip: " + ioe.getMessage());
        }
        
        return out.toByteArray();
    }

    /**
     * Writes two byte[] into a zip
     * -> like a zipfile with two files
     * 
     * @param namePfx prefix for files inside file
     * @param overlay
     * @param data
     * @param out
     * @throws IOException
     */
    private static void writeZippedKML(
            String namePfx, String formatExtension,
            byte[] overlay, byte[] data, OutputStream out) 
    throws IOException {

        ZipOutputStream zipos = new ZipOutputStream(out);
        // High compression
        //zipos.setLevel(9);

        // Add the overlay, links to the next content
        ZipEntry zeOl = new ZipEntry("netlinks_"+namePfx + ".kml");
        zipos.putNextEntry(zeOl);
        zipos.write(overlay);
        //zipos.closeEntry();

        // Add the actual data, if applicable
        if(data != null) {
            ZipEntry zeData = new ZipEntry("data_" + namePfx+"."+formatExtension);
            zipos.putNextEntry(zeData);
            zipos.write(data);
            //zipos.closeEntry();
        }
        zipos.finish();
    }
}
