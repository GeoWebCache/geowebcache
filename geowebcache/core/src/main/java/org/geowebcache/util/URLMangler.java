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
 * @author Robert Marianski, OpenGeo, 20012
 */
package org.geowebcache.util;

/**
 * subset copied from org.geoserver.ows.URLMangler
 *
 * <p>This hook allows others to plug in custom url generation.
 */
public interface URLMangler {

    /**
     * Allows for a custom url generation strategy
     *
     * @param baseURL the base url - contains the url up to the domain and port
     * @param contextPath the servlet context path, like /geoserver/gwc
     * @param path the remaining path after the context path
     * @return the full generated url from the pieces
     */
    public String buildURL(String baseURL, String contextPath, String path);
}
