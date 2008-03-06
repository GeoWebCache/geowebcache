package org.geowebcache.layer;

import java.util.Properties;
import java.util.Arrays;

import junit.framework.TestCase;

public class MetaTileTest extends TestCase {
	LayerProfile profile = null;
	
	protected void setUp() throws Exception {
        super.setUp();
        profile = new LayerProfile(new Properties());

	}
	
	public void testMetaTile1() throws Exception {
        profile.bbox = new BBOX(0,0,180,90);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        
        profile.gridCalc = new GridCalculator(profile, profile.gridBase);
        
        int[] gridPos = {0,0,0};
        
        MetaTile mt = new MetaTile(profile,gridPos);
        System.out.println("1 - " + mt.debugString());
 	}
	
	public void testMetaTile2() throws Exception {
        profile.bbox = new BBOX(0,0,180,90);
        profile.gridBase = new BBOX(-180,-90,180,90);
        profile.metaHeight = 1;
        profile.metaWidth = 1;
        
        profile.gridCalc = new GridCalculator(profile, profile.gridBase);
        
        int[] gridPos = {1,1,1};
        
        MetaTile mt = new MetaTile(profile,gridPos);
        System.out.println("2 - " + mt.debugString());
 	}
}