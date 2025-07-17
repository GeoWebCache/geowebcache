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

public class IntegerParameterFilterTest {

    private IntegerParameterFilter filter;
    private XStream xs;

    @Before
    public void setUp() {
        filter = new IntegerParameterFilter();
        filter.setKey("TEST");
        filter.setValues(Arrays.asList(42, 2, 0, -1, -200));
        filter.setDefaultValue("Default");
        filter.setThreshold(1);

        xs = new GeoWebCacheXStream();
        xs = XMLConfiguration.getConfiguredXStream(xs, new StaticWebApplicationContext());
    }

    @Test
    public void testBasic() throws Exception {

        assertThat(filter.getLegalValues(), containsInAnyOrder("42", "2", "0", "-1", "-200"));

        for (String test : Arrays.asList("42", "2", "0", "-1", "-200")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo(test));
        }
        for (String test : Arrays.asList("43", "41", "3", "1", "-2", "-199", "-201", "-42")) {
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
        filter.setThreshold(15);

        for (String test : Arrays.asList("42", "2", "0", "-1", "-200")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo(test));
        }

        for (String test : Arrays.asList("42", "56", "28")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo("42"));
        }
        for (String test : Arrays.asList("2", "16", "1")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo("2"));
        }
        for (String test : Arrays.asList("0")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo("0"));
        }
        for (String test : Arrays.asList("-1", "-15")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo("-1"));
        }
        for (String test : Arrays.asList("-200", "-186", "-214")) {
            assertThat(filter.applies(test), Matchers.describedAs("Filter should apply to %0", is(true), test));
            assertThat(filter.apply(test), equalTo("-200"));
        }
        for (String test : Arrays.asList("57", "27", "-42", "-100")) {
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
                <integerParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <values>
                    <int>42</int>
                    <int>2</int>
                    <int>0</int>
                    <int>-1</int>
                    <int>-200</int>
                  </values>
                  <threshold>1</threshold>
                </integerParameterFilter>""",
                xs.toXML(filter));
    }

    @Test
    public void testFromXML() throws Exception {
        Object o = xs.fromXML(
                """
                <integerParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <values>
                    <int>42</int>
                    <int>2</int>
                    <int>0</int>
                    <int>-1</int>
                    <int>-200</int>
                  </values>
                  <threshold>15</threshold>
                </integerParameterFilter>""");

        assertThat(o, instanceOf(IntegerParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(o, hasProperty("threshold", equalTo(15)));
        assertThat(o, hasProperty("values", containsInAnyOrder(42, 2, 0, -1, -200)));
    }

    @Test
    public void testCloneable() throws Exception {
        IntegerParameterFilter clone = filter.clone();
        assertThat(clone.getDefaultValue(), equalTo(filter.getDefaultValue()));
        assertThat(clone.getValues(), equalTo(filter.getValues()));
    }
}
