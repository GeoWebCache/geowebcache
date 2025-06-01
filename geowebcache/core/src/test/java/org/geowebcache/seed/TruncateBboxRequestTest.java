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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.seed;

import static org.easymock.EasyMock.eq;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.StorageBroker;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.Test;

public class TruncateBboxRequestTest {

    protected SeedRequest seedRequest(
            String layerName,
            String gridSet,
            String format,
            int minZ,
            int maxZ,
            BoundingBox bounds,
            Map<String, String> parameters) {
        Matcher<Object> matcher = allOf(
                hasProperty("layerName", equalTo(layerName)),
                hasProperty("gridSetId", equalTo(gridSet)),
                hasProperty("mimeFormat", equalTo(format)),
                hasProperty("zoomStart", equalTo(minZ)),
                hasProperty("zoomStop", equalTo(maxZ)),
                hasProperty("parameters", equalTo(parameters)),
                hasProperty("filterUpdate", any(Boolean.class)),
                hasProperty("threadCount", any(Integer.class)),
                hasProperty("bounds", equalTo(bounds)),
                hasProperty("type", any(GWCTask.TYPE.class)));
        EasyMock.reportMatcher(new IArgumentMatcher() {

            @Override
            public boolean matches(Object argument) {
                return matcher.matches(argument);
            }

            @Override
            public void appendTo(StringBuffer buffer) {
                matcher.describeTo(new StringDescription(buffer));
            }
        });
        return null;
    }

    @Test
    public void testDoTruncate() throws Exception {
        String layerName = "testLayer";
        BoundingBox bbox = new BoundingBox(0.0, 1.0, 10.0, 11.0);
        String gridSetName = "testGridset";
        TruncateBboxRequest request = new TruncateBboxRequest(layerName, bbox, gridSetName);

        StorageBroker broker = EasyMock.createMock("broker", StorageBroker.class);
        TileBreeder breeder = EasyMock.createMock("breeder", TileBreeder.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subSet = EasyMock.createMock("subSet", GridSubset.class);

        GWCTask pngStyle1 = EasyMock.createMock("pngStyle1", GWCTask.class);
        GWCTask pngStyle2 = EasyMock.createMock("pngStyle2", GWCTask.class);
        GWCTask jpegStyle1 = EasyMock.createMock("jpegStyle1", GWCTask.class);
        GWCTask jpegStyle2 = EasyMock.createMock("jpegStyle2", GWCTask.class);

        final Set<Map<String, String>> allParams = new HashSet<>();
        allParams.add(Collections.singletonMap("STYLES", "style1"));
        allParams.add(Collections.singletonMap("STYLES", "style2"));

        final long[][] coverages = {{0, 0, 0, 0, 0}, {0, 0, 1, 1, 1}, {0, 0, 4, 4, 2}};
        final int[] metaFactors = {1, 1};

        // Boring mocks
        EasyMock.expect(broker.getCachedParameters(layerName)).andStubReturn(Collections.unmodifiableSet(allParams));
        EasyMock.expect(breeder.findTileLayer(layerName)).andStubReturn(layer);
        EasyMock.expect(layer.getGridSubset(gridSetName)).andStubReturn(subSet);
        EasyMock.expect(layer.getMimeTypes()).andStubReturn(Arrays.asList(ImageMime.png, ImageMime.jpeg));
        EasyMock.expect(subSet.getMinCachedZoom()).andStubReturn(0);
        EasyMock.expect(subSet.getMaxCachedZoom()).andStubReturn(2);
        EasyMock.expect(subSet.getZoomStart()).andStubReturn(0);
        EasyMock.expect(subSet.getZoomStop()).andStubReturn(2);
        EasyMock.expect(subSet.getCoverageIntersections(bbox)).andStubReturn(coverages);
        EasyMock.expect(layer.getMetaTilingFactors()).andStubReturn(metaFactors);
        EasyMock.expect(subSet.expandToMetaFactors(coverages, metaFactors)).andStubReturn(coverages);
        EasyMock.expect(layer.getName()).andStubReturn(layerName);

        // Should issue seed requests for Cartesian product of formats and parameters

        breeder.seed(
                eq(layerName),
                seedRequest(
                        layerName, gridSetName, "image/png", 0, 2, bbox, Collections.singletonMap("STYLES", "style1")));
        EasyMock.expectLastCall().once();
        breeder.seed(
                eq(layerName),
                seedRequest(
                        layerName, gridSetName, "image/png", 0, 2, bbox, Collections.singletonMap("STYLES", "style2")));
        EasyMock.expectLastCall().once();
        breeder.seed(eq(layerName), seedRequest(layerName, gridSetName, "image/png", 0, 2, bbox, null)); // Default
        EasyMock.expectLastCall().once();
        breeder.seed(
                eq(layerName),
                seedRequest(
                        layerName,
                        gridSetName,
                        "image/jpeg",
                        0,
                        2,
                        bbox,
                        Collections.singletonMap("STYLES", "style1")));
        EasyMock.expectLastCall().once();
        breeder.seed(
                eq(layerName),
                seedRequest(
                        layerName,
                        gridSetName,
                        "image/jpeg",
                        0,
                        2,
                        bbox,
                        Collections.singletonMap("STYLES", "style2")));
        EasyMock.expectLastCall().once();
        breeder.seed(eq(layerName), seedRequest(layerName, gridSetName, "image/jpeg", 0, 2, bbox, null)); // Default
        EasyMock.expectLastCall().once();

        EasyMock.replay(broker, breeder, layer, subSet);
        EasyMock.replay(pngStyle1, pngStyle2, jpegStyle1, jpegStyle2);

        assertThat(request.doTruncate(broker, breeder), is(true));

        EasyMock.verify(broker, breeder, layer, subSet);
    }
}
