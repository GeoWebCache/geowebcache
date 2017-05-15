/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.geowebcache.filter.parameters;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Locale;

import org.custommonkey.xmlunit.XMLAssert;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.filter.parameters.CaseNormalizer.Case;
import org.geowebcache.io.GeoWebCacheXStream;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.support.StaticWebApplicationContext;

import com.thoughtworks.xstream.XStream;

public class FreeStringParameterFilterTest {
    
    private FreeStringParameterFilter filter;
    private XStream xs;
    
    @Before
    public void setUp() {
        filter = new FreeStringParameterFilter();
        filter.setKey("FOO");
        filter.setDefaultValue("Default");
        
        xs = new GeoWebCacheXStream();
        xs = XMLConfiguration.getConfiguredXStream(xs, new StaticWebApplicationContext());
    }
    
    @Test
    public void testBasic() throws Exception {
        assertNull(filter.getLegalValues());
        
        for(String test: Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test));
        }

        assertThat(filter.apply(null), equalTo("Default"));
        assertThat(filter.apply(""), equalTo("Default"));
    }


    @Test
    public void testNormalizeUpper() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.UPPER, Locale.ENGLISH));

        assertThat(filter.apply("tEst"), equalTo("TEST"));
    }
    
    @Test
    public void testCloneable() throws Exception {
        FreeStringParameterFilter clone = filter.clone();
        assertThat(clone.getDefaultValue(), equalTo(filter.getDefaultValue()));
        assertThat(clone.getKey(), equalTo(filter.getKey()));
    }
}
