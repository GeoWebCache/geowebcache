package org.geowebcache.config;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Set;

import static org.hamcrest.Matchers.*;

import org.geowebcache.layer.TileLayer;
import org.junit.Test;

public abstract class LayerConfigurationTest extends ConfigurationTest<TileLayer, TileLayerConfiguration>{
    
    @Test
    public void testCanSaveGoodInfo() throws Exception {
        assertThat(config.canSave(getGoodInfo("test", 1)), equalTo(true));
    }
    
    
    @Test
    public void testCanSaveBadInfo() throws Exception {
        assertThat(config.canSave(getBadInfo("test", 1)), equalTo(false));
    }


    @Override
    protected void addInfo(TileLayerConfiguration config, TileLayer info) throws Exception {
        config.addLayer(info);
    }


    @Override
    protected TileLayer getInfo(TileLayerConfiguration config, String name) throws Exception {
        return config.getLayer(name);
    }


    @Override
    protected Collection<? extends TileLayer> getInfos(TileLayerConfiguration config) throws Exception {
        return config.getLayers();
    }


    @Override
    protected Set<String> getInfoNames(TileLayerConfiguration config) throws Exception {
        return config.getLayerNames();
    }


    @Override
    protected void removeInfo(TileLayerConfiguration config, String name) throws Exception {
        config.removeLayer(name);
    }


    @Override
    protected void renameInfo(TileLayerConfiguration config, String name1, String name2) throws Exception {
        config.renameLayer(name1, name2);
    }


    @Override
    protected void modifyInfo(TileLayerConfiguration config, TileLayer info) throws Exception {
        config.modifyLayer(info);
    }
 
}
