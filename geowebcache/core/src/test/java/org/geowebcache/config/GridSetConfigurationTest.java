package org.geowebcache.config;

import static org.junit.Assert.*;

import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.*;

import org.geowebcache.grid.GridSet;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;


/**
 * 
 * Abstract unit test for GridSet Configurations.  Checks that behavior is consistent across 
 * implementations.
 *
 */
public abstract class GridSetConfigurationTest {
    
    GridSetConfiguration config;
    
    @Rule 
    public ExpectedException exception = ExpectedException.none();
    
    @Before
    public void setUpTestUnit() throws Exception {
        config = getConfig();
    }
    
    @Test
    public void testCanSaveGoodGridSet() throws Exception {
        assertThat(config.canSave(getGoodGridSet("test", 1)), equalTo(true));
    }
    
    @Test
    public void testAdd() throws Exception {
        GridSet goodGridSet = getGoodGridSet("test", 1);
        config.addGridSet(goodGridSet);
        GridSet retrieved = config.getGridSet("test");
        assertThat(retrieved, GridSetEquals(goodGridSet));
    }
    
    @Test
    public void testPersistAdd() throws Exception {
        GridSet goodGridSet = getGoodGridSet("test", 1);
        config.addGridSet(goodGridSet);

        GridSetConfiguration config2 = getConfig();
        GridSet retrieved = config2.getGridSet("test");
        assertThat(retrieved, GridSetEquals(goodGridSet));
    }
    
    @Test
    public void testDoubleAddException() throws Exception {
        GridSet goodGridSet = getGoodGridSet("test", 1);
        GridSet doubleGridSet = getGoodGridSet("test", 2);
        assertThat("Invalid test", goodGridSet, not(GridSetEquals(doubleGridSet)));
        config.addGridSet(goodGridSet);
        exception.expect(instanceOf(IllegalArgumentException.class)); // May want to change to something more specific.
        config.addGridSet(doubleGridSet);
    }
    
    @Test
    public void testDoubleAddNoChange() throws Exception {
        GridSet goodGridSet = getGoodGridSet("test", 1);
        GridSet doubleGridSet = getGoodGridSet("test", 2);
        assertThat("Invalid test", goodGridSet, not(GridSetEquals(doubleGridSet)));
        config.addGridSet(goodGridSet);
        try {
            config.addGridSet(doubleGridSet);
        } catch (IllegalArgumentException ex) { // May want to change to something more specific.
            
        }
        GridSet retrieved = config.getGridSet("test");
        assertThat(retrieved, GridSetEquals(goodGridSet));
    }
    
    @Test
    public void testCanSaveBadGridSet() throws Exception {
        assertThat(config.canSave(getBadGridSet("test", 1)), equalTo(false));
    }
    
    @Test
    public void testAddBadGridSetException() throws Exception {
        GridSet badGridSet = getBadGridSet("test", 1);
        exception.expect(IllegalArgumentException.class);// May want to change to something more specific.
        config.addGridSet(badGridSet);
    }
    
    @Test
    public void testBadGridSetNoAdd() throws Exception {
        GridSet badGridSet = getGoodGridSet("test", 1);
        config.addGridSet(badGridSet);
        try {
            config.addGridSet(badGridSet);
        } catch (IllegalArgumentException ex) { // May want to change to something more specific.
            
        }
        GridSet retrieved = config.getGridSet("test");
        assertThat(retrieved, GridSetEquals(badGridSet));
    }
    
    @Test
    public void testRemove() throws Exception {
        testAdd();
        config.removeGridSet("test");
        GridSet retrieved = config.getGridSet("test");
        assertThat(retrieved, nullValue());
    }
    
    @Test
    public void testPersistRemove() throws Exception {
        testPersistAdd();
        
        config.removeGridSet("test");

        GridSetConfiguration config2 = getConfig();
        GridSet retrieved = config2.getGridSet("test");
        assertThat(retrieved, nullValue());
    }
    
    @Test
    public void testGetNotExists() throws Exception {
        exception.expect(NoSuchElementException.class);
        GridSet retrieved = config.getGridSet("GridSetThatDoesntExist");
    }
    
