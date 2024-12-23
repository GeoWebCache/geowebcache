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
 * @author Andrea Aime - GeoSolutions 2019
 */
package org.geowebcache.util;

import java.io.Closeable;
import java.io.IOException;

/** IO related utility methods that common libraries won't provide */
public class IOUtils {

    /**
     * A replacement for commons-io closeQuietly, for those rare cases in which the quiet closing behavior is actually
     * needed and try-with-resources won't do the expected job
     */
    public static void closeQuietly(Closeable clo) {
        if (clo != null) {
            try {
                clo.close();
            } catch (IOException ignore) {
            }
        }
    }
}
