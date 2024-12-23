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
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.config.legends;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

/** Test styles legends info different combinations. */
public class LegendInfoBuilderTest {

    @Test
    public void testWithOnlyDefaults() {
        LegendInfo legendInfo = new LegendInfoBuilder()
                .withLayerName("layer1")
                .withLayerUrl("http://localhost:8080/geoserver")
                .withDefaultWidth(50)
                .withDefaultHeight(100)
                .withDefaultFormat("image/png")
                .build();
        assertThat(legendInfo.getWidth(), is(50));
        assertThat(legendInfo.getHeight(), is(100));
        assertThat(legendInfo.getFormat(), is("image/png"));
        assertThat(legendInfo.getStyleName(), is(""));
        assertThat(legendInfo.getMinScale(), is(nullValue()));
        assertThat(legendInfo.getMaxScale(), is(nullValue()));
        assertThat(
                legendInfo.getLegendUrl(),
                is(
                        "http://localhost:8080/geoserver?"
                                + "service=WMS&request=GetLegendGraphic&format=image/png&width=50&height=100&layer=layer1&style="));
    }

    @Test
    public void testWithValues() {
        LegendInfo legendInfo = new LegendInfoBuilder()
                .withLayerName("layer1")
                .withLayerUrl("http://localhost:8080/geoserver")
                .withDefaultWidth(50)
                .withDefaultHeight(100)
                .withDefaultFormat("image/png")
                .withStyleName("style1")
                .withWidth(150)
                .withHeight(200)
                .withFormat("image/gif")
                .withMinScale(1000.55)
                .withMaxScale(2000.655)
                .build();
        assertThat(legendInfo.getWidth(), is(150));
        assertThat(legendInfo.getHeight(), is(200));
        assertThat(legendInfo.getFormat(), is("image/gif"));
        assertThat(legendInfo.getStyleName(), is("style1"));
        assertThat(legendInfo.getMinScale(), is(1000.55));
        assertThat(legendInfo.getMaxScale(), is(2000.655));
        assertThat(
                legendInfo.getLegendUrl(),
                is(
                        "http://localhost:8080/geoserver?"
                                + "service=WMS&request=GetLegendGraphic&format=image/gif&width=150&height=200&layer=layer1&style=style1"));
    }

    @Test
    public void testWithUrl() {
        LegendInfo legendInfo = new LegendInfoBuilder()
                .withLayerName("layer1")
                .withLayerUrl("http://localhost:8080/geoserver")
                .withDefaultWidth(50)
                .withDefaultHeight(100)
                .withDefaultFormat("image/png")
                .withStyleName("style1")
                .withWidth(150)
                .withHeight(200)
                .withFormat("image/gif")
                .withUrl("http://localhost:9090/image.gif?")
                .build();
        assertThat(legendInfo.getWidth(), is(150));
        assertThat(legendInfo.getHeight(), is(200));
        assertThat(legendInfo.getFormat(), is("image/gif"));
        assertThat(legendInfo.getStyleName(), is("style1"));
        assertThat(
                legendInfo.getLegendUrl(),
                is(
                        "http://localhost:9090/image.gif?"
                                + "service=WMS&request=GetLegendGraphic&format=image/gif&width=150&height=200&layer=layer1&style=style1"));
    }

    @Test
    public void testWithCompleteUrl() {
        LegendInfo legendInfo = new LegendInfoBuilder()
                .withLayerName("layer1")
                .withLayerUrl("http://localhost:8080/geoserver")
                .withDefaultWidth(50)
                .withDefaultHeight(100)
                .withDefaultFormat("image/png")
                .withStyleName("style1")
                .withWidth(150)
                .withHeight(200)
                .withFormat("image/gif")
                .withCompleteUrl("http://my.server.com/image.gif")
                .build();
        assertThat(legendInfo.getWidth(), is(150));
        assertThat(legendInfo.getHeight(), is(200));
        assertThat(legendInfo.getFormat(), is("image/gif"));
        assertThat(legendInfo.getStyleName(), is("style1"));
        assertThat(legendInfo.getLegendUrl(), is("http://my.server.com/image.gif"));
    }

    @Test
    public void testWithValuesNoDefaults() {
        LegendInfo legendInfo = new LegendInfoBuilder()
                .withLayerName("layer1")
                .withLayerUrl("http://localhost:8080/geoserver")
                .withStyleName("style1")
                .withWidth(150)
                .withHeight(200)
                .withFormat("image/gif")
                .withMinScale(50.5)
                .withMaxScale(80.5)
                .build();
        assertThat(legendInfo.getWidth(), is(150));
        assertThat(legendInfo.getHeight(), is(200));
        assertThat(legendInfo.getFormat(), is("image/gif"));
        assertThat(legendInfo.getStyleName(), is("style1"));
        assertThat(legendInfo.getMinScale(), is(50.5));
        assertThat(legendInfo.getMaxScale(), is(80.5));
        assertThat(
                legendInfo.getLegendUrl(),
                is(
                        "http://localhost:8080/geoserver?"
                                + "service=WMS&request=GetLegendGraphic&format=image/gif&width=150&height=200&layer=layer1&style=style1"));
    }
}
