package org.geowebcache.filter.parameters;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

public abstract class CaseNormalizingParameterFilter extends ParameterFilter {
    
    private CaseNormalizer normalize;
    
    public CaseNormalizingParameterFilter() {
        super();
    }
    
    public CaseNormalizingParameterFilter(String key, String defaultValue) {
        super(key, defaultValue);
    }
    
    public abstract List<String> getValues();
    
    public CaseNormalizingParameterFilter(String key) {
        super(key);
    }
    
    public CaseNormalizer getNormalize() {
        if(normalize!=null) {
            return normalize;
        } else {
            return new CaseNormalizer();
        }
    }
    
    public void setNormalize(CaseNormalizer normalize) {
        this.normalize = normalize;
    }
    
    @Override
    public  @Nullable List<String> getLegalValues() {
        List<String> values = getValues();
        if(values == null) {
            return null;
        } else {
            return Lists.transform(values, getNormalize());
        }
    }
    
}