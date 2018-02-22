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
 * @author Kevin Smith (Boundless) 2018
 *  
 */

package org.geowebcache.rest.service;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.seed.TileBreeder;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class FormServiceTest {
    
    FormService service;
    
    public MockWepAppContextRule context = new MockWepAppContextRule();

    private TileBreeder breeder;
    
    @Before
    public void setUp() throws Exception {
        service = new FormService();
        breeder = EasyMock.createMock("breeder", TileBreeder.class);
        service.setTileBreeder(breeder);
    }
    
    @Test
    public void testKill() {
        Map<String,String> form = new HashMap<>();
        form.put("kill_thread", "1");
        form.put("thread_id", "2");
        TileLayer tl = EasyMock.createMock("tl", TileLayer.class);
        EasyMock.expect(breeder.terminateGWCTask(2)).andReturn(true);
        EasyMock.expect(tl.getName()).andStubReturn("testLayer");
        EasyMock.replay(tl, breeder);
        ResponseEntity<?> response = service.handleKillThreadPost(form, tl);
        EasyMock.verify(tl, breeder);
        
        assertThat(response, hasProperty("statusCode", equalTo(HttpStatus.OK)));
        assertThat(response, hasProperty("body", Matchers.containsString("Requested to terminate task 2")));
    }
}
