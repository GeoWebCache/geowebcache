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
 * @author Niels Charlier Copyright 2015
 */
package org.geowebcache.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstraction of a resource such as a file to write and read the configuration from.
 *
 * @author Niels Charlier
 */
public interface ConfigurationResourceProvider {

    /** Create inputstream */
    public InputStream in() throws IOException;

    /** Create outputstream */
    public OutputStream out() throws IOException;

    /** Make a backup */
    public void backup() throws IOException;

    /** @return identifier for this resource */
    public String getId();

    /** @return location of this resource */
    public String getLocation() throws IOException;

    /**
     * Allows to set the location of the template file to create geowebcache.xml from when it's not found in the cache
     * directory.
     *
     * @param templateLocation location of the template geowebcache.xml file, must be a classpath location. If not set
     *     defaults to /geowebcache.xml
     */
    public void setTemplate(String templateLocation);

    /** @return true if input is supported */
    public boolean hasInput();

    /** @return true if output is supported */
    public boolean hasOutput();
}
