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
 * @author Fernando Mino, GeoSolutions, Copyright 2026
 */
package org.geowebcache.util;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class URLManglerUtilsTest {

    @Test
    public void testBuildURLAppendsMutatedParametersAfterPathQuery() {
        URLMangler mangler = (base, path, kvp, type) -> kvp.put("projecttoken", "abc 123");
        assertEquals(
                "https://host/app/wmts/{TileRow}?format=image/png&projecttoken=abc+123",
                URLManglerUtils.buildURL(
                        "https://host/",
                        "/app",
                        "/wmts/{TileRow}?format=image/png",
                        new LinkedHashMap<>(),
                        mangler,
                        URLMangler.URLType.SERVICE));
    }

    @Test
    public void testBuildURLPreservesOrderAndFragment() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("first", "one");
        parameters.put("second", "two");
        assertEquals(
                "https://host/path?existing=yes&first=one&second=two#fragment",
                URLManglerUtils.buildURL(
                        "https://host",
                        null,
                        "/path?existing=yes#fragment",
                        parameters,
                        new NullURLMangler(),
                        URLMangler.URLType.RESOURCE));
    }

    @Test
    public void testBuildURLUsesCompleteURLCreatedByMangler() {
        URLMangler mangler = (base, path, kvp, type) -> {
            base.setLength(0);
            base.append("https://proxy.example.com/complete");
            path.setLength(0);
            kvp.clear();
        };
        assertEquals(
                "https://proxy.example.com/complete",
                URLManglerUtils.buildURL(
                        "https://host", "/context", "/path", null, mangler, URLMangler.URLType.SERVICE));
    }

    @Test
    public void testBuildURLPassesTypeAndAllowsBaseAndPathChanges() {
        URLMangler mangler = (base, path, kvp, type) -> {
            assertEquals(URLMangler.URLType.RESOURCE, type);
            base.append("/proxy");
            path.append("/resource");
            kvp.put("token", "value");
        };
        assertEquals(
                "https://host/proxy/context/path/resource?token=value",
                URLManglerUtils.buildURL(
                        "https://host", "/context", "/path", null, mangler, URLMangler.URLType.RESOURCE));
    }
}
