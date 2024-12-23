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
package org.geowebcache.diskquota;

import java.io.IOException;
import java.util.List;
import org.geowebcache.config.ConfigurationException;
import org.springframework.context.ApplicationContext;

/**
 * Builds and returns a quota store factory.
 *
 * @author Andrea Aime - GeoSolutions
 */
public interface QuotaStoreFactory {

    /**
     * Builds a quota store based on a provided name, or returns null if the specified quota store cannot be handled
     *
     * @param ctx the application context, should the store depend on other beans
     */
    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName)
            throws IOException, ConfigurationException;

    /** Lists the quota store names supported by this factory */
    List<String> getSupportedStoreNames();
}
