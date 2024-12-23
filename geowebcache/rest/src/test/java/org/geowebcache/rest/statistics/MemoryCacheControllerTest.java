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
 * @author David Vick, Boundless, 2017
 */
package org.geowebcache.rest.statistics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.MockConfigurationResourceProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.rest.controller.MemoryCacheController;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"file*:/webapp/WEB-INF/web.xml", "file*:/webapp/WEB-INF/geowebcache-servlet.xml"})
public class MemoryCacheControllerTest {
    private MockMvc mockMvc;

    MemoryCacheController mcc;

    @Before
    @SuppressWarnings("deprecation") // setUseSuffixPatternMatch is deprecated because Spring wants to
    // discourage extensions in paths
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.setGridSetBroker(gridSetBroker);
        xmlConfig.afterPropertiesSet();

        mcc = new MemoryCacheController(null);
        this.mockMvc = MockMvcBuilders.standaloneSetup(mcc)
                .setUseSuffixPatternMatch(true)
                .build();
    }

    @Test
    public void testStatisticsXml() throws Exception {
        // Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mcc.setBlobStore(mbs);
        mbs.setCacheProvider(cache);

        this.mockMvc
                .perform(get("/rest/statistics").accept("application/xml").contextPath(""))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testStatisticsJson() throws Exception {
        // Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mcc.setBlobStore(mbs);
        mbs.setCacheProvider(cache);

        this.mockMvc
                .perform(get("/rest/statistics").accept("application/json").contextPath(""))
                .andExpect(status().is2xxSuccessful());
    }

    private XMLConfiguration loadXMLConfig() {

        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(
                    null,
                    new MockConfigurationResourceProvider(() -> XMLConfiguration.class.getResourceAsStream(
                            XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE)));
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }
}
