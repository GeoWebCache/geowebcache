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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.grid.GridSet;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class XMLConfigurationGridsetConformanceTest extends GridSetConfigurationTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    protected File configDir;
    protected File configFile;

    public @Rule MockWepAppContextRule extensions = new MockWepAppContextRule();
    public @Rule MockWepAppContextRule extensions2 = new MockWepAppContextRule(false);

    @Override
    protected GridSetConfiguration getConfig() throws Exception {
        makeConfigFile();
        return getConfig(extensions);
    }

    @Override
    protected GridSetConfiguration getSecondConfig() throws Exception {
        return getConfig(extensions2);
    }

    TestXMLConfigurationSource configSource = new TestXMLConfigurationSource();

    protected GridSetConfiguration getConfig(MockWepAppContextRule extensions) throws Exception {
        return configSource.create(extensions, configDir);
    }

    protected void makeConfigFile() throws IOException {
        if (configFile == null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");

            URL source = XMLConfiguration.class.getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
            FileUtils.copyURLToFile(source, configFile);
        }
    }

    @Override
    protected Matcher<GridSet> infoEquals(GridSet expected) {
        return new CustomMatcher<>("GridSet matching " + expected.getName() + " with " + expected.getDescription()) {

            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet gs
                        && gs.getName().equals(expected.getName())
                        && gs.getDescription().equals(expected.getDescription());
            }
        };
    }

    @Override
    protected Matcher<GridSet> infoEquals(int expected) {
        return new CustomMatcher<>("GridSet with value " + expected) {

            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet gs && (Objects.equals(gs.getDescription(), Integer.toString(expected)));
            }
        };
    }

    @Override
    protected String getExistingInfo() {
        return "EPSG:2163";
    }

    @Override
    public void failNextRead() {
        configSource.setFailNextRead(true);
    }

    @Override
    public void failNextWrite() {
        configSource.setFailNextWrite(true);
    }

    @Override
    protected void renameInfo(GridSetConfiguration config, String name1, String name2) throws Exception {
        Assume.assumeFalse(true);
    }
}
