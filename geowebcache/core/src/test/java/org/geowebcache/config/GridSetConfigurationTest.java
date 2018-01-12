package org.geowebcache.config;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.*;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
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
    protected Optional<GridSet> getInfo(GridSetConfiguration config, String name) throws Exception {
        return config.getGridSet(name);
    }


    @Override
    protected Collection<? extends GridSet> getInfos(GridSetConfiguration config) throws Exception {
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


    @Override
    protected GridSet getGoodInfo(String id, int rand) {
        GridSet gridset = GridSetFactory.createGridSet(id, SRS.getSRS(4326), new BoundingBox(0,0,1,1), true, 3, 1.0, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, false);
        gridset.setDescription(Integer.toString(rand));
        return gridset;
    }


    @Override
    protected GridSet getBadInfo(String id, int rand) {
        return new GridSet() {};
    }


    @Override
    protected void doModifyInfo(GridSet info, int rand) throws Exception {
        info.setDescription(Integer.toString(rand));
    }
    
}
