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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.filter.parameters;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

public class ParametersUtilsTest {

    @Test
    public void testEmptyToKVP() {
        String result = ParametersUtils.getKvp(Collections.emptyMap());
        assertThat(result, emptyString());
    }

    @Test
    public void testEmptyToMap() {
        Map<String, String> result = ParametersUtils.getMap("");
        assertThat(result.entrySet(), empty());
    }

    @Test
    public void testSingletonToKVP() {
        String result = ParametersUtils.getKvp(Collections.singletonMap("test", "blah"));
        assertThat(result, Matchers.equalTo("test=blah"));
    }

    @Test
    public void testSingletonToMap() {
        Map<String, String> result = ParametersUtils.getMap("test=blah");
        assertThat(result, hasEntries(entry(equalTo("test"), equalTo("blah"))));
    }

    @Test
    public void testTwoToKVP() {
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("test1", "blah1");
        parameters.put("test2", "blah2");
        String result = ParametersUtils.getKvp(parameters);
        assertThat(result, Matchers.equalTo("test1=blah1&test2=blah2"));
    }

    @Test
    public void testTwoToMap() {
        Map<String, String> result = ParametersUtils.getMap("test1=blah1&test2=blah2");
        assertThat(
                result,
                hasEntries(entry(equalTo("test1"), equalTo("blah1")), entry(equalTo("test2"), equalTo("blah2"))));
    }

    @Test
    public void testTwoToKVPSorting() {
        Map<String, String> parameters =
                new TreeMap<>((s1, s2) -> -s1.compareTo(s2)); // Intentionally make the tree use reverse
        // alphabetical order
        parameters.put("test1", "blah1");
        parameters.put("test2", "blah2");
        String result = ParametersUtils.getKvp(parameters);
        assertThat(result, Matchers.equalTo("test1=blah1&test2=blah2")); // Should be normal alphabetical order
    }

    @Test
    public void testEqualsToKVP() {
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("=test1", "=blah1");
        parameters.put("te=st2", "bl=ah2");
        parameters.put("test3=", "blah3=");
        String result = ParametersUtils.getKvp(parameters);
        assertThat(result, Matchers.equalTo("%3Dtest1=%3Dblah1&te%3Dst2=bl%3Dah2&test3%3D=blah3%3D"));
    }

    @Test
    public void testEqualsToMap() {
        Map<String, String> result = ParametersUtils.getMap("%3Dtest1=%3Dblah1&te%3Dst2=bl%3Dah2&test3%3D=blah3%3D");
        assertThat(
                result,
                hasEntries(
                        entry(equalTo("=test1"), equalTo("=blah1")),
                        entry(equalTo("te=st2"), equalTo("bl=ah2")),
                        entry(equalTo("test3="), equalTo("blah3="))));
    }

    @Test
    public void testAmpToKVP() {
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("&test1", "&blah1");
        parameters.put("te&st2", "bl&ah2");
        parameters.put("test3&", "blah3&");
        String result = ParametersUtils.getKvp(parameters);
        assertThat(result, Matchers.equalTo("%26test1=%26blah1&te%26st2=bl%26ah2&test3%26=blah3%26"));
    }

    @Test
    public void testAmpToMap() {
        Map<String, String> result = ParametersUtils.getMap("%26test1=%26blah1&te%26st2=bl%26ah2&test3%26=blah3%26");
        assertThat(
                result,
                hasEntries(
                        entry(equalTo("&test1"), equalTo("&blah1")),
                        entry(equalTo("te&st2"), equalTo("bl&ah2")),
                        entry(equalTo("test3&"), equalTo("blah3&"))));
    }

    @SafeVarargs
    static <K, V> Matcher<Map<K, V>> hasEntries(Matcher<Entry<K, V>>... entryMatchers) {
        final Matcher<? super Set<Entry<K, V>>> entrySetMatcher = Matchers.containsInAnyOrder(entryMatchers);
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof Map<?, ?> map) {
                    return entrySetMatcher.matches(map.entrySet());
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has entries ");
                description.appendDescriptionOf(entrySetMatcher);
            }
        };
    }

    static <K, V> Matcher<Entry<K, V>> entry(Matcher<K> key, Matcher<V> value) {
        return Matchers.allOf(
                Matchers.instanceOf(Entry.class),
                Matchers.hasProperty("key", key),
                Matchers.hasProperty("value", value));
    }
}
