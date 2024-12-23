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
package org.geowebcache.config.wms;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.easymock.EasyMock;
import org.geotools.data.ows.OperationType;
import org.geotools.ows.wms.CRSEnvelope;
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wms.WMSRequest;
import org.geotools.ows.wms.WebMapServer;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.DefaultingConfiguration;
import org.geowebcache.config.LayerConfigurationTest;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.URLs;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class GetCapabilitiesLayerConfigurationConformanceTest extends LayerConfigurationTest {

    private GridSetBroker broker;

    @Before
    public void setupBroker() {
        if (broker == null) {
            broker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
        }
    }

    @Override
    protected void doModifyInfo(TileLayer info, int rand) throws Exception {
        ((WMSLayer) info).setWmsLayers(Integer.toString(rand));
    }

    @Override
    protected TileLayer getGoodInfo(String id, int rand) throws Exception {
        return new WMSLayer(
                id,
                new String[] {"http://foo"},
                "style",
                Integer.toString(rand),
                Collections.emptyList(),
                Collections.singletonMap("EPSG:4326", GridSubsetFactory.createGridSubSet(broker.getWorldEpsg4326())),
                Collections.emptyList(),
                new int[] {3, 3},
                "",
                false,
                null);
    }
    /*(String layerName, String[] wmsURL, String wmsStyles, String wmsLayers,
    List<String> mimeFormats, Map<String, GridSubset> subSets,
    List<ParameterFilter> parameterFilters, int[] metaWidthHeight, String vendorParams,
    boolean queryable, String wmsQueryLayers)*/

    @Override
    protected TileLayer getBadInfo(String id, int rand) throws Exception {
        Assume.assumeFalse(true);
        return null;
    }

    @Override
    protected String getExistingInfo() {
        return "testExisting";
    }

    @Override
    protected TileLayerConfiguration getConfig() throws Exception {
        WebMapServer server;
        WMSCapabilities cap;
        WMSRequest req;
        OperationType gcOpType;
        DefaultingConfiguration globalConfig;
        server = createNiceMock(WebMapServer.class);
        cap = createNiceMock(WMSCapabilities.class);
        req = createNiceMock(WMSRequest.class);
        gcOpType = createNiceMock(OperationType.class);
        globalConfig = createNiceMock(DefaultingConfiguration.class);
        setupBroker();

        Layer l = new Layer();
        l.setName("testExisting");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet())
                .andStubReturn(URLs.of("http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));
        expect(cap.getVersion()).andStubReturn("1.1.1");
        EasyMock.replay(server, cap, req, gcOpType, globalConfig);

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(broker, "http://test/wms", "image/png", "3x3", "", null, "false") {

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }
                };
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();

        return config;
    }

    @Override
    protected TileLayerConfiguration getSecondConfig() throws Exception {
        Assume.assumeTrue("This configuration does not have persistance", false);
        return null;
    }

    @Override
    protected Matcher<TileLayer> infoEquals(TileLayer expected) {
        return Matchers.allOf(
                Matchers.hasProperty("name", equalTo(expected.getName())),
                Matchers.hasProperty("wmsLayers", equalTo(((WMSLayer) expected).getWmsLayers())));
    }

    @Override
    protected Matcher<TileLayer> infoEquals(int expected) {
        return Matchers.hasProperty("wmsLayers", equalTo(expected));
    }

    @Override
    public void failNextRead() {
        Assume.assumeFalse(true);
    }

    @Override
    public void failNextWrite() {
        Assume.assumeFalse(true);
    }

    @Override
    protected void renameInfo(TileLayerConfiguration config, String name1, String name2) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void addInfo(TileLayerConfiguration config, TileLayer info) throws Exception {
        // TODO Auto-generated method stub
        Assume.assumeFalse(true);
    }

    @Override
    protected void removeInfo(TileLayerConfiguration config, String name) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void modifyInfo(TileLayerConfiguration config, TileLayer info) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    @Test
    public void testCanSaveGoodInfo() throws Exception {
        // Should not be able to save anything as it is read only
        assertThat(config.canSave(getGoodInfo("test", 1)), equalTo(false));
    }

    @Test
    @Ignore
    @Override
    public void testRemoveNotExists() throws Exception {
        super.testRemoveNotExists();
    }

    @Test
    @Ignore
    @Override
    public void testModifyNotExistsExcpetion() throws Exception {
        super.testModifyNotExistsExcpetion();
    }
}
