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
 */
package org.geowebcache.config;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSSourceHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class EnvironmentAwareXMLConfigurationTest {
    /** Allows to set environment variables for each individual test */
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    /** Loaded from {@literal geowebcache-config-env.xml}, defines the {@link WMSLayer}s below */
    private XMLConfiguration xmlConfig;

    /** Test layer with http user/pwd set from global config */
    private WMSLayer withDefaultCredentials;

    /** Test layer with http user/pwd defined as env variable placeholders */
    private WMSLayer withEnvVariableCredentials;

    /** Test layer with its own, non parametrized http user/pwd */
    private WMSLayer withCustomCredentials;

    /**
     * Test layer with its own, non parametrized http user/pwd, where their values contain the '${' placeholder prefix
     */
    private WMSLayer customCredentialsWithEnvPrefix;

    /**
     * Make {@code GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class)} return a bean, for
     * {@link XMLConfiguration#setDefaultValues(TileLayer)} to set it on each
     * {@link WMSHttpHelper#setGeoWebCacheEnvironment(GeoWebCacheEnvironment)}
     */
    @BeforeClass
    public static void setUpAppContext() {
        GeoWebCacheEnvironment gwcEnv = new GeoWebCacheEnvironment();
        ApplicationContext appContext = mock(ApplicationContext.class);

        when(appContext.getBeansOfType(GeoWebCacheEnvironment.class))
                .thenReturn(Map.of("geoWebCacheEnvironment", gwcEnv));
        when(appContext.getBean("geoWebCacheEnvironment")).thenReturn(gwcEnv);
        new GeoWebCacheExtensions().setApplicationContext(appContext);
    }

    /**
     * Set up the following environment variables and load the test layers from {@literal geowebcache-config-env.xml}:
     *
     * <ul>
     *   <li>{@code DEFAULT_USER=default_user_value}
     *   <li>{@code DEFAULT_SECRET=default_secret_value}
     *   <li>{@code CUSTOM_USER=custom_user_value}
     *   <li>{@code CUSTOM_SECRET=custom_secret_value}
     * </ul>
     */
    @Before
    public void setUp() throws URISyntaxException, GeoWebCacheException {

        setEnvironmentVariablesForCredentials();

        xmlConfig = loadConfig();
        withDefaultCredentials = getLayer("default_credentials");
        withEnvVariableCredentials = getLayer("env_var_credentials");
        withCustomCredentials = getLayer("custom_credentials");
        customCredentialsWithEnvPrefix = getLayer("custom_credentials_with_env_prefix");
    }

    private void setEnvironmentVariablesForCredentials() {
        environmentVariables.set("DEFAULT_USER", "default_user_value");
        environmentVariables.set("DEFAULT_SECRET", "default_secret_value");
        environmentVariables.set("CUSTOM_USER", "custom_user_value");
        environmentVariables.set("CUSTOM_SECRET", "custom_secret_value");
    }

    private void enableEnvParametrization() {
        environmentVariables.set("ALLOW_ENV_PARAMETRIZATION", "true");
    }

    private WMSLayer getLayer(String layerName) {
        return (WMSLayer) xmlConfig.getLayer(layerName).orElseThrow();
    }

    private XMLConfiguration loadConfig() throws GeoWebCacheException, URISyntaxException {
        File cpDirectory =
                new File(requireNonNull(this.getClass().getResource("./")).toURI());
        XMLFileResourceProvider resourceProvider = new XMLFileResourceProvider(
                "geowebcache-config-env.xml", (WebApplicationContext) null, cpDirectory.getAbsolutePath(), null);

        XMLConfiguration config = new XMLConfiguration(null, resourceProvider);
        config.setGridSetBroker(new GridSetBroker(List.of(new DefaultGridsets(true, true))));
        config.afterPropertiesSet();
        return config;
    }

    @Test
    public void testLayerWithDefaultCredentialsAllowEnvDisabled() {
        assertCredentials(withDefaultCredentials, null, null, "layer loading shall not change the layer config");

        xmlConfig.setDefaultValues(withDefaultCredentials);
        assertCredentials(
                withDefaultCredentials, null, null, "setting default values shall not change the layer config");

        assertCredentials(
                withDefaultCredentials.getSourceHelper(),
                "${DEFAULT_USER}",
                "${DEFAULT_SECRET}",
                "sourceHelper should be assigned the default user and password, no env var substitution expected");
    }

    @Test
    public void testLayerWithDefaultCredentialsAllowEnvEnabled() {
        enableEnvParametrization();

        assertCredentials(withDefaultCredentials, null, null, "layer loading shall not change the layer config");

        xmlConfig.setDefaultValues(withDefaultCredentials);
        assertCredentials(
                withDefaultCredentials, null, null, "setting default values shall not change the layer config");

        assertCredentials(
                withDefaultCredentials.getSourceHelper(),
                "default_user_value",
                "default_secret_value",
                "sourceHelper should have resolved the global user and password");
    }

    @Test
    public void testLayerWithEnvVarCredentialsAllowEnvDisabled() {
        assertCredentials(
                withEnvVariableCredentials,
                "${CUSTOM_USER}",
                "${CUSTOM_SECRET}",
                "layer loading shall not change the layer config");

        xmlConfig.setDefaultValues(withEnvVariableCredentials);
        assertCredentials(
                withEnvVariableCredentials,
                "${CUSTOM_USER}",
                "${CUSTOM_SECRET}",
                "setting default values shall not change the layer config");

        assertCredentials(
                withEnvVariableCredentials.getSourceHelper(),
                "${CUSTOM_USER}",
                "${CUSTOM_SECRET}",
                "sourceHelper should keep the layer user and password, no env var substitution expected");
    }

    @Test
    public void testLayerWithEnvVarCredentialsAllowEnvEnabled() {
        enableEnvParametrization();

        assertCredentials(
                withEnvVariableCredentials,
                "${CUSTOM_USER}",
                "${CUSTOM_SECRET}",
                "layer loading shall not change the layer config");

        xmlConfig.setDefaultValues(withEnvVariableCredentials);
        assertCredentials(
                withEnvVariableCredentials,
                "${CUSTOM_USER}",
                "${CUSTOM_SECRET}",
                "setting default values shall not change the layer config");

        assertCredentials(
                withEnvVariableCredentials.getSourceHelper(),
                "custom_user_value",
                "custom_secret_value",
                "sourceHelper should have resolved the layer's user and password variables");
    }

    @Test
    public void testLayerWithCustomCredentialsIsAllowEnvAgnostic() {

        assertLayerUnchanged(withCustomCredentials, "testuser", "testpass");

        enableEnvParametrization();

        assertLayerUnchanged(withCustomCredentials, "testuser", "testpass");
    }

    @Test
    public void testLayerWithCustomCredentialsHavingPlaceholderPrefixIsAllowEnvAgnostic() {

        assertLayerUnchanged(customCredentialsWithEnvPrefix, "${user", "pass${word");

        enableEnvParametrization();

        assertLayerUnchanged(customCredentialsWithEnvPrefix, "${user", "pass${word");
    }

    public void assertLayerUnchanged(WMSLayer staticCredentials, String user, String password) {
        assertCredentials(
                staticCredentials,
                user,
                password,
                "parametrization shouldn't affect a non parametrized layer's credentials");

        xmlConfig.setDefaultValues(staticCredentials);
        assertCredentials(
                staticCredentials,
                user,
                password,
                "set default values shouldn't affect a non parametrized layer's credenals");

        assertCredentials(
                (WMSHttpHelper) staticCredentials.getSourceHelper(),
                user,
                password,
                "set default values shouldn't affect a non parametrized layer's credenals");

        WMSHttpHelper sourceHelper = (WMSHttpHelper) staticCredentials.getSourceHelper();
        assertCredentials(
                sourceHelper,
                user,
                password,
                "source helper shouldn't not change credential values on a non parametrized layer");
    }

    private void assertCredentials(
            WMSSourceHelper wmsSourceHelper, String expectedUser, String expectedPassword, String message) {

        WMSHttpHelper helper = (WMSHttpHelper) wmsSourceHelper;
        assertEquals(message, expectedUser, helper.getResolvedHttpUsername());
        assertEquals(message, expectedPassword, helper.getResolvedHttpPassword());
    }

    private void assertCredentials(WMSLayer layer, String expectedUser, String expectedPassword, String message) {
        assertEquals(message, expectedUser, layer.getHttpUsername());
        assertEquals(message, expectedPassword, layer.getHttpPassword());
    }
}
