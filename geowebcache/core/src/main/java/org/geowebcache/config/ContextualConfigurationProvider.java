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

/**
 * An XMLConfigurationProvider that can restrict itself to certain contexts
 *
 * @author Kevin Smith, OpenGeo
 */
public interface ContextualConfigurationProvider extends XMLConfigurationProvider {

    /** The contexts a provider can apply to */
    public static enum Context {
        /** Persistence to storage */
        PERSIST,

        /** Over the REST API */
        REST
    }

    /**
     * Does the provider apply to the given context
     *
     * @param ctxt The context
     * @return true of applicable, false otherwise
     */
    public boolean appliesTo(Context ctxt);
}
