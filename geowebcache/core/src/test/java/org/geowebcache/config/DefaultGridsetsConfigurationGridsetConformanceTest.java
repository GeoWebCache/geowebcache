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
 * Copyright 2018
 *
 */
package org.geowebcache.config;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Assume;

public class DefaultGridsetsConfigurationGridsetConformanceTest extends GridSetConfigurationTest {
    
    @Override
    protected GridSetConfiguration getConfig() throws Exception {
        return new DefaultGridsets(true, true);
    }
    @Override
    protected GridSetConfiguration getSecondConfig() throws Exception {
        Assume.assumeFalse("This config does not have persistence.", true);
        return null;
    }

    @Override
    protected Matcher<GridSet> infoEquals(GridSet expected) {
        return new CustomMatcher<GridSet>("GridSet matching "+expected.getName()+" with " + expected.getDescription()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && ((GridSet)item).getName().equals(((GridSet)expected).getName()) &&
                    ((GridSet)item).getDescription().equals(((GridSet)expected).getDescription());
            }
            
        };
    }
    
    @Override
    protected Matcher<GridSet> infoEquals(int expected) {
        return new CustomMatcher<GridSet>("GridSet with value " + expected){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && ((GridSet)item).getDescription().equals(Integer.toString(expected));
            }
            
        };
    }

    @Override
    protected String getExistingInfo() {
        return "EPSG:4326";
    }

    @Override
    public void failNextRead() {
        Assume.assumeFalse(true);
    }

    @Override
    public void failNextWrite() {
        Assume.assumeFalse(true);
    }

    @Override
    protected void renameInfo(GridSetConfiguration config, String name1, String name2)
            throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void addInfo(GridSetConfiguration config, GridSet info) throws Exception {
        // TODO Auto-generated method stub
        Assume.assumeFalse(true);
    }

    @Override
    protected void removeInfo(GridSetConfiguration config, String name) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void modifyInfo(GridSetConfiguration config, GridSet info) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    public void testCanSaveGoodInfo() throws Exception {
        // Should not be able to save anything as it is read only
        assertThat(config.canSave(getGoodInfo("test", 1)), equalTo(false));
    }
    
    
}
