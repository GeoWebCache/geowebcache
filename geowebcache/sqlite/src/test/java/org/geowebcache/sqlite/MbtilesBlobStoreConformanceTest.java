/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Kevin Smith, Boundless, 2017
 */

package org.geowebcache.sqlite;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class MbtilesBlobStoreConformanceTest extends AbstractBlobStoreTest<MbtilesBlobStore> {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Override
    public void createTestUnit() throws Exception {
        this.store = new MbtilesBlobStore(getDefaultConfiguration());
    }
    
    protected MbtilesInfo getDefaultConfiguration() {
        MbtilesInfo configuration = new MbtilesInfo();
        configuration.setPoolSize(1000);
        configuration.setRootDirectory(temp.getRoot().getPath());
        configuration.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{params}", "{format}", "{z}", "tiles-{x}-{y}.sqlite"));
        configuration.setRowRangeCount(500);
        configuration.setColumnRangeCount(500);
        return configuration;
    }
}
