package org.geowebcache.layer;

import java.util.Properties;
import java.util.Arrays;

import junit.framework.TestCase;

public class MetaTileTest extends TestCase {
	
	
	protected void setUp() throws Exception {
        super.setUp();
        //profile = new LayerProfile(new Properties());
	}
	
	
	public void testMetaTile1() throws Exception {
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
        
        System.out.println("1 - " + mt.debugString());
        
//		LayerProfile profile = new LayerProfile();
//        profile.bbox = new BBOX(0,0,180,90);
//        profile.gridBase = new BBOX(-180,-90,180,90);
//        profile.metaHeight = 1;
//        profile.metaWidth = 1;
//        
//        profile.gridCalc = new GridCalculator(profile);
//        int[] gridPos = {0,0,0};
//        
//        MetaTile mt = new MetaTile(profile,gridPos);
//        System.out.println("1 - " + mt.debugString());
        assert(true);
 	}
//	
//	public void testMetaTile2() throws Exception {
//        profile.bbox = new BBOX(0,0,180,90);
//        profile.gridBase = new BBOX(-180,-90,180,90);
//        profile.metaHeight = 1;
//        profile.metaWidth = 1;
//        
//        profile.gridCalc = new GridCalculator(profile);
//        int[] gridPos = {1,1,1};
//        
//        MetaTile mt = new MetaTile(profile,gridPos);
//        System.out.println("2 - " + mt.debugString());
// 	}
}