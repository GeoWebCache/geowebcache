package org.geowebcache.config;

import java.util.List;

import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.layer.wms.WMSLayer;

/**
 * Provides {@link #setParameterFilters(List)} to be used during parsing
 * 
 * @author ez
 *
 */
public class GsonWMSLayer extends WMSLayer {

    public GsonWMSLayer() {
        super();
    }

    public void setParameterFilters(List<ParameterFilter> parameterFilters) {
        this.parameterFilters = parameterFilters;
    }

}
