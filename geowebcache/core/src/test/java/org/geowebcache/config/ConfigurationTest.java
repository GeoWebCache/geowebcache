package org.geowebcache.config;

import static org.geowebcache.util.TestUtils.isPresent;
import static org.geowebcache.util.TestUtils.notPresent;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import org.geowebcache.util.TestUtils;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class ConfigurationTest<I extends Info, C extends BaseConfiguration> {
    
    C config;
    
    @Rule 
    public ExpectedException exception = ExpectedException.none();
    
    @Before
    public void setUpTestUnit() throws Exception {
        config = getConfig();
    }
    
    @After 
    public void assertNameSetMatchesCollection() throws Exception {
        assertNameSetMatchesCollection(config);
    }
    
    @Test
    public void testAdd() throws Exception {
        I goodGridSet = getGoodInfo("test", 1);
        addInfo(config, goodGridSet);
        I retrieved = getInfo(config, "test").get();
        assertThat(retrieved, infoEquals(goodGridSet));
    }
    
    @Test
    public void testPersistAdd() throws Exception {
        I goodGridSet = getGoodInfo("test", 1);
        addInfo(config, goodGridSet);

        C config2 = getConfig();
        I retrieved = getInfo(config2, "test").get();
        assertThat(retrieved, infoEquals(goodGridSet));
        assertNameSetMatchesCollection(config2);
    }
    
    @Test
    public void testDoubleAddException() throws Exception {
        I goodGridSet = getGoodInfo("test", 1);
        I doubleGridSet = getGoodInfo("test", 2);
        assertThat("Invalid test", goodGridSet, not(infoEquals(doubleGridSet)));
        addInfo(config, goodGridSet);
        exception.expect(instanceOf(IllegalArgumentException.class)); // May want to change to something more specific.
        addInfo(config, doubleGridSet);
    }
    
    @Test
    public void testDoubleAddNoChange() throws Exception {
        I goodGridSet = getGoodInfo("test", 1);
        I doubleGridSet = getGoodInfo("test", 2);
        assertThat("Invalid test", goodGridSet, not(infoEquals(doubleGridSet)));
        addInfo(config, goodGridSet);
        try {
            addInfo(config, doubleGridSet);
        } catch (IllegalArgumentException ex) { // May want to change to something more specific.
            
        }
        I retrieved = getInfo(config, "test").get();
        assertThat(retrieved, infoEquals(goodGridSet));
    }
    
    @Test
    public void testAddBadInfoException() throws Exception {
        I badGridSet = getBadInfo("test", 1);
        exception.expect(IllegalArgumentException.class);// May want to change to something more specific.
        addInfo(config, badGridSet);
    }
    
    @Test
    public void testBadInfoNoAdd() throws Exception {
        I badInfo = getBadInfo("test", 1);
        try {
            addInfo(config, badInfo);
        } catch (IllegalArgumentException ex) { // May want to change to something more specific.
            
        }
        Optional<I> retrieved = getInfo(config, "test");
        assertThat(retrieved, TestUtils.notPresent());
    }

    @Test
    public void testRemove() throws Exception {
        testAdd();
        removeInfo(config, "test");
        Optional<I> retrieved = getInfo(config, "test");
        assertThat(retrieved, TestUtils.notPresent());
    }
    
    @Test
    public void testPersistRemove() throws Exception {
        testPersistAdd();
        
        removeInfo(config, "test");

        C config2 = getConfig();
        Optional<I> retrieved = getInfo(config2, "test");
        assertThat(retrieved, notPresent());
        assertNameSetMatchesCollection(config2);
    }
    
    @Test
    public void testGetNotExists() throws Exception {
        
        @SuppressWarnings("unused")
        Optional<I> retrieved = getInfo(config, "GridSetThatDoesntExist");
    }
    
    @Test
    public void testRemoveNotExists() throws Exception {
        exception.expect(NoSuchElementException.class);
        removeInfo(config, "GridSetThatDoesntExist");
    }
    @Test
    public void testModify() throws Exception {
        testAdd();
        I goodGridSet = getGoodInfo("test", 2);
        modifyInfo(config, goodGridSet);
        
        Optional<I> retrieved = getInfo(config, "test");
        assertThat(retrieved, isPresent(infoEquals(goodGridSet)));
    }

    @Test
    public void testModifyBadGridSetException() throws Exception {
        testAdd();
        I badGridSet = getBadInfo("test", 2);
        
        exception.expect(IllegalArgumentException.class); // Could be more specific
        
        modifyInfo(config, badGridSet);
    }

    @Test
    public void testModifyBadGridSetNoChange() throws Exception {
        testAdd();
        I goodGridSet = getInfo(config, "test").get();
        I badGridSet = getBadInfo("test", 2);
        
        try {
            modifyInfo(config, badGridSet);
        } catch (IllegalArgumentException ex) { // Could be more specific
            
        }
        
        Optional<I> retrieved = getInfo(config, "test");
        assertThat(retrieved, isPresent(infoEquals(goodGridSet)));
    }
    
    @Test
    public void testPersistModify() throws Exception {
        testPersistAdd();
        
        I goodGridSet = getGoodInfo("test", 2);
        modifyInfo(config, goodGridSet);

        C config2 = getConfig();
        Optional<I> retrieved = getInfo(config2, "test");
        assertThat(retrieved, isPresent(infoEquals(goodGridSet)));
        assertNameSetMatchesCollection(config2);
    }
    
    @Test
    public void testModifyNotExistsExcpetion() throws Exception {
        I goodGridSet = getGoodInfo("test", 2);
        exception.expect(NoSuchElementException.class);
        modifyInfo(config, goodGridSet);
    }
    
    @Test
    public void testModifyNotExistsNoChange() throws Exception {
        I goodGridSet = getGoodInfo("GridSetThatDoesntExist", 2);
        try {
            modifyInfo(config, goodGridSet);
        } catch(NoSuchElementException ex) {
            
        }
        Optional<I> retrieved = getInfo(config, "GridSetThatDoesntExist");
        assertThat(retrieved, notPresent());
    }
    
    @Test
    public void testGetExisting() throws Exception {
        Optional<I> retrieved = getInfo(config, getExistingInfo());
        assertThat(retrieved, isPresent());
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCantModifyReturnedCollection() throws Exception {
        I info = getGoodInfo("test", 1);
        try {
            ((Collection)getInfos(config)).add(info);
        } catch (Exception e) {
            
        }
        assertThat(getInfo(config, "test"), notPresent());
    }

    @Test
    public void testModifyCallRequiredToChangeInfoFromGetInfo() throws Exception {
        testAdd();
        I goodGridSet = getInfo(config, "test").get();
        doModifyInfo(goodGridSet, 2);
        
        Optional<I> retrieved = getInfo(config, "test");
        assertThat(retrieved, isPresent(infoEquals(getGoodInfo("test", 1))));
        assertThat(retrieved, isPresent(not(infoEquals(getGoodInfo("test", 2)))));
    }
    
    @Test
    public void testModifyCallRequiredToChangeInfoFromGetInfos() throws Exception {
        testAdd();
        I goodGridSet = getInfos(config).stream()
                .filter(i->i.getName().equals("test"))
                .findAny()
                .get();
        doModifyInfo(goodGridSet, 2);
        
        Optional<I> retrieved = getInfo(config, "test");
        assertThat(retrieved, isPresent(infoEquals(getGoodInfo("test", 1))));
        assertThat(retrieved, isPresent(not(infoEquals(getGoodInfo("test", 2)))));
    }
    
    /**
     * Modify an existing info object.
     * @param info
     * @param rand
     * @throws Exception
     */
    protected abstract void doModifyInfo(I info, int rand) throws Exception;
    
    @Test
    public void testNoChangeOnPersistExceptionOnAdd() throws Exception {
        I goodGridSet = getGoodInfo("test", 1);
        
        // Force a failure
        failNextWrite();
        exception.expect(ConfigurationPersistenceException.class);
        
        try {
            addInfo(config, goodGridSet);
        } finally {
            // Should be unchanged
            Optional<I> retrieved = getInfo(config, "test");
            assertThat(retrieved, notPresent());
            
            // Persistence should also be unchanged
            C config2 = getConfig();
            Optional<I> retrieved2 = getInfo(config2, "test");
            assertThat(retrieved2, notPresent());
            assertNameSetMatchesCollection(config2);
        }
    }
    
    @Test
    public void testRename() throws Exception {
        testAdd();
        renameInfo(config, "test", "test2");
        
        Optional<I> retrieved = getInfo(config, "test2");
        assertThat(retrieved, isPresent());
        Optional<I> retrievedOld = getInfo(config, "test");
        assertThat(retrievedOld, notPresent());
    }

    @Test
    public void testConcurrentAdds() throws Exception {
        // get the initial number of default configured Info elements
        final int defaultCount = getInfoNames(config).size();
        // add a bunch of Infos
        for (int i=0; i< 10; ++i) {
            I goodInfo = this.getGoodInfo(Integer.toString(i), i);
            this.addInfo(config, goodInfo);
        }
        // ensure all 10 got added to the initial info
        assertEquals("Unexpected number of Info config elements", defaultCount + 10, getInfoNames(config).size());
        // get a thread pool
        ExecutorService pool = Executors.newFixedThreadPool(16,
            (Runnable r) -> new Thread(r, "Info Concurrency Test for ADD"));
        // create a bunch of concurrent adds
        ArrayList<Future<I>> futures = new ArrayList<>(100);
        for (int i=0; i<100; ++i) {
            // create a new info
            int id = 100 + i;
            I info = getGoodInfo(Integer.toString(id), id);
            // schedule the add
            Future<I> future = pool.submit(() -> {
                addInfo(config, info);
                return info;
            });
            futures.add(future);
        }
        // get the results
        for (Future<I> f : futures) {
            f.get();
        }
        // ensure the 110 added are in the list
        assertEquals("Unexpected number of Info config elements", defaultCount + 110,
            getInfoNames(config).size());
    }

    @Test
    public void testConcurrentDeletes() throws Exception {
        // get the initial number of default configured Info elements
        final int defaultCount = getInfoNames(config).size();
        // add a bunch of Infos
        for (int i=0; i< 100; ++i) {
            I goodInfo = this.getGoodInfo(Integer.toString(i), i);
            this.addInfo(config, goodInfo);
        }
        // ensure all 100 got added to the initial info
        assertEquals("Unexpected number of Info config elements", defaultCount + 100,
            getInfoNames(config).size());
        // get a thread pool
        ExecutorService pool = Executors.newFixedThreadPool(16,
            (Runnable r) -> new Thread(r, "Info Concurrency Test for DELETE"));
        // create a bunch of concurrent deletes
        ArrayList<Future<String>> futures = new ArrayList<>(100);
        for (int i=0; i<100; ++i) {
            // schedule the delete
            final String name = Integer.toString(i);
            Future<String> future = pool.submit(() -> {
                removeInfo(config, name);
                return name;
            });
            futures.add(future);
        }
        // get the results
        for (Future<String> f : futures) {
            f.get();
        }
        // ensure only the default configured Info elements are left
        assertEquals("Unexpected number of Info config elements", defaultCount, getInfoNames(config).size());
    }

    @Test
    public void testConcurrentModifies() throws Exception {
        // get the initial number of default configured Info elements
        final int defaultCount = getInfoNames(config).size();
        // add a bunch of Infos
        for (int i=0; i< 100; ++i) {
            I goodInfo = this.getGoodInfo(Integer.toString(i), i);
            this.addInfo(config, goodInfo);
        }
        // ensure all 100 got added to the initial info
        assertEquals("Unexpected number of Info config elements", defaultCount + 100, getInfoNames(config).size());
        // get a thread pool
        ExecutorService pool = Executors.newFixedThreadPool(16,
            (Runnable r) -> new Thread(r, "Info Concurrency Test for MODIFY"));
        // create a bunch of concurrent modifies
        ArrayList<Future<I>> futures = new ArrayList<>(100);
        for (int i=0; i<100; ++i) {
            // create a modified info
            int originalId = i;
            int modifiedId = 200 + i;
            I modifiedInfo = getGoodInfo(Integer.toString(originalId), modifiedId);
            // schedule the add
            Future<I> future = pool.submit(() -> {
                modifyInfo(config, modifiedInfo);
                return modifiedInfo;
            });
            futures.add(future);
        }
        // get the results
        for (Future<I> f : futures) {
            f.get();
        }
        // ensure the 100 added are still in the list
        assertEquals("Unexpected number of Info config elements", defaultCount + 100, getInfoNames(config).size());
    }
    
    /**
     * Create a GridSet that should be saveable in the configuration being tested. Throw 
     * AssumptionViolatedException if this is a read only GridSetConfiguration.
     * @param id ID for the GridSet
     * @param rand GridSets created with different values should not be equal to one another.
     * @return
     */
    protected abstract I getGoodInfo(String id, int rand) throws Exception;
    
    /**
     * Create a GridSet that should not be saveable in the configuration being tested. Throw 
     * AssumptionViolatedException if this is a read only GridSetConfiguration.
     * @param id ID for the GridSet
     * @param rand GridSets created with different values should not be equal to one another.
     * @return
     */
    protected abstract I getBadInfo(String id, int rand) throws Exception;
    
    /**
     * Get an ID for a pre-existing GridSet. Throw AssumptionViolatedException if this this
     * configuration does not have existing GridSets.
     * @return
     */
    protected abstract String getExistingInfo() throws Exception;

    /**
     * Create a GridSetConfiguration to test.  Subsequent calls should create new configurations using the
     * same persistence or throw AssumptionViolatedException if this is a non-persistent 
     * configuration.
     * @return
     * @throws Exception 
     */
    protected abstract C getConfig() throws Exception;
    
    /**
     * Check that two GridSets created by calls to getGoodGridSet, which may have been persisted and 
     * depersisted, are equal if and only if they had the same rand value.
     * @param expected
     * @return
     */
    protected abstract Matcher<I> infoEquals(final I expected);
    
    protected abstract void addInfo(C config, I info) throws Exception;
    protected abstract Optional<I> getInfo(C config, String name) throws Exception;
    protected abstract Collection<? extends I> getInfos(C config) throws Exception;
    protected abstract Set<String> getInfoNames(C config) throws Exception;
    protected abstract void removeInfo(C config, String name) throws Exception;
    protected abstract void renameInfo(C config, String name1, String name2) throws Exception;
    protected abstract void modifyInfo(C config, I info) throws Exception;
    
    public void assertNameSetMatchesCollection(C config) throws Exception {
        Set<String> collectionNames = getInfos(config).stream().map(Info::getName).collect(Collectors.toSet());
        Set<String> setNames = getInfoNames(config);
        assertThat(setNames, equalTo(collectionNames));
    }
    
    public abstract void failNextRead();
    public abstract void failNextWrite();

}
