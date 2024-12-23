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
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.service.wmts;

import static org.mockito.Mockito.mock;

import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
@ComponentScan({"org.geowebcache.service.wmts"})
@EnableWebMvc
@Profile("test")
class WMTSRestWebConfig extends WebMvcConfigurationSupport {

    @Bean
    public DefaultStorageFinder defaultStorageFinder() {
        return mock(DefaultStorageFinder.class);
    }

    @Bean
    public RuntimeStats runtimeStats() {
        return mock(RuntimeStats.class);
    }
}
