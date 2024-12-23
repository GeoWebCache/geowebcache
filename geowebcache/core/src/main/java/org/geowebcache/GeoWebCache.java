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

/** GeoWebCache version information */
public class GeoWebCache {

    /** @return current the GWC version */
    @SuppressWarnings("deprecation") // Package.getPackage is deprecated in java 11 but replacement is not
    // available in Java 8
    public static String getVersion() {
        Package versionInfo = Package.getPackage("org.geowebcache");
        String version = versionInfo.getSpecificationVersion();
        return version;
    }

    /** @return the current build revision number/hash */
    @SuppressWarnings("deprecation") // Package.getPackage is deprecated in java 11 but replacement is not
    // available in Java 8
    public static String getBuildRevision() {
        Package versionInfo = Package.getPackage("org.geowebcache");
        String build = versionInfo.getImplementationVersion();
        return build;
    }
}
