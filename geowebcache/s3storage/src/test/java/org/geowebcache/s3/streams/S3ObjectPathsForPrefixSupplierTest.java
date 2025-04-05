package org.geowebcache.s3.streams;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.geowebcache.s3.S3ObjectsWrapper;
import org.geowebcache.s3.streams.S3ObjectPathsForPrefixSupplier.Builder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class S3ObjectPathsForPrefixSupplierTest {
    private final String PREFIX = "prefix";
    private final String BUCKET = "bucket";

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
    AmazonS3 conn;

    @Mock
    S3ObjectsWrapper wrapper;

    private Builder builder;

    @Before
    public void setup() {
        when(wrapper.iterator()).thenReturn(S_3_OBJECT_SUMMARY_LIST.iterator());

        builder = S3ObjectPathsForPrefixSupplier.newBuilder()
                .withPrefix(PREFIX)
                .withBucket(BUCKET)
                .withWrapper(wrapper);
    }

    @Test
    public void testGet_FirstReturns_Summary_1() {
        var supplier = builder.build();
        var summary = supplier.get();
        assertNotNull("Should have returned summary", summary);
        assertEquals("Should have returned SUMMARY_1", SUMMARY_1, summary);
    }

    @Test
    public void testGet_CanCountAllElements() {
        var supplier = builder.build();
        var stream = Stream.generate(supplier);
        var count = stream.takeWhile(Objects::nonNull).count();
        assertEquals("Expected count", S_3_OBJECT_SUMMARY_LIST.size(), count);
    }

    @Test
    public void testPrefix_CannotBuildIfNullPrefix() {
        builder.withPrefix(null);
        assertThrows(NullPointerException.class, () -> builder.build());
    }

    @Test
    public void testPrefix_CannotBuildIfNullBucket() {
        builder.withBucket(null);
        assertThrows(NullPointerException.class, () -> builder.build());
    }

    @Test
    public void testPrefix_CannotBuildIfNullConn() {
        builder.withWrapper(null);
        assertThrows(NullPointerException.class, () -> builder.build());
    }
}
