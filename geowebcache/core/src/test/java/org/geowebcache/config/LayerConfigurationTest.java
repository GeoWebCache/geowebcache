/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.geowebcache.layer.TileLayer;
import org.junit.Test;

public abstract class LayerConfigurationTest extends ConfigurationTest<TileLayer, TileLayerConfiguration> {

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
    protected Optional<TileLayer> getInfo(TileLayerConfiguration config, String name) throws Exception {
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
