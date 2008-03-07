package org.geowebcache.layer;

import java.util.Properties;
import java.util.Arrays;

import junit.framework.TestCase;

public class MetaTileTest extends TestCase {
	
	
	protected void setUp() throws Exception {
        super.setUp();
	}
	
	
	public void test1MetaTile() throws Exception {
		LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0,0,180,90);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
    	profile.maxTileWidth = 180.0;
    	profile.maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;
    	
        profile.gridCalc = new GridCalculator(profile);
        int[] gridPos = {0,0,0};
        MetaTile mt = new MetaTile(profile,gridPos);
        
        int[] solution = {0, 0, 1, 0, 0};
        boolean test = Arrays.equals(mt.metaGrid,solution);
        if(test) {
        	assert(test);
        } else {
        	System.out.println("1 - " + mt.debugString());
        	System.out.println("test1MetaTile {" + Arrays.toString(solution ) + "} {" + mt.metaGrid +"}" );
        }
 	}
	
	public void test2MetaTile() throws Exception {
		LayerProfile profile = new LayerProfile();
        profile.bbox = new BBOX(0,0,180,90);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
    	profile.maxTileWidth = 180.0;
    	profile.maxTileHeight = 180.0;
        profile.zoomStart = 0;
        profile.zoomStop = 20;
    	
        profile.gridCalc = new GridCalculator(profile);
        int[] gridPos = {70,38,6};
        MetaTile mt = new MetaTile(profile,gridPos);
        
        int[] solution = {69,36,72,39,6};
        boolean test = Arrays.equals(mt.metaGrid,solution);
        if(test) {
        	assert(test);
        } else {
        	System.out.println("1 - " + mt.debugString());
        	System.out.println("test2MetaTile {" + Arrays.toString(solution ) + "} {" + Arrays.toString(mt.metaGrid) +"}" );
        }
 	}
	
	public void test3MetaTile() throws Exception {
		LayerProfile profile = new LayerProfile();
		profile.bbox = new BBOX(0,0,20037508.34,20037508.34);
		profile.gridBase = new BBOX(-20037508.34,-20037508.34,20037508.34,20037508.34);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        profile.maxTileWidth = 20037508.34*2;
    	profile.maxTileHeight = 20037508.34*2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;
    	
        profile.gridCalc = new GridCalculator(profile);
        int[] gridPos = {0,0,0};
        MetaTile mt = new MetaTile(profile,gridPos);
        
        int[] solution = {0, 0, 0, 0, 0};
        boolean test = Arrays.equals(mt.metaGrid,solution);
        if(test) {
        	assert(test);
        } else {
        	System.out.println("1 - " + mt.debugString());
        	System.out.println("test3MetaTile {" + Arrays.toString(solution ) + "} {" + mt.metaGrid +"}" );
        }
 	}
	
	public void test4MetaTile() throws Exception {
		LayerProfile profile = new LayerProfile();
		profile.bbox = new BBOX(0,0,20037508.34,20037508.34);
		profile.gridBase = new BBOX(-20037508.34,-20037508.34,20037508.34,20037508.34);
        profile.metaHeight = 3;
        profile.metaWidth = 3;
        profile.maxTileWidth = 20037508.34*2;
    	profile.maxTileHeight = 20037508.34*2;
        profile.zoomStart = 0;
        profile.zoomStop = 20;
    	
        profile.gridCalc = new GridCalculator(profile);
        int[] gridPos = {70,70,6};
        MetaTile mt = new MetaTile(profile,gridPos);
        
        int[] solution = {69,36,72,39,6};
        boolean test = Arrays.equals(mt.metaGrid,solution);
        if(test) {
        	assert(test);
        } else {
        	System.out.println("1 - " + mt.debugString());
        	System.out.println("test4MetaTile {" + Arrays.toString(solution ) + "} {" + Arrays.toString(mt.metaGrid) +"}" );
        }
 	}
}