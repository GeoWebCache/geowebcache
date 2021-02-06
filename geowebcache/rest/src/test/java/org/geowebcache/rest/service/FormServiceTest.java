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
 * @author Kevin Smith (Boundless) 2018
 */
package org.geowebcache.rest.service;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.iterators.EmptyIterator;
import org.easymock.EasyMock;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.ImageMime;
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
        breeder = EasyMock.createNiceMock("breeder", TileBreeder.class);
        service.setTileBreeder(breeder);
    }

    @Test
    public void testKill() {
        Map<String, String> form = new HashMap<>();
        form.put("kill_thread", "1");
        form.put("thread_id", "2");
        TileLayer tl = EasyMock.createMock("tl", TileLayer.class);
        expect(breeder.terminateGWCTask(2)).andReturn(true);
        expect(tl.getName()).andStubReturn("testLayer");
        replay(tl, breeder);
        ResponseEntity<?> response = service.handleKillThreadPost(form, tl);
        verify(tl, breeder);

        assertThat(response, hasProperty("statusCode", equalTo(HttpStatus.OK)));
        assertThat(
                response,
                hasProperty("body", Matchers.containsString("Requested to terminate task 2")));
    }

    @Test
    public void testThreads() {
        expect(breeder.getPendingTasks()).andReturn(EmptyIterator.emptyIterator()).anyTimes();
        expect(breeder.getRunningAndPendingTasks())
                .andReturn(EmptyIterator.emptyIterator())
                .anyTimes();
        replay(breeder);
        Set<String> layer1GridSet = new HashSet<>(Arrays.asList("test_grid1"));
        GridSubset gs = createMock(GridSubset.class);
        expect(gs.getOriginalExtent()).andReturn(new BoundingBox(0, 0, 10, 10)).anyTimes();
        expect(gs.getZoomStart()).andReturn(0).anyTimes();
        expect(gs.getZoomStop()).andReturn(20).anyTimes();
        replay(gs);
        TileLayer tl1 = createMock(TileLayer.class);
        expect(tl1.getGridSubsets()).andReturn(layer1GridSet).anyTimes();
        expect(tl1.getGridSubset("test_grid1")).andReturn(gs).anyTimes();
        expect(tl1.getMimeTypes()).andReturn(Arrays.asList(ImageMime.png));
        expect(tl1.getParameterFilters()).andReturn(Collections.emptyList()).anyTimes();
        expect(tl1.getName()).andStubReturn("testLayer");
        replay(tl1);

        // in lexicographic order after 10 there was 100
        String html = service.makeFormPage(tl1, false);
        assertTrue(
                html.contains(
                        "<select name=\"threadCount\">\n"
                                + "<option value=\"01\">01</option>\n"
                                + "<option value=\"02\">02</option>\n"
                                + "<option value=\"03\">03</option>\n"
                                + "<option value=\"04\">04</option>\n"
                                + "<option value=\"05\">05</option>\n"
                                + "<option value=\"06\">06</option>\n"
                                + "<option value=\"07\">07</option>\n"
                                + "<option value=\"08\">08</option>\n"
                                + "<option value=\"09\">09</option>\n"
                                + "<option value=\"10\">10</option>\n"
                                + "<option value=\"11\">11</option>\n"
                                + "<option value=\"12\">12</option>\n"
                                + "<option value=\"13\">13</option>\n"
                                + "<option value=\"14\">14</option>\n"
                                + "<option value=\"15\">15</option>"));
    }
}
