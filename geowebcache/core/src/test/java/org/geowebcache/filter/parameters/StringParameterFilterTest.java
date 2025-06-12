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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import com.thoughtworks.xstream.XStream;
import java.util.Arrays;
import java.util.Locale;
import org.custommonkey.xmlunit.XMLAssert;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.filter.parameters.CaseNormalizer.Case;
import org.geowebcache.io.GeoWebCacheXStream;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.support.StaticWebApplicationContext;

public class StringParameterFilterTest {

    private StringParameterFilter filter;
    private XStream xs;

    @Before
    public void setUp() {
        filter = new StringParameterFilter();
        filter.setKey("TEST");
        filter.setValues(Arrays.asList("foo", "Bar", "BAZ"));
        filter.setDefaultValue("Default");

        xs = new GeoWebCacheXStream();
        xs = XMLConfiguration.getConfiguredXStream(xs, new StaticWebApplicationContext());
    }

    @Test
    public void testBasic() throws Exception {
        assertThat(
                filter.getNormalize(),
                allOf(hasProperty("case", equalTo(Case.NONE)), hasProperty("locale", equalTo(Locale.getDefault()))));

        assertThat(filter.getLegalValues(), containsInAnyOrder("foo", "Bar", "BAZ"));

        for (String test : Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test));
        }
        for (String test : Arrays.asList("Foo", "FOO", "BaR", "bar", "BAR", "BAz", "baz")) {
            assertThat(filter.applies(test), is(false));
            try {
                filter.apply(test);
                fail();
            } catch (Exception ex) {
                assertThat(ex, instanceOf(ParameterException.class));
            }
        }
        for (String test : Arrays.asList("fooo", "fo", "Ba", "BBarr", "BA", "BBAZ")) {
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
    public void testNormalizeUpper() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.UPPER, Locale.ENGLISH));

        assertThat(filter.getLegalValues(), containsInAnyOrder("FOO", "BAR", "BAZ"));

        for (String test : Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toUpperCase(Locale.ENGLISH)));
        }
        for (String test : Arrays.asList("Foo", "FOO", "BaR", "bar", "BAR", "BAz", "baz")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toUpperCase(Locale.ENGLISH)));
        }
        for (String test : Arrays.asList("fooo", "fo", "Ba", "BBarr", "BA", "BBAZ")) {
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
    public void testNormalizeLower() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.LOWER, Locale.ENGLISH));

        assertThat(filter.getLegalValues(), containsInAnyOrder("foo", "bar", "baz"));

        for (String test : Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.ENGLISH)));
        }
        for (String test : Arrays.asList("Foo", "FOO", "BaR", "bar", "BAR", "BAz", "baz")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.ENGLISH)));
        }
        for (String test : Arrays.asList("fooo", "fo", "Ba", "BBarr", "BA", "BBAZ")) {
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
    public void testNormalizeLocale() throws Exception {
        filter.setValues(Arrays.asList("f\u0131o", "B\u0131r", "BIZ"));

        filter.setNormalize(new CaseNormalizer(Case.LOWER, Locale.forLanguageTag("tr")));

        assertThat(filter.getLegalValues(), containsInAnyOrder("f\u0131o", "b\u0131r", "b\u0131z"));

        for (String test : Arrays.asList("f\u0131o", "B\u0131r", "BIZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.forLanguageTag("tr"))));
        }
        for (String test : Arrays.asList("F\u0131o", "FIO", "B\u0131R", "b\u0131r", "BIR", "BIz", "b\u0131z")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.forLanguageTag("tr"))));
        }
        for (String test : Arrays.asList("f\u0131oo", "f\u0131", "B\u0131", "BB\u0131rr", "BI", "BBIZ")) {
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
    public void testToXMLNullNormalizer() throws Exception {
        XMLAssert.assertXMLEqual(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""",
                xs.toXML(filter));
    }

    @Test
    public void testToXMLDefaultNormalizer() throws Exception {
        filter.setNormalize(new CaseNormalizer());
        XMLAssert.assertXMLEqual(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <normalize/>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""",
                xs.toXML(filter));
    }

    @Test
    public void testToXMLNoneNormalizer() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.NONE));
        XMLAssert.assertXMLEqual(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <normalize>
                    <case>NONE</case>
                  </normalize>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""",
                xs.toXML(filter));
    }

    @Test
    public void testToXMLUpperCanadianEnglish() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.UPPER, Locale.CANADA));
        XMLAssert.assertXMLEqual(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <normalize>
                    <case>UPPER</case>
                    <locale>en_CA</locale>
                  </normalize>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""",
                xs.toXML(filter));
    }

    @Test
    public void testFromXMLUpperCanadianEnglish() throws Exception {
        Object o = xs.fromXML(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <normalize>
                    <case>UPPER</case>
                    <locale>en_CA</locale>
                  </normalize>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""");

        assertThat(o, instanceOf(StringParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(
                o,
                hasProperty(
                        "normalize",
                        allOf(
                                instanceOf(CaseNormalizer.class),
                                hasProperty("case", is(Case.UPPER)),
                                hasProperty("locale", is(Locale.CANADA)))));
        assertThat(o, hasProperty("values", containsInAnyOrder("foo", "Bar", "BAZ")));
    }

    @Test
    public void testFromXMLIdentifiersCaseInsensitive() throws Exception {
        Object o = xs.fromXML(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <normalize>
                    <case>uPPer</case>
                    <locale>EN_ca</locale>
                  </normalize>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""");

        assertThat(o, instanceOf(StringParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(
                o,
                hasProperty(
                        "normalize",
                        allOf(
                                instanceOf(CaseNormalizer.class),
                                hasProperty("case", is(Case.UPPER)),
                                hasProperty("locale", is(Locale.CANADA)))));
        assertThat(o, hasProperty("values", containsInAnyOrder("foo", "Bar", "BAZ")));
    }

    @Test
    public void testFromXMLDefaultNormalize() throws Exception {
        // important for backward compatibility with older config files
        Object o = xs.fromXML(
                """
                <stringParameterFilter>
                  <key>TEST</key>
                  <defaultValue>Default</defaultValue>
                  <values>
                    <string>foo</string>
                    <string>Bar</string>
                    <string>BAZ</string>
                  </values>
                </stringParameterFilter>""");

        assertThat(o, instanceOf(StringParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(
                o,
                hasProperty("normalize", allOf(instanceOf(CaseNormalizer.class), hasProperty("case", is(Case.NONE)))));
        assertThat(o, hasProperty("values", containsInAnyOrder("foo", "Bar", "BAZ")));
    }

    @Test
    public void testCloneable() throws Exception {
        StringParameterFilter clone = filter.clone();
        assertThat(clone.getDefaultValue(), equalTo(filter.getDefaultValue()));
        assertThat(clone.getValues(), equalTo(filter.getValues()));
        assertThat(clone.normalize, equalTo(filter.normalize));

        filter.setNormalize(new CaseNormalizer(Case.UPPER, Locale.ENGLISH));
        clone = filter.clone();
        assertThat(clone.getDefaultValue(), equalTo(filter.getDefaultValue()));
        assertThat(clone.getValues(), equalTo(filter.getValues()));
        assertThat(clone.normalize, equalTo(filter.normalize));
    }
}
