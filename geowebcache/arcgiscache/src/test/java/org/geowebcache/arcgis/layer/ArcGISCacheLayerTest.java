/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2022
 */
package org.geowebcache.arcgis.layer;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.grid.GridSetBroker;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.ApplicationContext;

public class ArcGISCacheLayerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testArcGISCacheLayerInitialization() throws Exception {

        // The ArcGISCacheLayer GridSetBroker is instantiated with two configurations:
        // org.geowebcache.config.XMLConfiguration & org.geowebcache.config.DefaultGridsets
        // PLEASE DO NOT update the ArcGISCacheLayer GridSetBroker mocking below without first
        // confirming any new ArcGISCacheLayer GridSet configuration logic is build tested to work
        // with an actual ArcGISCacheLayer configuration test using the GWC tiling scheme steps:
        // https://www.geowebcache.org/docs/latest/configuration/layers/arcgistilingschemes.html

        final File configDir = temp.getRoot();
        ArcGISCacheLayer layer = new ArcGISCacheLayer("fakeLayerId");
        ApplicationContext appContext = createMock(ApplicationContext.class);
        final DefaultGridsets defaultGridSets = new DefaultGridsets(true, true);
        GridSetBroker gridSetBroker = new GridSetBroker(Arrays.asList(defaultGridSets));
        XMLConfiguration config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.setGridSetBroker(gridSetBroker);
        gridSetBroker.setApplicationContext(appContext);
        final HashMap<String, GridSetConfiguration> beans = new HashMap<>(2);
        beans.put("defaultGridSets", defaultGridSets);
        beans.put("xmlConfig", config);
        expect(appContext.getBeansOfType(GridSetConfiguration.class)).andReturn(beans);
        expect(appContext.getBean("defaultGridSets")).andReturn(defaultGridSets);
        expect(appContext.getBean("xmlConfig")).andReturn(config);
        replay(appContext);

        File tileConfig = new File("./src/test/resources/compactcacheV2/Conf.xml");
        layer.setTilingScheme(tileConfig);
        // ArcGISCacheLayer should initialize with gridSetBroker without throwing exceptions
        layer.initialize(gridSetBroker);
    }
}
