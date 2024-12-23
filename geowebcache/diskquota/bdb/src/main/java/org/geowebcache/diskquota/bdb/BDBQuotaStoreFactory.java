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
 * @author Andrea Aime Copyright 2012
 */
package org.geowebcache.diskquota.bdb;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.QuotaStoreFactory;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.context.ApplicationContext;

public class BDBQuotaStoreFactory implements QuotaStoreFactory {

    public static final String STORE_NAME = "BDB";

    @Override
    public List<String> getSupportedStoreNames() {
        return Arrays.asList(STORE_NAME);
    }

    @Override
    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName)
            throws IOException, ConfigurationException {
        if (!STORE_NAME.equals(quotaStoreName)) {
            return null;
        }

        DefaultStorageFinder cacheDirFinder = (DefaultStorageFinder) ctx.getBean("gwcDefaultStorageFinder");
        TilePageCalculator tilePageCalculator = (TilePageCalculator) ctx.getBean("gwcTilePageCalculator");
        try {
            BDBQuotaStore bdbQuotaStore = new BDBQuotaStore(cacheDirFinder, tilePageCalculator);
            bdbQuotaStore.startUp();

            return bdbQuotaStore;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Failed to startup the BDB store", e);
        }
    }
}
