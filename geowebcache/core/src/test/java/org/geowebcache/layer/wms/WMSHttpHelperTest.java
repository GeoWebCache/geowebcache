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
package org.geowebcache.layer.wms;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.layer.wms.WMSLayer.HttpRequestMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mockito;

public class WMSHttpHelperTest {

    /** Allows to set environment variables for each individual test */
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private WMSHttpHelper wmsHelper(String username, String password) {
        WMSHttpHelper wmsHelper = new WMSHttpHelper(username, password, null);
        wmsHelper.setGeoWebCacheEnvironment(new GeoWebCacheEnvironment());
        return wmsHelper;
    }

    private void enableEnvParametrization(boolean enable) {
        environmentVariables.set("ALLOW_ENV_PARAMETRIZATION", String.valueOf(enable));
    }

    @Test
    public void testNoGeoWebCacheEnvironmentSet() {
        WMSHttpHelper helper = wmsHelper("u${ername}", "pas${word}");
        helper.setGeoWebCacheEnvironment(null);
        assertEquals("u${ername}", helper.getResolvedHttpUsername());
        assertEquals("pas${word}", helper.getResolvedHttpPassword());
    }

    @Test
    public void testCredentialsUnchangedWhenEnvParametrizationDisabled() {
        WMSHttpHelper helper = wmsHelper("u${ername}", "pas${word}");
        assertEquals("u${ername}", helper.getResolvedHttpUsername());
        assertEquals("pas${word}", helper.getResolvedHttpPassword());

        enableEnvParametrization(false);
        assertEquals("u${ername}", helper.getResolvedHttpUsername());
        assertEquals("pas${word}", helper.getResolvedHttpPassword());
    }

    @Test
    public void testCredentialsResolvedWhenEnvParametrizationEnabled() {
        WMSHttpHelper helper = wmsHelper("${GWC_USER}", "${GWC_PWD}");

        enableEnvParametrization(true);
        assertEquals("${GWC_USER}", helper.getResolvedHttpUsername());
        assertEquals("${GWC_PWD}", helper.getResolvedHttpPassword());

        environmentVariables.set("GWC_USER", "user_resolved");
        environmentVariables.set("GWC_PWD", "pwd_resolved");

        assertEquals("user_resolved", helper.getResolvedHttpUsername());
        assertEquals("pwd_resolved", helper.getResolvedHttpPassword());
    }

    @Test
    public void testExecuteRequestUsesResolvedCredentials() throws IOException, URISyntaxException {
        enableEnvParametrization(true);
        environmentVariables.set("GWC_USER", "user_resolved");
        environmentVariables.set("GWC_PWD", "pwd_resolved");

        WMSHttpHelper helper = spy(wmsHelper("${GWC_USER}", "${GWC_PWD}"));
        // do not actually execute the request
        doReturn(null).when(helper).execute(any(CloseableHttpClient.class), any(HttpUriRequestBase.class));

        // just check the http client is built with the resolved credeltials
        URL url = new URI("http://example.com/wms?request=getcapabilities").toURL();
        helper.executeRequest(url, null, 0, HttpRequestMode.Get);
        verify(helper)
                .buildHttpClient(
                        Mockito.anyInt(),
                        Mockito.eq("user_resolved"),
                        Mockito.eq("pwd_resolved"),
                        Mockito.nullable(URL.class),
                        Mockito.anyInt());
    }
}
