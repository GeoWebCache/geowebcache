package org.geowebcache.s3.streams;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class S3ObjectPathsForPrefixSupplierTest {
    private static final String PREFIX = "prefix";
    private static final String BUCKET = "bucket";

    private static final List<S3ObjectSummary> S_3_OBJECT_SUMMARY_LIST = new ArrayList<>();
    private static final S3ObjectSummary SUMMARY_1 = new S3ObjectSummary();
    private static final S3ObjectSummary SUMMARY_2 = new S3ObjectSummary();
    private static final S3ObjectSummary SUMMARY_3 = new S3ObjectSummary();

    static {
        SUMMARY_1.setKey("key");
        S_3_OBJECT_SUMMARY_LIST.add(SUMMARY_1);
        S_3_OBJECT_SUMMARY_LIST.add(SUMMARY_2);
        S_3_OBJECT_SUMMARY_LIST.add(SUMMARY_3);
    }

    @Mock
    S3ObjectsWrapper wrapper;

    @Mock
    Logger logger;


    @Before
    public void setup() {
        when(wrapper.iterator()).thenReturn(S_3_OBJECT_SUMMARY_LIST.iterator());
    }

    @Test
    public void testGet_FirstReturns_Summary_1() {
        var supplier = new S3ObjectPathsForPrefixSupplier(PREFIX, BUCKET, wrapper, logger);
        var summary = supplier.get();
        assertNotNull("Should have returned summary", summary);
        assertEquals("Should have returned SUMMARY_1", SUMMARY_1, summary);
    }

    @Test
    public void testGet_CanCountAllElements() {
        var supplier = new S3ObjectPathsForPrefixSupplier(PREFIX, BUCKET, wrapper, logger);
        var stream = Stream.generate(supplier);
        var count = stream.takeWhile(Objects::nonNull).count();
        assertEquals("Expected count", S_3_OBJECT_SUMMARY_LIST.size(), count);
    }

    @Test
    public void testPrefix_CannotBuildIfNullPrefix() {
        assertThrows(NullPointerException.class, () ->  new S3ObjectPathsForPrefixSupplier(null, BUCKET, wrapper, logger));
    }

    @Test
    public void testPrefix_CannotBuildIfNullBucket() {
        assertThrows(NullPointerException.class, () ->  new S3ObjectPathsForPrefixSupplier(PREFIX, null, wrapper, logger));
    }

    @Test
    public void testPrefix_CannotBuildIfNullConn() {
        assertThrows(NullPointerException.class, () ->  new S3ObjectPathsForPrefixSupplier(PREFIX, BUCKET, null, logger));
    }


    @Test
    public void testPrefix_CannotBuildIfNullLogger() {
        assertThrows(NullPointerException.class, () ->  new S3ObjectPathsForPrefixSupplier(PREFIX, BUCKET, wrapper, null));
    }
}
