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
 * @author Fernando Mino (Geosolutions), 2019
 */
package org.geowebcache.rest.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration()
@ContextConfiguration({
    "file:../web/src/main/webapp/WEB-INF/geowebcache-rest-context.xml",
    "file:../web/src/main/webapp/WEB-INF/geowebcache-core-context.xml"
})
public class SeedControllerTest {

    @Autowired private WebApplicationContext wac;

    private MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Configuration
    @ComponentScan(basePackages = {"org.geowebcache.rest.controller"})
    @EnableWebMvc
    static class Config {
        @Bean
        PropertyPlaceholderConfigurer propConfig() {
            PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
            ppc.setLocation(new ClassPathResource("test.properties"));
            return ppc;
        }
    }

    /** Checks correct media type for RestException response handling. GET method. */
    @Test
    public void testSeedGetContentType() throws Exception {
        mockMvc.perform(get("/rest/seed/{layer}", "xxxp4z85").accept(MediaType.TEXT_HTML))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is4xxClientError());
    }

    /** Checks correct media type for RestException response handling. POST method. */
    @Test
    public void testSeedPostContentType() throws Exception {
        mockMvc.perform(
                        post("/rest/seed/{layer}", "xxxp4z85")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .accept(MediaType.TEXT_HTML))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is4xxClientError());
    }
}
