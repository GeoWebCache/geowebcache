package org.geowebcache.config;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Set;

import static org.hamcrest.Matchers.*;

import org.geowebcache.grid.GridSet;
import org.junit.Test;


/**
 * 
 * Abstract unit test for GridSet Configurations.  Checks that behavior is consistent across 
 * implementations.
 *
 */
public abstract class GridSetConfigurationTest extends ConfigurationTest<GridSet, GridSetConfiguration> {
    
    @Test
    public void testCanSaveGoodInfo() throws Exception {
        assertThat(config.canSave(getGoodInfo("test", 1)), equalTo(true));
    }
    
    
    @Test
    public void testCanSaveBadInfo() throws Exception {
        assertThat(config.canSave(getBadInfo("test", 1)), equalTo(false));
    }


    @Override
    protected void addInfo(GridSetConfiguration config, GridSet info) throws Exception {
        config.addGridSet(info);
    }


    @Override
    protected GridSet getInfo(GridSetConfiguration config, String name) throws Exception {
        return config.getGridSet(name);
    }


    @Override
    protected Collection<GridSet> getInfos(GridSetConfiguration config) throws Exception {
        return config.getGridSets();
    }


    @Override
    protected Set<String> getInfoNames(GridSetConfiguration config) throws Exception {
        return config.getGridSetNames();
    }


    @Override
    protected void removeInfo(GridSetConfiguration config, String name) throws Exception {
        config.removeGridSet(name);
    }


    @Override
    protected void renameInfo(GridSetConfiguration config, String name1, String name2) throws Exception {
        config.renameGridSet(name1, name2);
    }


    @Override
    protected void modifyInfo(GridSetConfiguration config, GridSet info) throws Exception {
        config.modifyGridSet(info);
    }
    
}
