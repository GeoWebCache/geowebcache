package org.geowebcache.layer.wms;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.layer.TileLayer;
import org.junit.Test;

public abstract class TileLayerTest {

    @Test
    public void testGetModifiableParameters() throws Exception {
        List<ParameterFilter> filters = new LinkedList<>();
        {
            StringParameterFilter filter = new StringParameterFilter();
            filter.setKey("FILTER1");
            filter.setValues(Arrays.asList("foo", "bar"));
            filters.add(filter);
        }
        {
            StringParameterFilter filter = new StringParameterFilter();
            filter.setKey("FILTER2");
            filter.setValues(Arrays.asList("quux", "quam"));
            filters.add(filter);
        }
        {
            StringParameterFilter filter = new StringParameterFilter();
            filter.setKey("FILTER3");
            filter.setValues(Arrays.asList("quux", "quam"));
            filters.add(filter);
        }

        Map<String, Object> rawParams = new HashMap<>();
        {
            rawParams.put("FILTER1", new String[] {"bar"});
            // Nothing for FILTER2
            rawParams.put("FILTER3", new String[] {"quux"});
            rawParams.put("FILTER4", new String[] {"quux"});
        }

        TileLayer layer = getLayerWithFilters(filters);

        Map<String, String> result = layer.getModifiableParameters(rawParams, "UTF-8");

        assertThat(result, hasEntry(equalToIgnoringCase("FILTER1"), equalTo("bar")));
        assertThat(result, hasEntry(equalToIgnoringCase("FILTER2"), equalTo(""))); // Default is empty
        assertThat(result, hasEntry(equalToIgnoringCase("FILTER3"), equalTo("quux")));
        assertThat(result, not(hasEntry(equalToIgnoringCase("FILTER4"), notNullValue())));
    }

    protected abstract TileLayer getLayerWithFilters(Collection<ParameterFilter> filters) throws Exception;
}
