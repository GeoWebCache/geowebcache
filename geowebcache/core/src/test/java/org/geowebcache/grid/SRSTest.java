package org.geowebcache.grid;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Simple Test class for testing the behavior of a {@link GridSubset} with a non-zero zoomStart parameter. */
public class SRSTest {
    private SRS createSRSWithReflection(int epsgCode) {
        return createSRSWithReflection(epsgCode, null);
    }

    private SRS createSRSWithReflection(int epsgCode, List<Integer> aliases) {
        try {
            Constructor<SRS> constructor = SRS.class.getDeclaredConstructor(int.class, List.class);
            constructor.setAccessible(true);

            return constructor.newInstance(epsgCode, aliases);
        } catch (NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            Assert.fail("Failed to invoke SRS constructor via reflection: " + e.getMessage());
            return null;
        }
    }

    /** Two SRS objects created with the same EPSG code should be equal */
    @Test
    public void testCompareSameSRS() {
        SRS srs1 = createSRSWithReflection(3308);
        SRS srs2 = createSRSWithReflection(3308);

        Assert.assertEquals("Expect two identical SRS", srs1, srs2);
        Assert.assertEquals("Expect two identical SRS", srs2, srs1);
    }

    /** Two SRS objects created with different EPSG codes should not be equal */
    @Test
    public void testCompareDifferentSRS() {
        SRS srs1 = createSRSWithReflection(3308);
        SRS srs2 = createSRSWithReflection(3857);

        Assert.assertNotEquals("Expect two different SRS", srs1, srs2);
        Assert.assertNotEquals("Expect two different SRS", srs2, srs1);
    }

    /** But two different SRS objects created with alias EPSG codes should be equal */
    @Test
    public void testCompareAliasSRS() {
        SRS srs1 = createSRSWithReflection(3857, List.of(900913));
        SRS srs2 = createSRSWithReflection(900913, List.of(3857));

        Assert.assertEquals("Expect two alias SRS to be equal", srs1, srs2);
        Assert.assertEquals("Expect two alias SRS to be equal", srs2, srs1);
    }

    /** Two different SRS objects. One has an alias of the other, but not vice versa. They should still be equal. */
    @Test
    public void testCompareOneDirectionalAliasSRS() {
        SRS srs1 = createSRSWithReflection(900913, List.of(3857));
        SRS srs2 = createSRSWithReflection(3857);

        Assert.assertEquals("Expect two alias SRS to be equal", srs1, srs2);
        Assert.assertEquals("Expect two alias SRS to be equal", srs2, srs1);
    }
}
