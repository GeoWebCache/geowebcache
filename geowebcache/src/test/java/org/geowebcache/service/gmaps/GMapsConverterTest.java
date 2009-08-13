package org.geowebcache.service.gmaps;

import java.util.Arrays;

import junit.framework.TestCase;

public class GMapsConverterTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * see
     * http://code.google.com/apis/maps/documentation/overlays.html#Custom_Map_Types
     * 
     * @throws Exception
     */
    public void testGMapsConverter() throws Exception {
    	/* Check origin location */
    	int x = 0; int y = 0; int z = 0;
    	long[] gridLoc = GMapsConverter.convert(z, x, y);
    	long[] solution = {0,0,0};
    	assert(Arrays.equals(gridLoc, solution));
    	
    	/* Check zoomlevel */
    	x = 0; y = 0; z = 1;
    	solution[0] = 0; solution[1] = 1; solution[2] = 1;
    	gridLoc = GMapsConverter.convert(z, x, y);
    	assert(Arrays.equals(gridLoc, solution));
    	
    	/* Check top right */
    	x = 1; y = 0; z = 1;
    	solution[0] = 1; solution[1] = 1; solution[2] = 1;
    	gridLoc = GMapsConverter.convert(z, x, y);
    	assert(Arrays.equals(gridLoc, solution));
    	
    	/* Check top right, zoomlevel */
    	x = 3; y = 0; z = 2;
    	solution[0] = 3; solution[1] = 3; solution[2] = 2;
    	gridLoc = GMapsConverter.convert(z, x, y);
    	assert(Arrays.equals(gridLoc, solution));
    	
    	/* Check middle */
    	x = 2; y = 1; z = 2;
    	solution[0] = 2; solution[1] = 2; solution[2] = 2;
    	gridLoc = GMapsConverter.convert(z, x, y);
    	assert(Arrays.equals(gridLoc, solution));
    	
    	//System.out.println(Arrays.toString(solution));
    	//System.out.println(Arrays.toString(gridLoc));
    }
}