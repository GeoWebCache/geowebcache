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
package org.geowebcache.filter.request;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;

/**
 * Hardcoded to the built-in blank tile for now
 *
 * @author ak
 */
public class BlankTileException extends RequestFilterException {
    /** */
    @Serial
    private static final long serialVersionUID = 6910805463474341350L;

    private static Logger log = Logging.getLogger(BlankTileException.class.getName());

    private static volatile Resource blankTile;

    public BlankTileException(RequestFilter reqFilter) {
        super(reqFilter, 200, "image/png");
    }

    private Resource getBlankTile() {
        // Use the built-in one:
        try (InputStream is = GeoWebCacheDispatcher.class.getResourceAsStream("blank.png")) {
            byte[] blankTile = new byte[425];
            int ret = is.read(blankTile);
            log.info("Read " + ret + " from blank PNG file (expected 425).");

            return new ByteArrayResource(blankTile);
        } catch (IOException ioe) {
            log.log(Level.SEVERE, ioe.getMessage(), ioe);
        }

        return null;
    }

    @Override
    public Resource getResponse() {
        Resource ret = BlankTileException.blankTile;
        if (ret == null) {
            synchronized (BlankTileException.class) {
                ret = BlankTileException.blankTile;
                if (ret == null) {
                    BlankTileException.blankTile = ret = getBlankTile();
                }
            }
        }
        return ret;
    }
}
