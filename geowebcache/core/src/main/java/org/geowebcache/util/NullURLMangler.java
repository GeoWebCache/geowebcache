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
 * <p>Copyright 2019
 */
package org.geowebcache.util;

import org.apache.commons.lang3.StringUtils;

public class NullURLMangler implements URLMangler {

    @Override
    public String buildURL(String baseURL, String contextPath, String path) {
        final String context = StringUtils.strip(contextPath, "/");

        // if context is root ("/") then don't append it to prevent double slashes ("//") in return
        // URLs
        if (context == null || context.isEmpty()) {
            return StringUtils.strip(baseURL, "/") + "/" + StringUtils.strip(path, "/");
        } else {
            return StringUtils.strip(baseURL, "/")
                    + "/"
                    + context
                    + "/"
                    + StringUtils.strip(path, "/");
        }
    }
}
