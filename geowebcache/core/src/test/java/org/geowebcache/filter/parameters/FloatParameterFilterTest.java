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
 * @author Kevin Smith, Boundless, Copyright 2015
 */
package org.geowebcache.filter.parameters;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import com.thoughtworks.xstream.XStream;
import java.util.Arrays;
import org.custommonkey.xmlunit.XMLAssert;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.support.StaticWebApplicationContext;

public class FloatParameterFilterTest {

    private FloatParameterFilter filter;
    private XStream xs;

    @Before
    public void setUp() {
        filter = new FloatParameterFilter();
        filter.setKey("TEST");
        filter.setValues(Arrays.asList(42f, 6.283f, -17.5f));
        filter.setDefaultValue("Default");
        filter.setThreshold(0.00001f);

        xs = new GeoWebCacheXStream();
        xs = XMLConfiguration.getConfiguredXStream(xs, new StaticWebApplicationContext());
    }

    @Test
    public void testBasic() throws Exception {

        assertThat(filter.getLegalValues(), containsInAnyOrder("42.0", "6.283", "-17.5"));

        for (String test : Arrays.asList("42.0", "6.283", "-17.5", "42", "6.2830", "-1.75e1")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            // assertThat(filter.apply(test), equalTo(test));
        }
        for (String test : Arrays.asList("42.0", "42", "4.2e1")) {
            assertThat(filter.apply(test), equalTo("42.0"));
        }
        for (String test : Arrays.asList("42.5", "6.281", "-17.52", "-42.0", "-6.283", "17.5")) {
            assertThat(filter.applies(test), is(false));
            try {
                filter.apply(test);
                fail();
            } catch (Exception ex) {
                assertThat(ex, instanceOf(ParameterException.class));
            }
        }
        assertThat(filter.apply(null), equalTo("Default"));
        assertThat(filter.apply(""), equalTo("Default"));
    }

    @Test
    public void testThreshold() throws Exception {
        filter.setThreshold(15f);

        assertThat(filter.getLegalValues(), containsInAnyOrder("42.0", "6.283", "-17.5"));

        for (String test : Arrays.asList("42.0", "6.283", "-17.5", "42", "6.2830", "-1.75e1")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
        }
        for (String test : Arrays.asList("42", "56.99999", "27.00001")) {
            assertThat(filter.apply(test), equalTo("42.0"));
        }
        for (String test : Arrays.asList(
                "6.283", Float.toString(6.283f + 15f - 0.00001f), Float.toString((6.283f - 17.5f) / 2 + 0.000001f))) {
            assertThat(filter.apply(test), equalTo("6.283"));
        }
        for (String test : Arrays.asList(
                "-17.5", Float.toString(-17.5f - 15f + 0.00001f), Float.toString((6.283f - 17.5f) / 2 - 0.000001f))) {
            assertThat(filter.apply(test), equalTo("-17.5"));
        }
        for (String test : Arrays.asList("57", "27", "-42")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should not apply to %0", is(false), test));
            try {
                filter.apply(test);
                fail();
            } catch (Exception ex) {
                assertThat(ex, instanceOf(ParameterException.class));
            }
        }
        assertThat(filter.apply(null), equalTo("Default"));
        assertThat(filter.apply(""), equalTo("Default"));
    }

    @Test
    public void testToXML() throws Exception {
        XMLAssert.assertXMLEqual(
                """
                <floatParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <values>
                    <float>42.0</float>
                    <float>6.283</float>
                    <float>-17.5</float>
                  </values>
                  <threshold>1.0E-5</threshold>
                </floatParameterFilter>""",
                xs.toXML(filter));
    }

    @Test
    public void testFromXML() throws Exception {
        Object o = xs.fromXML(
                """
                <floatParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <values>
                    <float>42</float>
                    <float>6.283</float>
                    <float>-17.5</float>
                  </values>
                  <threshold>0.00001</threshold>
                </floatParameterFilter>""");

        assertThat(o, instanceOf(FloatParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(o, hasProperty("threshold", equalTo(0.00001f))); // Should be exactly this
        assertThat(o, hasProperty("values", containsInAnyOrder(42f, 6.283f, -17.5f)));
    }

    @Test
    public void testCloneable() throws Exception {
        FloatParameterFilter clone = filter.clone();
        assertThat(clone.getDefaultValue(), equalTo(filter.getDefaultValue()));
        assertThat(clone.getValues(), equalTo(filter.getValues()));
    }
}
