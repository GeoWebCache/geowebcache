package org.geowebcache.layer;

import java.util.Properties;
import java.util.Arrays;

import junit.framework.TestCase;

public class GridCalculatorTest extends TestCase {
	LayerProfile profile = null;
	
	protected void setUp() throws Exception {
        super.setUp();
        
        profile = new LayerProfile(new Properties());

	}
	
	public void testgridLevels1() throws Exception {
		
        profile.bbox = new BBOX(0,0,180,90);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        
		GridCalculator gridCalc = new GridCalculator(profile, profile.gridBase);
		gridCalc.calculateGridBounds(profile.bbox, 0, 20);
		
		int[][] solution = {
				{1, 0, 1, 0},
				{2, 1, 3, 1},
				{4, 2, 7, 3},
				{8, 4, 15, 7},
				{16, 8, 31, 15} };
		
		for(int i=0; i<solution.length; i++) {
			int[] bounds = gridCalc.getGridBounds(i);
			
			assert(Arrays.equals(solution[i], bounds));
		}
	}
	
	public void testgridLevels2() throws Exception {
        profile.bbox = new BBOX(0,0,180,90);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        
		GridCalculator gridCalc = new GridCalculator(profile, profile.gridBase);
		gridCalc.calculateGridBounds(profile.bbox, 0, 20);
		
		int[][] solution = {
				{1,0,1,0},
				{0,0,3,1},
				{3,0,7,3},
				{6,3,15,7},
				{15,6,31,15},
				{30,15,63,31},
				{63,30,127,63}
		};
		
		for(int i=0; i<solution.length; i++) {
			int[] bounds = gridCalc.getGridBounds(i);
			
			assert(Arrays.equals(solution[i], bounds));
		}
		
		//for(int i=0; i<10; i++) {
		//	int[] bounds = gridCalc.getGridBounds(i);
		//	System.out.println("{"+bounds[0]+","+bounds[1]+","+bounds[2]+","+bounds[3]+"}");
		//}
	}
	
	public void testgridLevels3() throws Exception {
        profile.bbox = new BBOX(-10.0,-10.0,10.0,10.0);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        
		GridCalculator gridCalc = new GridCalculator(profile, profile.gridBase);
		gridCalc.calculateGridBounds(profile.bbox, 0, 20);
		
		for(int i=0; i<10; i++) {
			int[] bounds = gridCalc.getGridBounds(i);
			System.out.println("{"+bounds[0]+","+bounds[1]+","+bounds[2]+","+bounds[3]+"}");
		}
	}
	
	

}