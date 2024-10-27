/*
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
 */

package org.geowebcache.storage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.base.Ticker;
import java.io.InputStream;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.junit.Before;
import org.junit.Test;

/** @author Kevin Smith, Boundless */
public class TransientCacheTest {

    TransientCache transCache;
    TestTicker ticker;

    public static final long EXPIRE_TIME = 2000;
    public static final int MAX_TILES = 5;
    public static final int MAX_SPACE_KiB = 5;

    @Before
    public void setUp() throws Exception {
        transCache = new TransientCache(MAX_TILES, MAX_SPACE_KiB, EXPIRE_TIME);
        ticker = new TestTicker(System.nanoTime());
        transCache.setTicker(ticker);
    }

    @Test
    public void testHit() throws Exception {
        Resource r = new ByteArrayResource(new byte[] {1, 2, 3});

        transCache.put("foo", r);

        ticker.advanceMilli(EXPIRE_TIME - 1);
        Resource result = transCache.get("foo");
        assertThat(result, notNullValue());
        assertThat(r.getLastModified(), equalTo(r.getLastModified()));
        try (InputStream is = result.getInputStream()) {
            assertThat(is.read(), equalTo(1));
            assertThat(is.read(), equalTo(2));
            assertThat(is.read(), equalTo(3));
            assertThat(is.read(), equalTo(-1));
        }
    }

    @Test
    public void testRemoveOnHit() throws Exception {
        Resource r = new ByteArrayResource(new byte[] {1, 2, 3});

        transCache.put("foo", r);

        ticker.advanceMilli(EXPIRE_TIME - 1);

        transCache.get("foo"); // Hit

        Resource result = transCache.get("foo");
        assertThat(result, nullValue()); // Should have been cleared
    }

    @Test
    public void testRemoveOnExpire() throws Exception {
        Resource r = new ByteArrayResource(new byte[] {1, 2, 3});

        transCache.put("foo", r);

        ticker.advanceMilli(EXPIRE_TIME + 1);

        Resource result = transCache.get("foo");
        assertThat(result, nullValue()); // Should have expired
    }

    @Test
    public void testRemoveWhenMaxTiles() throws Exception {

        for (byte i = 0; i < MAX_TILES; i++) {
            Resource r = new ByteArrayResource(new byte[] {(byte) (i + 1), (byte) (i + 2), (byte) (i + 3)});
            transCache.put("foo" + i, r);
            assertThat(transCache.size(), is(i + 1));
        }
        assertThat(transCache.storageSize(), is((long) MAX_TILES * 3));
        Resource r = new ByteArrayResource(new byte[] {(byte) (MAX_TILES + 1), (byte) (MAX_TILES + 2)});
        transCache.put("foo" + MAX_TILES, r);
        assertThat(transCache.size(), is(MAX_TILES));
        assertThat(transCache.storageSize(), is((long) MAX_TILES * 3 - 1)); // remove a 3 byte  and add a 2 byte

        ticker.advanceMilli(1);

        Resource result1 = transCache.get("foo0");
        assertThat(result1, nullValue()); // Should have expired
        Resource result2 = transCache.get("foo1");
        assertThat(result2, notNullValue()); // Should still be cached
    }

    @Test
    public void testRemoveWhenMaxSpace() throws Exception {

        for (long i = 0; i < MAX_SPACE_KiB; i++) {
            Resource r =
                    new ByteArrayResource(new byte[i == 0 ? 1023 : 1024]); // make the first one 1 byte less than a KiB
            transCache.put("foo" + i, r);
            assertThat(
                    transCache.storageSize(), is((i + 1) * 1024 - 1)); // 1 KiB per resource, less a byte for the first
            ticker.advanceMilli(1);
        }
        assertThat(
                transCache.storageSize(),
                is((long) MAX_SPACE_KiB * 1024 - 1)); // 1 KiB per resource, less a byte for the first
        assertThat(transCache.size(), is(MAX_SPACE_KiB));
        Resource r = new ByteArrayResource(new byte[2]); // 2 bytes will go over the maximum
        transCache.put("foo" + MAX_SPACE_KiB, r);
        assertThat(
                transCache.storageSize(),
                is((long) (MAX_SPACE_KiB - 1) * 1024
                        + 2)); // 1 KiB for each of the resources except the first should be
        // removed, and the last is only 2 bytes.
        assertThat(transCache.size(), is(MAX_SPACE_KiB));

        ticker.advanceMilli(1);

        Resource result1 = transCache.get("foo0");
        assertThat(result1, nullValue()); // Should have expired
        Resource result2 = transCache.get("foo1");
        assertThat(result2, notNullValue()); // Should still be cached
    }

    private static class TestTicker extends Ticker {
        long time;

        public TestTicker(long startAt) {
            super();
            this.time = startAt;
        }

        @Override
        public long read() {
            // TODO Auto-generated method stub
            return time;
        }

        public void advanceMilli(long millis) {
            advanceNano(millis * 1000);
        }

        public void advanceNano(long nanos) {
            time += nanos;
        }
    }
}
