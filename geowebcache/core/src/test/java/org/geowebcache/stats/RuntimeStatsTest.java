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
 * @author Kevin Smith, Boundless, Copyright 2016
 */
package org.geowebcache.stats;

import java.time.Clock;
import java.util.Arrays;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class RuntimeStatsTest {

    @Before
    public void setUp() throws Exception {}

    long time;

    @Test
    public void test() {
        Clock clock = EasyMock.createMock("Clock", Clock.class);
        EasyMock.expect(clock.millis()).andStubAnswer(() -> time);
        EasyMock.replay(clock);
        time = 0;
        RuntimeStats stats = new RuntimeStats(1, Arrays.asList(60), Arrays.asList("Minutes"), clock);
        time += 500;
        stats.getHTMLStats();
        // Shouldn't get a divide by zero
        EasyMock.verify(clock);
    }
}
