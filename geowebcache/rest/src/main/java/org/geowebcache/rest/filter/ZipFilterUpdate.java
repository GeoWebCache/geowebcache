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
 */
package org.geowebcache.rest.filter;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.RestletException;
import org.geowebcache.util.ServletUtils;
import org.restlet.data.Status;

public class ZipFilterUpdate {
    private static Log log = LogFactory.getLog(ZipFilterUpdate.class);

    InputStream is;

    public ZipFilterUpdate(InputStream is) {
        this.is = is;
    }

    public void runUpdate(RequestFilter filter, TileLayer tl)
    throws RestletException {
        
        try {
            ZipInputStream zis = new ZipInputStream(is);

            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                log.info("Reading " + ze.getName() + " (" + ze.getSize() + " bytes ) for " + filter.getName());
                
                if (ze.isDirectory()) {
                    throw new RestletException("Zip file cannot contain directories.",
                            Status.CLIENT_ERROR_BAD_REQUEST);
                }
                
                String[] parsedName = parseName(ze.getName());
                
                byte[] data = ServletUtils.readStream(zis, 16*1024, 1500, false);
                
                try {
                    filter.update(
                            data,
                            tl, 
                            parsedName[0], 
                            Integer.parseInt(parsedName[1]));
                    
                } catch (GeoWebCacheException e) {
                    throw new RestletException("Error updating " + filter.getName()
                            + ": " + e.getMessage(), Status.SERVER_ERROR_INTERNAL);
                }
                
                ze = zis.getNextEntry();
            }
            
            
        } catch (IOException ioe) {
            throw new RestletException("IOException while reading zip, " + ioe.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // Ok at this point
            }

        }
    }
    
    String[] parseName(String fileName) throws RestletException {
        String[] strs = fileName.split("_");

        // Slice away the extension, we dont have the data to test it
        String[] zExt = strs[2].split("\\.");
        strs[2] = zExt[0];

        String[] gridSetIdZ = { strs[1], strs[2] };
        
        return gridSetIdZ;
    }
}
