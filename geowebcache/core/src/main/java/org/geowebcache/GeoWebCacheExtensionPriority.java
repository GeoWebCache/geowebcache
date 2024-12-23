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
 * <p>Copyright 2019
 */
package org.geowebcache;

/**
 * Interface implemented by extensions which require control over the order in which they are processed.
 *
 * <p>This the same interface of GeoServer extension priority.
 */
public interface GeoWebCacheExtensionPriority {

    /** The numeric value for highest priority. */
    int HIGHEST = 0;

    /** THe numeric value for lowest priority. */
    int LOWEST = 100;

    /**
     * Returns the priority of the extension.
     *
     * <p>This value is an integer between 0 and 100. Lesser values mean higher priority.
     */
    int getPriority();
}
