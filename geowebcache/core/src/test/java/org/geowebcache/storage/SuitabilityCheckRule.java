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
 * @author Kevin Smith, Boundless, 2018
 */

package org.geowebcache.storage;

import java.util.Properties;

import org.geowebcache.util.PropertyRule;

/**
 * Sets GEOWEBCACHE_BLOBSTORE_SUITABILITY_CHECK property for a unit test then reverts it.
 * @author Kevin Smith, Boundless, 2018
 *
 */
public class SuitabilityCheckRule extends PropertyRule {
    
    private SuitabilityCheckRule(Properties props) {
        super(props, CompositeBlobStore.GEOWEBCACHE_BLOBSTORE_SUITABILITY_CHECK);
    }
    
    /**
     * Create a rule to override the system property
     * @param name
     * @return
     */
    public static SuitabilityCheckRule system() {
        return new SuitabilityCheckRule(System.getProperties());
    }

    /**
     * Set the StoreSuitabilityCheck property
     * @param value
     */
    public void setValue(CompositeBlobStore.StoreSuitabilityCheck value) {
        setValue(value.name());
    }
    
    @Override
    protected void before() throws Throwable {
        super.before();
        cleanUp();
    }

    @Override
    protected void after() {
        super.after();
        cleanUp();
    }
    
    protected void cleanUp() {
        CompositeBlobStore.storeSuitability.remove();
    }

}
