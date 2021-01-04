package org.geowebcache.service.ve;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class VEConverterTest {

    @Before
    public void setUp() throws Exception {}

    /**
     * Remember, quadkeys look like this: 0 1 2 3
     *
     * <p>And the "most zoomed out" is level 1,
     */
    @Test
    public void testVEConverter() throws Exception {
        /* Check origin location */
        String quadKey = "2";
        long[] gridLoc = VEConverter.convert(quadKey);
        long[] solution = {0, 0, 1};
        assert (Arrays.equals(gridLoc, solution));

        /* Check zoomlevel */
        quadKey = "22";
        solution[0] = 0;
        solution[1] = 0;
        solution[2] = 2;
        gridLoc = VEConverter.convert(quadKey);
        assert (Arrays.equals(gridLoc, solution));

        /* Check top right */
        quadKey = "1";
        solution[0] = 1;
        solution[1] = 1;
        solution[2] = 1;
        gridLoc = VEConverter.convert(quadKey);
        assert (Arrays.equals(gridLoc, solution));

        /* Check top right, zoomlevel */
        quadKey = "11";
        solution[0] = 3;
        solution[1] = 3;
        solution[2] = 2;
        gridLoc = VEConverter.convert(quadKey);
        assert (Arrays.equals(gridLoc, solution));

        /* Check middle */
        quadKey = "122";
        solution[0] = 4;
        solution[1] = 4;
        solution[2] = 3;
        gridLoc = VEConverter.convert(quadKey);
        assert (Arrays.equals(gridLoc, solution));
    }
}
