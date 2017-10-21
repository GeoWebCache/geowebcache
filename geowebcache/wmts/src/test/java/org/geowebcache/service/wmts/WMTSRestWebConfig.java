/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.service.wmts;

import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.config.legends.LegendInfo;
import org.geowebcache.config.legends.LegendInfoBuilder;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
@ComponentScan({"org.geowebcache.service.wmts"})
@EnableWebMvc
@Profile("test")
class WMTSRestWebConfig extends WebMvcConfigurationSupport {

    GridSetBroker broker = new GridSetBroker(true, true);

    @Bean
    public DefaultStorageFinder defaultStorageFinder() {
        return mock(DefaultStorageFinder.class);
    }

    @Bean
    public RuntimeStats runtimeStats() {
        return mock(RuntimeStats.class);
    }

    @Bean
    public TileLayerDispatcher tileLayerDispatcher() throws Exception {
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        List<String> gridSetNames = Arrays.asList("GlobalCRS84Pixel", "GlobalCRS84Scale",
                "EPSG:4326");
        String layerName = "mockLayer";
        TileLayer tileLayer = mock(TileLayer.class);

        StringParameterFilter styles = new StringParameterFilter();
        styles.setKey("STYLES");
        styles.setValues(Arrays.asList("style-a", "style-b"));

        StringParameterFilter time = new StringParameterFilter();
        time.setKey("time");
        time.setValues(Arrays.asList("2016-02-23T03:00:00.000Z"));

        StringParameterFilter elevation = new StringParameterFilter();
        elevation.setKey("elevation");
        elevation.setValues(Arrays.asList("500"));

        when(tileLayer.getParameterFilters()).thenReturn(Arrays.asList(styles, time, elevation));

        LegendInfo legendInfo2 = new LegendInfoBuilder().withStyleName("styla-b-legend")
                .withWidth(125).withHeight(130).withFormat("image/png")
                .withCompleteUrl("https://some-url?some-parameter=value3&another-parameter=value4")
                .withMinScale(5000D).withMaxScale(10000D).build();
        when(tileLayer.getLayerLegendsInfo())
                .thenReturn(Collections.singletonMap("style-b", legendInfo2));

        when(tld.getTileLayer(eq(layerName))).thenReturn(tileLayer);
        when(tileLayer.getName()).thenReturn(layerName);
        when(tileLayer.isEnabled()).thenReturn(true);
        when(tileLayer.isAdvertised()).thenReturn(true);

        final MimeType mimeType1 = MimeType.createFromFormat("image/png");
        final MimeType mimeType2 = MimeType.createFromFormat("image/jpeg");
        when(tileLayer.getMimeTypes()).thenReturn(Arrays.asList(mimeType1, mimeType2));

        final MimeType infoMimeType1 = MimeType.createFromFormat("text/plain");
        final MimeType infoMimeType2 = MimeType.createFromFormat("text/html");
        final MimeType infoMimeType3 = MimeType.createFromFormat("application/vnd.ogc.gml");
        when(tileLayer.getInfoMimeTypes())
                .thenReturn(Arrays.asList(infoMimeType1, infoMimeType2, infoMimeType3));
        Map<String, GridSubset> subsets = new HashMap<String, GridSubset>();
        Map<SRS, List<GridSubset>> bySrs = new HashMap<SRS, List<GridSubset>>();

        for (String gsetName : gridSetNames) {
            GridSet gridSet = broker.get(gsetName);
            XMLGridSubset xmlGridSubset = new XMLGridSubset();
            String gridSetName = gridSet.getName();
            xmlGridSubset.setGridSetName(gridSetName);
            GridSubset gridSubSet = xmlGridSubset.getGridSubSet(broker);
            subsets.put(gsetName, gridSubSet);

            List<GridSubset> list = bySrs.get(gridSet.getSrs());
            if (list == null) {
                list = new ArrayList<GridSubset>();
                bySrs.put(gridSet.getSrs(), list);
            }
            list.add(gridSubSet);

            when(tileLayer.getGridSubset(eq(gsetName))).thenReturn(gridSubSet);

        }

        for (SRS srs : bySrs.keySet()) {
            List<GridSubset> list = bySrs.get(srs);
            when(tileLayer.getGridSubsetsForSRS(eq(srs))).thenReturn(list);

        }
        when(tileLayer.getGridSubsets()).thenReturn(subsets.keySet());

        when(tld.getLayerList()).thenReturn(Arrays.asList(tileLayer));

        when(tileLayer.getTile(any(ConveyorTile.class))).thenAnswer(new Answer<ConveyorTile>() {
            @Override
            public ConveyorTile answer(InvocationOnMock invocation) throws Throwable {
                ConveyorTile sourceTile = (ConveyorTile) invocation.getArguments()[0];
                final File imgPath = new File(getClass().getResource("/image.png").toURI());
                BufferedImage originalImage = ImageIO.read(imgPath);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(originalImage, "png", baos);
                sourceTile.setBlob(new ByteArrayResource(baos.toByteArray()));
                return sourceTile;
            }
        });

        when(tileLayer.getFeatureInfo(any(ConveyorTile.class), any(BoundingBox.class), anyInt(),
                anyInt(), anyInt(), anyInt())).thenAnswer(new Answer<Resource>() {
            @Override
            public Resource answer(InvocationOnMock invocation) throws Throwable {
                return new ByteArrayResource(new byte[0]);
            }
        });

        return tld;
    }

    @Bean
    public GeoWebCacheExtensions geoWebCacheExtensions() {
        return new GeoWebCacheExtensions();
    }

    @Bean
    public WMTSService wmtsService() throws Exception {
        WMTSService wmtsService = new WMTSService(mock(StorageBroker.class), tileLayerDispatcher(), broker,
                mock(RuntimeStats.class));
        wmtsService.setSecurityDispatcher(securityDispatcher());
        return wmtsService;
    }

    @Bean
    public SecurityDispatcher securityDispatcher() {
        SecurityDispatcher secDisp = mock(SecurityDispatcher.class);
        when(secDisp.isSecurityEnabled()).thenReturn(false);
        return secDisp;
    }

    @Bean
    public PropertyPlaceholderConfigurer configurationPlaceholders() {
        // necessary for spring configuration placeholders
        return new PropertyPlaceholderConfigurer();
    }
}