    @Test
    public void testRemoveNotExists() throws Exception {
        exception.expect(NoSuchElementException.class);
        config.removeGridSet("GridSetThatDoesntExist");
    }
    @Test
    public void testModify() throws Exception {
        testAdd();
        GridSet goodGridSet = getGoodGridSet("test", 2);
        config.modifyGridSet(goodGridSet);
        
        GridSet retrieved = config.getGridSet("test");
        assertThat(retrieved, GridSetEquals(goodGridSet));
    }

    @Test
    public void testModifyBadGridSetException() throws Exception {
        testAdd();
        GridSet badGridSet = getBadGridSet("test", 2);
        
        exception.expect(IllegalArgumentException.class); // Could be more specific
        
        config.modifyGridSet(badGridSet);
    }

    @Test
    public void testModifyBadGridSetNoChange() throws Exception {
        testAdd();
        GridSet goodGridSet = config.getGridSet("test");
        GridSet badGridSet = getBadGridSet("test", 2);
        
        try {
            config.modifyGridSet(badGridSet);
        } catch (IllegalArgumentException ex) { // Could be more specific
            
        }
        
        GridSet retrieved = config.getGridSet("test");
        assertThat(retrieved, GridSetEquals(goodGridSet));
    }
    
    @Test
    public void testPersistModify() throws Exception {
        testPersistAdd();
        
        GridSet goodGridSet = getGoodGridSet("test", 2);
        config.modifyGridSet(goodGridSet);

        GridSetConfiguration config2 = getConfig();
        GridSet retrieved = config2.getGridSet("test");
        assertThat(retrieved, GridSetEquals(goodGridSet));
    }
    
    @Test
    public void testModifyNotExistsExcpetion() throws Exception {
        GridSet goodGridSet = getGoodGridSet("test", 2);
        exception.expect(NoSuchElementException.class);// Inconsistent with other methods
        config.modifyGridSet(goodGridSet);
    }
    
    @Test
    public void testModifyNotExistsNoChange() throws Exception {
        GridSet goodGridSet = getGoodGridSet("GridSetThatDoesntExist", 2);
        try {
            config.modifyGridSet(goodGridSet);
        } catch(NoSuchElementException ex) {// Inconsistent with other methods.
            
        }
        GridSet retrieved = config.getGridSet("GridSetThatDoesntExist");
        assertThat(retrieved, nullValue()); // Possibly should be exception instead?
    }
    
    @Test
    public void testGetExisting() throws Exception {
        GridSet retrieved = config.getGridSet(getExistingGridSet());
        assertThat(retrieved, notNullValue());
    }
    
    /**
     * Create a GridSet that should be saveable in the configuration being tested. Throw 
     * AssumptionViolatedException if this is a read only GridSetConfiguration.
     * @param id ID for the GridSet
     * @param rand GridSets created with different values should not be equal to one another.
     * @return
     */
    protected abstract GridSet getGoodGridSet(String id, int rand) throws Exception;
    
    /**
     * Create a GridSet that should not be saveable in the configuration being tested. Throw 
     * AssumptionViolatedException if this is a read only GridSetConfiguration.
     * @param id ID for the GridSet
     * @param rand GridSets created with different values should not be equal to one another.
     * @return
     */
    protected abstract GridSet getBadGridSet(String id, int rand) throws Exception;
    
    /**
     * Get an ID for a pre-existing GridSet. Throw AssumptionViolatedException if this this
     * configuration does not have existing GridSets.
     * @return
     */
    protected abstract String getExistingGridSet() throws Exception;

    /**
     * Create a GridSetConfiguration to test.  Subsequent calls should create new configurations using the
     * same persistence or throw AssumptionViolatedException if this is a non-persistent 
     * configuration.
     * @return
     * @throws Exception 
     */
    protected abstract GridSetConfiguration getConfig() throws Exception;
    
    /**
     * Check that two GridSets created by calls to getGoodGridSet, which may have been persisted and 
     * depersisted, are equal if and only if they had the same rand value.
     * @param expected
     * @return
     */
    protected abstract Matcher<GridSet> GridSetEquals(final GridSet expected);
}
