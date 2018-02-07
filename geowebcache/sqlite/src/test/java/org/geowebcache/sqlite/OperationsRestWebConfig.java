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
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.DefaultingConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
@ComponentScan({"org.geowebcache.sqlite"})
@EnableWebMvc
@Profile("test")
public class OperationsRestWebConfig extends WebMvcConfigurationSupport {

    static File ROOT_DIRECTORY;

    static {
        try {
            ROOT_DIRECTORY = Files.createTempDirectory("gwc-").toFile();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error creating temporary directory.");
        }
    }

    @Bean
    public MultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        return multipartResolver;
    }

    @Bean
    public TileLayerDispatcher tileLayerDispatcher() throws GeoWebCacheException {
        TileLayerDispatcher tileLayerDispatcher = mock(TileLayerDispatcher.class);
        TileLayer tileLayer = mock(TileLayer.class);
        when(tileLayer.getBlobStoreId()).thenReturn("mbtiles-store");
        when(tileLayerDispatcher.getTileLayer("europe")).thenReturn(tileLayer);
        return tileLayerDispatcher;
    }

    @Bean
    public XMLConfiguration xmlConfiguration() {
        XMLConfiguration gwcConfiguration = mock(XMLConfiguration.class);
        List<BlobStoreInfo> mbtilesStoreConfig = new ArrayList<>();
        MbtilesInfo configuration = new MbtilesInfo("mbtiles-store");
        configuration.setRootDirectory(ROOT_DIRECTORY.getPath());
        mbtilesStoreConfig.add(configuration);
        when(gwcConfiguration.getBlobStores()).thenReturn(mbtilesStoreConfig);
        return gwcConfiguration;
    }
}
