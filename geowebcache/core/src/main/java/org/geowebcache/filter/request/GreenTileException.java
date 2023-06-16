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
package org.geowebcache.filter.request;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;

/**
 * Hardcoded to the built-in blank tile for now
 *
 * @author ak
 */
public class GreenTileException extends RequestFilterException {
    /** */
    private static final long serialVersionUID = -3369293469656922254L;

    private static Logger log = Logging.getLogger(GreenTileException.class.getName());

    private static volatile Resource greenTile;

    public GreenTileException(RequestFilter reqFilter) {
        super(reqFilter, 200, "image/png");
    }

    private Resource getGreenTile() {
        byte[] green = new byte[659];

        try (InputStream is = GreenTileException.class.getResourceAsStream("green.png"); ) {
            int ret = is.read(green);
            log.info("Read " + ret + " from gree PNG file (expected 659).");
        } catch (IOException ioe) {
            log.log(Level.SEVERE, ioe.getMessage());
        }

        return new ByteArrayResource(green);
    }

    @Override
    public Resource getResponse() {
        Resource ret = greenTile;
        if (ret == null) {
            synchronized (GreenTileException.class) {
                ret = greenTile;
                if (greenTile == null) {
                    greenTile = ret = getGreenTile();
                }
            }
        }
        return ret;
    }
}
