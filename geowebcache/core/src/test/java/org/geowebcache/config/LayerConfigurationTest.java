package org.geowebcache.config;

import static org.junit.Assert.*;

import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.*;

import org.geowebcache.layer.TileLayer;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class LayerConfigurationTest {
    
    TileLayerConfiguration config;
    
    @Rule 
    public ExpectedException exception = ExpectedException.none();
    
    @Before
    public void setUpTestUnit() throws Exception {
        config = getConfig();
    }
    
    @Test
    public void testCanSaveGoodLayer() throws Exception {
        assertThat(config.canSave(getGoodLayer("test", 1)), equalTo(true));
    }
    
    @Test
    public void testAdd() throws Exception {
        TileLayer goodLayer = getGoodLayer("test", 1);
        config.addLayer(goodLayer);
        TileLayer retrieved = config.getTileLayerById("test");
        assertThat(retrieved, layerEquals(goodLayer));
    }
    
    @Test
    public void testPersistAdd() throws Exception {
        TileLayer goodLayer = getGoodLayer("test", 1);
        config.addLayer(goodLayer);
        
        config.save(); // TODO Remove this
        
        TileLayerConfiguration config2 = getConfig();
        TileLayer retrieved = config2.getTileLayerById("test");
        assertThat(retrieved, layerEquals(goodLayer));
    }
    
    @Test
    public void testDoubleAddException() throws Exception {
        TileLayer goodLayer = getGoodLayer("test", 1);
        TileLayer doubleLayer = getGoodLayer("test", 2);
        assertThat("Invalid test", goodLayer, not(layerEquals(doubleLayer)));
        config.addLayer(goodLayer);
        exception.expect(instanceOf(IllegalArgumentException.class)); // May want to change to something more specific.
        config.addLayer(doubleLayer);
    }
    
    @Test
    public void testDoubleAddNoChange() throws Exception {
        TileLayer goodLayer = getGoodLayer("test", 1);
        TileLayer doubleLayer = getGoodLayer("test", 2);
        assertThat("Invalid test", goodLayer, not(layerEquals(doubleLayer)));
        config.addLayer(goodLayer);
        try {
            config.addLayer(doubleLayer);
        } catch (IllegalArgumentException ex) { // May want to change to something more specific.
            
        }
        TileLayer retrieved = config.getTileLayerById("test");
        assertThat(retrieved, layerEquals(goodLayer));
    }
    
    @Test
    public void testCanSaveBadLayer() throws Exception {
        assertThat(config.canSave(getBadLayer("test", 1)), equalTo(false));
    }
    
    @Test
    public void testAddBadLayerException() throws Exception {
        TileLayer badLayer = getBadLayer("test", 1);
        exception.expect(IllegalArgumentException.class);// May want to change to something more specific.
        config.addLayer(badLayer);
    }
    
    @Test
    public void testBadLayerNoAdd() throws Exception {
        TileLayer badLayer = getGoodLayer("test", 1);
        config.addLayer(badLayer);
        try {
            config.addLayer(badLayer);
        } catch (IllegalArgumentException ex) { // May want to change to something more specific.
            
        }
        TileLayer retrieved = config.getTileLayerById("test");
        assertThat(retrieved, layerEquals(badLayer));
    }
    
    @Test
    public void testRemove() throws Exception {
        testAdd();
        assertThat(config.removeLayer("test"), is(true));
        TileLayer retrieved = config.getTileLayerById("test");
        assertThat(retrieved, nullValue());
    }
    
    @Test
    public void testPersistRemove() throws Exception {
        testPersistAdd();
        
        assertThat(config.removeLayer("test"), is(true));
        
        config.save(); // TODO Remove this
        
        TileLayerConfiguration config2 = getConfig();
        TileLayer retrieved = config2.getTileLayerById("test");
        assertThat(retrieved, nullValue());
    }
    
    @Test
    public void testGetNotExists() throws Exception {
        TileLayer retrieved = config.getTileLayerById("layerThatDoesntExist");
        assertThat(retrieved, nullValue()); // Possibly should be exception instead?
    }
    
    @Test
    public void testRemoveNotExists() throws Exception {
        assertThat(config.removeLayer("layerThatDoesntExist"), is(false)); // Possibly should be exception instead?
    }
    @Test
    public void testModify() throws Exception {
        testAdd();
        TileLayer goodLayer = getGoodLayer("test", 2);
        config.modifyLayer(goodLayer);
        
        TileLayer retrieved = config.getTileLayerById("test");
        assertThat(retrieved, layerEquals(goodLayer));
    }

    @Test
    public void testModifyBadLayerException() throws Exception {
        testAdd();
        TileLayer badLayer = getBadLayer("test", 2);
        
        exception.expect(IllegalArgumentException.class); // Could be more specific
        
        config.modifyLayer(badLayer);
    }

    @Test
    public void testModifyBadLayerNoChange() throws Exception {
        testAdd();
        TileLayer goodLayer = config.getTileLayerById("test");
        TileLayer badLayer = getBadLayer("test", 2);
        
        try {
            config.modifyLayer(badLayer);
        } catch (IllegalArgumentException ex) { // Could be more specific
            
        }
        
        TileLayer retrieved = config.getTileLayerById("test");
        assertThat(retrieved, layerEquals(goodLayer));
    }
    
    @Test
    public void testPersistModify() throws Exception {
        testPersistAdd();
        
        TileLayer goodLayer = getGoodLayer("test", 2);
        config.modifyLayer(goodLayer);
        
        config.save(); // TODO Remove this
        
        TileLayerConfiguration config2 = getConfig();
        TileLayer retrieved = config2.getTileLayerById("test");
        assertThat(retrieved, layerEquals(goodLayer));
    }
    
    @Test
    public void testModifyNotExistsExcpetion() throws Exception {
        TileLayer goodLayer = getGoodLayer("test", 2);
        exception.expect(NoSuchElementException.class);// Inconsistent with other methods
        config.modifyLayer(goodLayer);
    }
    
    @Test
    public void testModifyNotExistsNoChange() throws Exception {
        TileLayer goodLayer = getGoodLayer("layerThatDoesntExist", 2);
        try {
            config.modifyLayer(goodLayer);
        } catch(NoSuchElementException ex) {// Inconsistent with other methods.
            
        }
        TileLayer retrieved = config.getTileLayerById("layerThatDoesntExist");
        assertThat(retrieved, nullValue()); // Possibly should be exception instead?
    }
    
    @Test
    public void testGetExisting() throws Exception {
        TileLayer retrieved = config.getTileLayerById(getExistingLayer());
        assertThat(retrieved, notNullValue());
    }
    
    /**
     * Create a layer that should be saveable in the configuration being tested. Throw 
     * AssumptionViolatedException if this is a read only TileLayerConfiguration.
     * @param id ID for the layer
     * @param rand Layers created with different values should not be equal to one another.
     * @return
     */
    protected abstract TileLayer getGoodLayer(String id, int rand) throws Exception;
    
    /**
     * Create a layer that should not be saveable in the configuration being tested. Throw 
     * AssumptionViolatedException if this is a read only TileLayerConfiguration.
     * @param id ID for the layer
     * @param rand Layers created with different values should not be equal to one another.
     * @return
     */
    protected abstract TileLayer getBadLayer(String id, int rand) throws Exception;
    
    /**
     * Get an ID for a pre-existing layer. Throw AssumptionViolatedException if this this
     * configuration does not have existing layers.
     * @param id ID for the layer
     * @param rand Layers created with different values should not be equal to one another.
     * @return
     */
    protected abstract String getExistingLayer() throws Exception;

    /**
     * Create a TileLayerConfiguration to test.  Subsequent calls should create new configurations using the
     * same persistence or throw AssumptionViolatedException if this is a non-persistent 
     * configuration.
     * @return
     * @throws Exception 
     */
    protected abstract TileLayerConfiguration getConfig() throws Exception;
    
    /**
     * Check that two layers created by calls to getGoodLayer, which may have been persisted and 
     * depersisted, are equal if and only if they had the same rand value.
     * @param expected
     * @return
     */
    protected abstract Matcher<TileLayer> layerEquals(final TileLayer expected);
}
