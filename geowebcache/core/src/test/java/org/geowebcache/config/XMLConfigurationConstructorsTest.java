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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.PropertyRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.context.WebApplicationContext;

public class XMLConfigurationConstructorsTest {

    public static final String MARKED_CONFIG_FILE_NAME = "geowebcache-test-correct-config.xml";
    public static final String MARKED_LAYER = "LOADED_CORRECT_CONFIG_FILE";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public PropertyRule gwcConfigDirProp = PropertyRule.system(XMLFileResourceProvider.GWC_CONFIG_DIR_VAR);

    File configDir;
    File cacheDir;

    ApplicationContextProvider provider;
    WebApplicationContext appContext;
    ServletContext svltContext;
    DefaultStorageFinder storageFinder;
    GridSetBroker broker;

    @Before
    public void setUp() throws Exception {
        configDir = temp.newFolder("config");
        cacheDir = temp.newFolder("cache");

        provider = createMock(ApplicationContextProvider.class);
        appContext = createMock(WebApplicationContext.class);
        svltContext = createMock(ServletContext.class);
        storageFinder = createMock(DefaultStorageFinder.class);
        broker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

        expect(provider.getApplicationContext()).andStubReturn(appContext);
        expect(appContext.getServletContext()).andStubReturn(svltContext);
        expect(svltContext.getInitParameter(anyObject())).andStubReturn(null);
        expect(storageFinder.getDefaultPath()).andStubReturn(cacheDir.getCanonicalPath());
        expect(appContext.getBeansOfType(org.geowebcache.config.XMLConfigurationProvider.class))
                .andReturn(Collections.emptyMap());
        expect(svltContext.getRealPath("")).andStubReturn(temp.getRoot().getCanonicalPath());

        replay(provider, appContext, svltContext, storageFinder);
    }

    @After
    public void tearDown() throws Exception {
        verify(provider, appContext, svltContext, storageFinder);
    }

    public void shouldLoadFrom(File dir) throws IOException {
        File configFile = configFile(dir);
        URL source = XMLConfigurationConstructorsTest.class.getResource(MARKED_CONFIG_FILE_NAME);
        FileUtils.copyURLToFile(source, configFile);
    }

    /** */
    protected File configFile(File dir) {
        return new File(dir, XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME);
    }

    public void shouldNotLoadFrom(File dir) throws IOException {
        File configFile = configFile(dir);
        URL source = XMLConfigurationConstructorsTest.class.getResource(
                XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
        FileUtils.copyURLToFile(source, configFile);
    }

    @Test
    public void testDefaultToCacheDir() throws Exception {
        shouldLoadFrom(cacheDir);

        XMLConfiguration config = new XMLConfiguration(provider, storageFinder);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();
        config.getLayerCount();

        assertThat(config.getLayer(MARKED_LAYER), notNullValue());
    }

    @Test
    public void testOverrideWithProperty() throws Exception {
        shouldLoadFrom(configDir);
        shouldNotLoadFrom(cacheDir);

        gwcConfigDirProp.setValue(configDir.getCanonicalPath());

        XMLConfiguration config = new XMLConfiguration(provider, storageFinder);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();
        config.getLayerCount();

        assertThat(config.getLayer(MARKED_LAYER), notNullValue());
    }

    @Test
    public void testOverrideWithPropertyRelative() throws Exception {
        shouldLoadFrom(configDir);
        shouldNotLoadFrom(cacheDir);

        gwcConfigDirProp.setValue(configDir.getName());

        XMLConfiguration config = new XMLConfiguration(provider, storageFinder);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();
        config.getLayerCount();

        assertThat(config.getLayer(MARKED_LAYER), notNullValue());
    }

    @Test
    public void testPathAsArgument() throws Exception {
        shouldLoadFrom(configDir);
        shouldNotLoadFrom(cacheDir);

        XMLConfiguration config = new XMLConfiguration(provider, configDir.getCanonicalPath());
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();

        assertThat(config.getLayer(MARKED_LAYER), notNullValue());
    }

    @Test
    public void testDefaultToCacheDirCreate() throws Exception {
        ServerConfiguration config = new XMLConfiguration(provider, storageFinder);
        ((XMLConfiguration) config).setGridSetBroker(broker);
        config.afterPropertiesSet();

        assertThat(configFile(cacheDir).exists(), is(true));
        assertThat(configFile(configDir).exists(), is(false));
    }

    @Test
    public void testOverrideWithPropertyCreate() throws Exception {
        gwcConfigDirProp.setValue(configDir.getCanonicalPath());

        ServerConfiguration config = new XMLConfiguration(provider, storageFinder);
        ((XMLConfiguration) config).setGridSetBroker(broker);
        config.afterPropertiesSet();

        assertThat(configFile(cacheDir).exists(), is(false));
        assertThat(configFile(configDir).exists(), is(true));
    }

    @Test
    public void testPathAsArgumentCreate() throws Exception {
        ServerConfiguration config = new XMLConfiguration(provider, configDir.getCanonicalPath());
        ((XMLConfiguration) config).setGridSetBroker(broker);
        config.afterPropertiesSet();

        assertThat(configFile(cacheDir).exists(), is(false));
        assertThat(configFile(configDir).exists(), is(true));
    }
}
