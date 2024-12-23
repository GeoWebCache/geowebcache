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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import com.thoughtworks.xstream.XStream;

/**
 * Extension point for {@link XMLConfiguration} to allow decoupled modules to contribute to the configuration set up in
 * order to extend the {@code geowebcache.xml} contents with new constructs.
 *
 * @author Gabriel Roldan
 */
public interface XMLConfigurationProvider {

    /**
     * Allows an extension to enhance the {@link XMLConfiguration} XStream persister to handle new contructs.
     *
     * @param xs the XStream persister configured with the default elements from {@link XMLConfiguration}
     * @return the modified (possibly the same) XStream persister with the extension point's added xml mappings
     */
    XStream getConfiguredXStream(XStream xs);

    /**
     * Returns true if XStream has been configured to persist the given Info object.
     *
     * @param i The info object
     */
    boolean canSave(Info i);
}
