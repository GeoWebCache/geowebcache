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
 * @author David Vick, Boundless, 2017
 */

package org.geowebcache.rest.statistics;


import org.geowebcache.GeoWebCacheException;
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

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class MemoryCacheControllerTest {
    private MockMvc mockMvc;

    MemoryCacheController mcc;

    @Before
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.initialize(gridSetBroker);

        mcc = new MemoryCacheController();
        mcc.setXMLConfiguration(xmlConfig);
        this.mockMvc = MockMvcBuilders.standaloneSetup(mcc).build();
    }

    @Test
    public void testStatisticsXml() throws Exception {
        //Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mcc.setBlobStore(mbs);
        mbs.setCacheProvider(cache);

        this.mockMvc.perform(get("/rest/statistics.xml")
                .contextPath("")).andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testStatisticsJson() throws Exception {
        //Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mcc.setBlobStore(mbs);
        mbs.setCacheProvider(cache);

        this.mockMvc.perform(get("/rest/statistics.json")
                .contextPath("")).andExpect(status().is2xxSuccessful());
    }

    private XMLConfiguration loadXMLConfig() {

        InputStream is = XMLConfiguration.class
                .getResourceAsStream(XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }
}
