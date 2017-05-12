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
 * @author Kevin Smith, Boundless, Copyright 2015
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
        assertThat(filter.getNormalize(), allOf(
                hasProperty("case", equalTo(Case.NONE)),
                hasProperty("locale", equalTo(Locale.getDefault()))));
        
        assertThat(filter.getLegalValues(), containsInAnyOrder("foo", "Bar", "BAZ"));
        
        for(String test: Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test));
        }
        for(String test: Arrays.asList("Foo", "FOO", "BaR", "bar", "BAR", "BAz", "baz")) {
            assertThat(filter.applies(test), is(false));
            try {
                filter.apply(test);
                fail();
            } catch (Exception ex) {
                assertThat(ex, instanceOf(ParameterException.class));
            }
        }
        for(String test: Arrays.asList("fooo", "fo", "Ba", "BBarr", "BA", "BBAZ")) {
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
        
        for(String test: Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toUpperCase(Locale.ENGLISH)));
        }
        for(String test: Arrays.asList("Foo", "FOO", "BaR", "bar", "BAR", "BAz", "baz")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toUpperCase(Locale.ENGLISH)));
        }
        for(String test: Arrays.asList("fooo", "fo", "Ba", "BBarr", "BA", "BBAZ")) {
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
        
        for(String test: Arrays.asList("foo", "Bar", "BAZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.ENGLISH)));
        }
        for(String test: Arrays.asList("Foo", "FOO", "BaR", "bar", "BAR", "BAz", "baz")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.ENGLISH)));
        }
        for(String test: Arrays.asList("fooo", "fo", "Ba", "BBarr", "BA", "BBAZ")) {
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
        
        for(String test: Arrays.asList("f\u0131o", "B\u0131r", "BIZ")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.forLanguageTag("tr"))));
        }
        for(String test: Arrays.asList("F\u0131o", "FIO", "B\u0131R", "b\u0131r", "BIR", "BIz", "b\u0131z")) {
            assertThat(filter.applies(test), is(true));
            assertThat(filter.apply(test), equalTo(test.toLowerCase(Locale.forLanguageTag("tr"))));
        }
        for(String test: Arrays.asList("f\u0131oo", "f\u0131", "B\u0131", "BB\u0131rr", "BI", "BBIZ")) {
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
        XMLAssert.assertXMLEqual("<stringParameterFilter id=\"1\">\n"+
                "  <key>TEST</key>\n"+
                "  <defaultValue>Default</defaultValue>\n"+
                "  <values id=\"2\">\n"+
                "    <string>foo</string>\n"+
                "    <string>Bar</string>\n"+
                "    <string>BAZ</string>\n"+
                "  </values>\n"+
                "</stringParameterFilter>", xs.toXML(filter));
    }
    
    @Test
    public void testToXMLDefaultNormalizer() throws Exception {
        filter.setNormalize(new CaseNormalizer());
        XMLAssert.assertXMLEqual("<stringParameterFilter id=\"1\">\n"+
                "  <key>TEST</key>\n"+
                "  <defaultValue>Default</defaultValue>\n"+
                "  <normalize id=\"2\"/>\n"+
                "  <values id=\"3\">\n"+
                "    <string>foo</string>\n"+
                "    <string>Bar</string>\n"+
                "    <string>BAZ</string>\n"+
                "  </values>\n"+
                "</stringParameterFilter>", xs.toXML(filter));
    }
    
    @Test
    public void testToXMLNoneNormalizer() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.NONE));
        XMLAssert.assertXMLEqual("<stringParameterFilter id=\"1\">\n"+
                                 "  <key>TEST</key>\n"+
                                 "  <defaultValue>Default</defaultValue>\n"+
                                 "  <normalize id=\"2\">\n"+
                                 "    <case>NONE</case>\n"+
                                 "  </normalize>\n"+
                                 "  <values id=\"3\">\n"+
                                 "    <string>foo</string>\n"+
                                 "    <string>Bar</string>\n"+
                                 "    <string>BAZ</string>\n"+
                                 "  </values>\n"+
                                 "</stringParameterFilter>", xs.toXML(filter));
    }
    
    @Test
    public void testToXMLUpperCanadianEnglish() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.UPPER, Locale.CANADA));
        XMLAssert.assertXMLEqual("<stringParameterFilter id=\"1\">\n"+
                                 "  <key>TEST</key>\n"+
                                 "  <defaultValue>Default</defaultValue>\n"+
                                 "  <normalize id=\"2\">\n"+
                                 "    <case>UPPER</case>\n"+
                                 "    <locale id=\"3\">en_CA</locale>\n"+
                                 "  </normalize>\n"+
                                 "  <values id=\"4\">\n"+
                                 "    <string>foo</string>\n"+
                                 "    <string>Bar</string>\n"+
                                 "    <string>BAZ</string>\n"+
                                 "  </values>\n"+
                                 "</stringParameterFilter>", xs.toXML(filter));
    }
    
    @Test
    public void testFromXMLUpperCanadianEnglish() throws Exception {
        Object o = xs.fromXML(
            "<stringParameterFilter>\n"+
            "  <key>TEST</key>\n"+
            "  <defaultValue>Default</defaultValue>\n"+
            "  <normalize>\n"+
            "    <case>UPPER</case>\n"+
            "    <locale>en_CA</locale>\n"+
            "  </normalize>\n"+
            "  <values>\n"+
            "    <string>foo</string>\n"+
            "    <string>Bar</string>\n"+
            "    <string>BAZ</string>\n"+
            "  </values>\n"+
            "</stringParameterFilter>");
        
        assertThat(o, instanceOf(StringParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(o, hasProperty("normalize", allOf(
                instanceOf(CaseNormalizer.class),
                hasProperty("case", is(Case.UPPER)),
                hasProperty("locale", is(Locale.CANADA))
                )));
        assertThat(o, hasProperty("values", containsInAnyOrder("foo", "Bar", "BAZ")));
    }
    
    @Test
    public void testFromXMLIdentifiersCaseInsensitive() throws Exception {
        Object o = xs.fromXML(
            "<stringParameterFilter>\n"+
            "  <key>TEST</key>\n"+
            "  <defaultValue>Default</defaultValue>\n"+
            "  <normalize>\n"+
            "    <case>uPPer</case>\n"+
            "    <locale>EN_ca</locale>\n"+
            "  </normalize>\n"+
            "  <values>\n"+
            "    <string>foo</string>\n"+
            "    <string>Bar</string>\n"+
            "    <string>BAZ</string>\n"+
            "  </values>\n"+
            "</stringParameterFilter>");
        
        assertThat(o, instanceOf(StringParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(o, hasProperty("normalize", allOf(
                instanceOf(CaseNormalizer.class),
                hasProperty("case", is(Case.UPPER)),
                hasProperty("locale", is(Locale.CANADA))
                )));
        assertThat(o, hasProperty("values", containsInAnyOrder("foo", "Bar", "BAZ")));
    }
    
    @Test
    public void testFromXMLDefaultNormalize() throws Exception {
        // important for backward compatibility with older config files
        Object o = xs.fromXML(
            "<stringParameterFilter>\n"+
            "  <key>TEST</key>\n"+
            "  <defaultValue>Default</defaultValue>\n"+
            "  <values>\n"+
            "    <string>foo</string>\n"+
            "    <string>Bar</string>\n"+
            "    <string>BAZ</string>\n"+
            "  </values>\n"+
            "</stringParameterFilter>");
        
        assertThat(o, instanceOf(StringParameterFilter.class));
        assertThat(o, hasProperty("key", equalTo("TEST")));
        assertThat(o, hasProperty("defaultValue", equalTo("Default")));
        assertThat(o, hasProperty("normalize", allOf(
                instanceOf(CaseNormalizer.class),
                hasProperty("case", is(Case.NONE))
                )));
        assertThat(o, hasProperty("values", containsInAnyOrder("foo", "Bar", "BAZ")));
    }
    
    @Test
    public void testCloneable() throws Exception {
        filter.setNormalize(new CaseNormalizer(Case.UPPER, Locale.ENGLISH));
        StringParameterFilter clone = filter.clone();
        assertThat(clone.getDefaultValue(), equalTo(filter.getDefaultValue()));
        assertThat(clone.getValues(), equalTo(filter.getValues()));
        assertThat(clone.getNormalize().getConfiguredLocale(), equalTo(filter.getNormalize().getConfiguredLocale()));
        assertThat(clone.getNormalize().getCase(), equalTo(filter.getNormalize().getCase()));
    }
}
