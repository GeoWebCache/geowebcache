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
package org.geowebcache.storage.memorycache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import com.google.common.base.Ticker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.Test;

public class HeapKeyValueStoreTest extends AbstractKeyValueStoreTest {

    private final AtomicLong now = new AtomicLong();

    private final Ticker ticker = new Ticker() {
        @Override
        public long read() {
            return now.get();
        }
    };

    @Override
    protected KeyValueStore createStore() {
        return new HeapKeyValueStore(1000, ticker);
    }

    @Test
    public void testPerEntryExpiry() {
        store.put("short", new byte[] {1}, 10);
        store.put("long", new byte[] {2}, 100);

        now.addAndGet(TimeUnit.SECONDS.toNanos(10));

        assertNull(store.get("short"));
        assertArrayEquals(new byte[] {2}, store.get("long"));
    }

    @Test
    public void testPutIfAbsentReplacesExpiredValue() {
        store.put("key", new byte[] {1}, 10);
        now.addAndGet(TimeUnit.SECONDS.toNanos(10));

        store.putIfAbsent("key", new byte[] {2}, 10);

        assertArrayEquals(new byte[] {2}, store.get("key"));
    }

    @Test
    public void testWeightBound() {
        IntStream.range(0, 20).forEach(i -> store.put("k" + i, new byte[100], 300));

        long present =
                IntStream.range(0, 20).filter(i -> store.get("k" + i) != null).count();
        assertThat(present, lessThanOrEqualTo(10L));
    }
}
