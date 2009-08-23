package org.geowebcache.layer.meta;

import java.util.List;

public class LayerMetaInformation {
    String title;
    
    String description;
    
    List<String> keywords;
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }

}
