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
package org.geowebcache.diskquota.rest.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import org.geowebcache.diskquota.DiskQuotaConfig;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

public class DiskQuotaControllerTest {

    private DiskQuotaConfig config;

    private DiskQuotaMonitor monitor;

    private DiskQuotaController controller;

    @Before
    public void setUp() {
        config = new DiskQuotaConfig();
        config.setDefaults();

        monitor = createStrictMock(DiskQuotaMonitor.class);
        expect(monitor.getConfig()).andReturn(config);

        controller = new DiskQuotaController(null);
        controller.setDiskQuotaMonitor(monitor);
    }

    /** A PUT applies the changes and saves the configuration. */
    @Test
    public void testPutSavesConfig() throws Exception {
        monitor.saveConfig();
        replay(monitor);

        ResponseEntity<?> response =
                put("<gwcQuotaConfiguration><maxConcurrentCleanUps>5</maxConcurrentCleanUps></gwcQuotaConfiguration>");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Integer.valueOf(5), config.getMaxConcurrentCleanUps());
        verify(monitor);
    }

    private ResponseEntity<?> put(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/rest/diskquota.xml");
        request.setPathInfo("/rest/diskquota.xml");
        request.setContent(body.getBytes(UTF_8));
        return controller.doPut(request);
    }
}
