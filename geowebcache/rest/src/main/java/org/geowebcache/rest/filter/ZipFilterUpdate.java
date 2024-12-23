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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 * @author David Vick, Boundless, 2017
 */
package org.geowebcache.rest.filter;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.util.ServletUtils;
import org.springframework.http.HttpStatus;

public class ZipFilterUpdate {
    private static Logger log = Logging.getLogger(ZipFilterUpdate.class.getName());

    InputStream is;

    public ZipFilterUpdate(InputStream is) {
        this.is = is;
    }

    @SuppressWarnings("PMD.UseTryWithResources") // is is a field, cannot be handled using ITWR
    public void runUpdate(RequestFilter filter, TileLayer tl) throws RestException {
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                log.info("Reading " + ze.getName() + " (" + ze.getSize() + " bytes ) for " + filter.getName());

                if (ze.isDirectory()) {
                    throw new RestException("Zip file cannot contain directories.", HttpStatus.BAD_REQUEST);
                }

                String[] parsedName = parseName(ze.getName());

                byte[] data = ServletUtils.readStream(zis, 16 * 1024, 1500, false);

                try {
                    filter.update(data, tl, parsedName[0], Integer.parseInt(parsedName[1]));

                } catch (GeoWebCacheException e) {
                    throw new RestException(
                            "Error updating " + filter.getName() + ": " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }

                ze = zis.getNextEntry();
            }

        } catch (IOException ioe) {
            throw new RestException("IOException while reading zip, " + ioe.getMessage(), HttpStatus.BAD_REQUEST);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // Ok at this point
            }
        }
    }

    String[] parseName(String fileName) throws RestException {
        String[] strs = fileName.split("_");

        // Slice away the extension, we dont have the data to test it
        String[] zExt = strs[2].split("\\.");
        strs[2] = zExt[0];

        String[] gridSetIdZ = {strs[1], strs[2]};

        return gridSetIdZ;
    }
}
