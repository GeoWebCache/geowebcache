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
package org.geowebcache.rest.reload;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.MockGridSetConfiguration;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.controller.ReloadController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"file*:/webapp/WEB-INF/web.xml", "file*:/webapp/WEB-INF/geowebcache-servlet.xml"})
public class ReloadTest {
    private MockMvc mockMvc;
    ReloadController reload;

    TileLayerDispatcher tld;

    @Before
    public void setup() throws GeoWebCacheException {

        BoundingBox extent = new BoundingBox(0, 0, 10E6, 10E6);
        boolean alignTopLeft = false;
        int levels = 10;
        Double metersPerUnit = 1.0;
        double pixelSize = 0.0028;
        int tileWidth = 256;
        int tileHeight = 256;
        boolean yCoordinateFirst = false;
        GridSet gridSet = GridSetFactory.createGridSet(
                "EPSG:3395",
                SRS.getSRS("EPSG:3395"),
                extent,
                alignTopLeft,
                levels,
                metersPerUnit,
                pixelSize,
                tileWidth,
                tileHeight,
                yCoordinateFirst);
        GridSetBroker gridSetBroker = new GridSetBroker(MockGridSetConfiguration.withDefaults(gridSet));

        tld = new TileLayerDispatcher(gridSetBroker, null);
        reload = new ReloadController();
        reload.setTileLayerDispatcher(tld);

        this.mockMvc = MockMvcBuilders.standaloneSetup(reload).build();
    }

    @Test
    public void testReloadConfiguration() throws Exception {
        String content = "reload_configuration=1";
        this.mockMvc
                .perform(post("/rest/reload")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .content(content)
                        .contextPath(""))
                .andExpect(status().is2xxSuccessful());
    }
}
