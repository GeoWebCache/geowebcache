/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache.config.wms;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.geowebcache.util.TestUtils.assertPresent;
import static org.geowebcache.util.TestUtils.requirePresent;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.google.common.base.Objects;
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
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.GridSetConfigurationTest;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.util.URLs;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class GetCapabilitiesGridSetConfigurationConformanceTest extends GridSetConfigurationTest {

    private GridSetBroker broker;

    @Before
    public void setupBroker() {
        if (broker == null) {
            broker =
                    new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
        }
    }

    @Override
    protected void doModifyInfo(GridSet info, int rand) throws Exception {
        info.setDescription(Integer.toString(rand));
    }

    @Override
    protected String getExistingInfo() {
        return "testExisting:EPSG:3978";
    }

    @Override
    protected GridSetConfiguration getConfig() throws Exception {
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
        l.setBoundingBoxes(new CRSEnvelope("EPSG:3978", -2259049, 347711, -1994160, 827919));
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet())
                .andStubReturn(
                        URLs.of(
                                "http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));
        expect(cap.getVersion()).andStubReturn("1.1.1");
        EasyMock.replay(server, cap, req, gcOpType, globalConfig);

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(
                        broker, "http://test/wms", "image/png", "3x3", "", null, "false") {

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
    protected GridSetConfiguration getSecondConfig() throws Exception {
        Assume.assumeTrue("This configuration does not have persistance", false);
        return null;
    }

    @Test
    public void testLayerGridsets() throws Exception {
        TileLayer layer =
                requirePresent(((GetCapabilitiesConfiguration) config).getLayer("testExisting"));
        GridSet gridset = assertPresent(config.getGridSet("testExisting:EPSG:3978"));

        GridSubset gridSubset = layer.getGridSubset("testExisting:EPSG:3978");
        GridSet gridSet2 = gridSubset.getGridSet();
        assertThat(gridSet2, equalTo(gridset));
    }

    @Override
    protected Matcher<GridSet> infoEquals(GridSet expected) {
        return new CustomMatcher<GridSet>(
                "GridSet matching " + expected.getName() + " with " + expected.getDescription()) {

            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet
                        && ((GridSet) item).getName().equals(expected.getName())
                        && ((GridSet) item).getDescription().equals(expected.getDescription());
            }
        };
    }

    @Override
    protected Matcher<GridSet> infoEquals(int expected) {
        return new CustomMatcher<GridSet>("GridSet with value " + expected) {

            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet
                        && Objects.equal(
                                ((GridSet) item).getDescription(), Integer.toString(expected));
            }
        };
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
    protected void renameInfo(GridSetConfiguration config, String name1, String name2)
            throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void addInfo(GridSetConfiguration config, GridSet info) throws Exception {
        // TODO Auto-generated method stub
        Assume.assumeFalse(true);
    }

    @Override
    protected void removeInfo(GridSetConfiguration config, String name) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void modifyInfo(GridSetConfiguration config, GridSet info) throws Exception {
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
    public void testAddBadInfoException() throws Exception {
        super.testAddBadInfoException();
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
