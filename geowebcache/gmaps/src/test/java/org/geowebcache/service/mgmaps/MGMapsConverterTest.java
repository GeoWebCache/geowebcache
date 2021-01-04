package org.geowebcache.service.mgmaps;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class MGMapsConverterTest {

    @Before
    public void setUp() throws Exception {}

    /** see Modified for MGMaps API */
    @Test
    public void testMGMapsConverter() throws Exception {
        /* Check origin location */
        int x = 0;
        int y = 0;
        int z = 17;
        long[] gridLoc = MGMapsConverter.convert(z, x, y);
        long[] solution = {0, 0, 0};

        assert (Arrays.equals(gridLoc, solution));

        /* Check zoomlevel */
        x = 0;
        y = 0;
        z = 17 - 1;
        solution[0] = 0;
        solution[1] = 1;
        solution[2] = 1;
        gridLoc = MGMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        /* Check top right */
        x = 1;
        y = 0;
        z = 17 - 1;
        solution[0] = 1;
        solution[1] = 1;
        solution[2] = 1;
        gridLoc = MGMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        /* Check top right, zoomlevel */
        x = 3;
        y = 0;
        z = 17 - 2;
        solution[0] = 3;
        solution[1] = 3;
        solution[2] = 2;
        gridLoc = MGMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        /* Check middle */
        x = 2;
        y = 1;
        z = 17 - 2;
        solution[0] = 2;
        solution[1] = 2;
        solution[2] = 2;
        gridLoc = MGMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        // System.out.println(Arrays.toString(solution));
        // System.out.println(Arrays.toString(gridLoc));
    }
}
