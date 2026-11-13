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
 * <p>Copyright 2026
 */
package org.geowebcache.rest.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import java.util.List;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.rest.converter.GWCConverter;
import org.geowebcache.storage.memorycache.HeapKeyValueStoreInfo;
import org.geowebcache.storage.memorycache.MemoryCacheInfo;
import org.geowebcache.util.ApplicationContextProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class MemoryCacheControllerTest {

    private static final String SETTINGS =
            """
            <memoryCache>
              <enabled>true</enabled>
              <timeToLive>60</timeToLive>
              <backend class="heapKeyValueStore">
                <maxSizeMB>16</maxSizeMB>
              </backend>
            </memoryCache>""";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private XMLConfiguration config;

    private MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {
        config = loadConfig();
        mockMvc = MockMvcBuilders.standaloneSetup(new MemoryCacheController(config))
                .setMessageConverters(new GWCConverter<>(new ApplicationContextProvider()))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    private XMLConfiguration loadConfig() throws Exception {
        XMLConfiguration loaded = new XMLConfiguration(null, temp.getRoot().getAbsolutePath());
        loaded.setGridSetBroker(new GridSetBroker(List.of(new DefaultGridsets(false, false))));
        loaded.afterPropertiesSet();
        return loaded;
    }

    @Test
    public void testPutSavesSettings() throws Exception {
        putSettings(SETTINGS).andExpect(status().isOk());

        MemoryCacheInfo saved = loadConfig().getMemoryCache();
        assertEquals(60, saved.getTimeToLive());
        assertEquals(16, ((HeapKeyValueStoreInfo) saved.getBackend()).getMaxSizeMB());
        mockMvc.perform(get("/rest/memorycache").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(xpath("/memoryCache/enabled").string("true"))
                .andExpect(xpath("/memoryCache/backend/@class").string("heapKeyValueStore"))
                .andExpect(xpath("/memoryCache/backend/maxSizeMB").string("16"));
    }

    @Test
    public void testPutSameSettingsKeepsCache() throws Exception {
        putSettings(SETTINGS);
        MemoryCacheInfo saved = config.getMemoryCache();

        putSettings(SETTINGS).andExpect(status().isOk());
        // the cache is rebuilt, and emptied, when the settings instance changes
        assertSame(saved, config.getMemoryCache());
    }

    @Test
    public void testPutRejectsTimeToLive() throws Exception {
        putSettings("<memoryCache><timeToLive>0</timeToLive></memoryCache>")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Time to live must be at least 1 second, got 0")));
        assertNull(config.getMemoryCache());
    }

    @Test
    public void testPutRejectsUnknownBackend() throws Exception {
        putSettings("<memoryCache><backend class=\"missingStore\"/></memoryCache>")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Backend missingStore is not installed")));
        assertNull(config.getMemoryCache());
    }

    private ResultActions putSettings(String xml) throws Exception {
        return mockMvc.perform(
                put("/rest/memorycache").contentType(MediaType.APPLICATION_XML).content(xml));
    }
}
