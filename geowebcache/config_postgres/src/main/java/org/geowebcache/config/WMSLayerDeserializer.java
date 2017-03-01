package org.geowebcache.config;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.filter.parameters.FloatParameterFilter;
import org.geowebcache.filter.parameters.IntegerParameterFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.layer.wms.WMSLayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Class to handle parsing of {@link WMSLayer} with {@link ParameterFilter}s set
 * <p>
 * Supports: {@link RegexParameterFilter}, {@link StringParameterFilter},
 * {@link FloatParameterFilter} and {@link IntegerParameterFilter} (float/int must have threshold
 * defined to detect correct filter class)
 * </p>
 * 
 * @author ez
 *
 */
public class WMSLayerDeserializer implements JsonDeserializer<GsonWMSLayer> {

    private static Log log = LogFactory.getLog(org.geowebcache.config.WMSLayerDeserializer.class);

    private static final String PARAMETER_FILTERS = "parameterFilters";

    /**
     * internal GSON without specific deserializer
     */
    private final static Gson GSON;

    static {
        // handle null..
        GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    }

    @Override
    public GsonWMSLayer deserialize(final JsonElement json, final Type typeOfT,
            final JsonDeserializationContext context) throws JsonParseException {
        try {
            final JsonObject jsonObject = json.getAsJsonObject();

            // Remove parameter filters before parse
            JsonElement parameters = jsonObject.remove(PARAMETER_FILTERS);

            // parse normally without parameterFilters
            GsonWMSLayer item = GSON.fromJson(jsonObject, GsonWMSLayer.class);

            // handle parameter filters
            if (parameters != null && !parameters.isJsonNull() && parameters.isJsonArray()) {
                List<ParameterFilter> parameterFilters = new ArrayList<ParameterFilter>();

                // loop array and parse as *ParameterFilter
                JsonArray parametersArray = parameters.getAsJsonArray();
                for (int i = 0; i < parametersArray.size(); i++) {
                    ParameterFilter filter = getFilter(parametersArray.get(i).getAsJsonObject());

                    if (filter != null) {
                        parameterFilters.add(filter);
                    }
                }

                // set to item
                item.setParameterFilters(parameterFilters);
            }

            return item;
        } catch (JsonParseException e) {
            log.error(
                    String.format("Failed to deserialize into GsonWMSLayer: json='%s' message='%s'",
                            json, e.getMessage()));

            throw e;
        }
    }

    private ParameterFilter getFilter(JsonObject paramFilter) {
        ParameterFilter filter = null;
        if (paramFilter.has("regex")) {
            // regex filter
            filter = JSONUtils.parseObject(paramFilter, RegexParameterFilter.class);
            
            // needs to reset regex to recompile Pattern
            ((RegexParameterFilter) filter).setRegex(((RegexParameterFilter) filter).getRegex());
        } else if (paramFilter.has("threshold")) {
            // int or float filter
            if (isInteger(paramFilter.getAsJsonPrimitive("threshold").getAsFloat())) {
                filter = JSONUtils.parseObject(paramFilter, IntegerParameterFilter.class);
            } else {
                filter = JSONUtils.parseObject(paramFilter, FloatParameterFilter.class);
            }
        } else if (paramFilter.has("values")) {
            // string filter
            filter = JSONUtils.parseObject(paramFilter, StringParameterFilter.class);
        }
        return filter;
    }

    private boolean isInteger(float f) {
        return f % 1 == 0;
    }
}
