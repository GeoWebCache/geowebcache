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

package org.geowebcache.rest.webresources;

import org.geowebcache.rest.controller.ByteStreamController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class ByteStreamControllerTest {
    private MockMvc mockMvc;


    ByteStreamController bsc;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        bsc = new ByteStreamController();
        this.mockMvc = MockMvcBuilders.standaloneSetup(bsc).build();
    }

    @Test
    public void setByteStreamController()  throws Exception {
        mockMvc.perform(get("/rest/web/doesnt%20exist")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testResourceFoundPNG() throws Exception {
        mockMvc.perform(get("/rest/web/test.png")).andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testResourceFoundCSS() throws Exception {
        mockMvc.perform(get("/rest/web/test.css")).andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testClass() throws Exception  {
        mockMvc.perform(get("/rest/web/ByteStreamerRestlet.class"));
    }

    @Test
    public void testAbsolute() throws Exception  {
        mockMvc.perform(get("/rest/web/org/geowebcache/shouldnt/access/test.png")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testBackreference() throws Exception  {
        mockMvc.perform(get("/rest/web/../../shouldnt/access/test.png")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testBackreference2() throws Exception  {
        mockMvc.perform(get("/rest/web/foo/../../../shouldnt/access/test.png")).andExpect(status().is4xxClientError());
    }
}
