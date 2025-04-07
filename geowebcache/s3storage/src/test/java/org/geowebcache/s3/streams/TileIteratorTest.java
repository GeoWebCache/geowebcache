package org.geowebcache.s3.streams;


import org.junit.Test;

import static org.geowebcache.s3.delete.DeleteTileRangeWithTileRange.ONE_BY_ONE_META_TILING_FACTOR;
import static org.geowebcache.s3.streams.StreamTestHelper.SINGLE_ZOOM_SINGLE_BOUND_MATCHING;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TileIteratorTest {
    @Test
    public void test_next() {
        TileIterator tileIterator = new TileIterator(SINGLE_ZOOM_SINGLE_BOUND_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);
        assertThat("Start at 0L, 0L", tileIterator.next(), is(new long[]{0L, 0L, 4L}));
        assertThat("Then  1L, 0L", tileIterator.next(), is(new long[]{1L, 0L, 4L}));
        assertThat("Then  2L, 0L", tileIterator.next(), is(new long[]{2L, 0L, 4L}));
        assertThat("Then  3L, 0L", tileIterator.next(), is(new long[]{3L, 0L, 4L}));
        assertThat("Then  0L, 1L", tileIterator.next(), is(new long[]{0L, 1L, 4L}));
        assertThat("Then  1L, 1L", tileIterator.next(), is(new long[]{1L, 1L, 4L}));
        assertThat("Then  2L, 1L", tileIterator.next(), is(new long[]{2L, 1L, 4L}));
        assertThat("Then  3L, 1L", tileIterator.next(), is(new long[]{3L, 1L, 4L}));
        assertThat("Then  0L, 2L", tileIterator.next(), is(new long[]{0L, 2L, 4L}));
        assertThat("Then  1L, 2L", tileIterator.next(), is(new long[]{1L, 2L, 4L}));
        assertThat("Then  2L, 2L", tileIterator.next(), is(new long[]{2L, 2L, 4L}));
        assertThat("Then  3L, 2L", tileIterator.next(), is(new long[]{3L, 2L, 4L}));
        assertThat("Then  0L, 3L", tileIterator.next(), is(new long[]{0L, 3L, 4L}));
        assertThat("Then  1L, 3L", tileIterator.next(), is(new long[]{1L, 3L, 4L}));
        assertThat("Then  2L, 3L", tileIterator.next(), is(new long[]{2L, 3L, 4L}));
        assertThat("Then  3L, 3L", tileIterator.next(), is(new long[]{3L, 3L, 4L}));
        assertThat("Iterator is exhausted", tileIterator.hasNext(), is(false));
    }

}