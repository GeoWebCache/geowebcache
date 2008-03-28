package org.geowebcache.service;

import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

public class Service {
    
    private String pathName = null;

    public Service(String pathName){
        this.pathName = pathName;
    }
    
    /**
     * Whether this service can handle the given request
     * 
     * @return
     */
    public boolean handlesRequest(HttpServletRequest request) {
        return request.getPathInfo().equalsIgnoreCase(pathName);
    }
    
    public String getPathName(){
        return new String(pathName);
    }
    
    public String getLayerIdentifier(HttpServletRequest request) {
        throw new RuntimeException(
                "Service for " + pathName  + " needs to override "
                +"getLayerIdentifier(HttpSerlvetRequest)" );
    }
    
    protected String getLayersParameter(HttpServletRequest request) {
        String layerId = request.getParameter("layers");
        
        if(layerId == null) {
            layerId = request.getParameter("LAYERS");
            
            if(layerId == null) {
                Enumeration enume = request.getParameterNames();
                while(enume.hasMoreElements()) {
                    String enumKey = (String) enume.nextElement();
                    if(enumKey.equalsIgnoreCase("layers")) {
                        layerId = request.getParameter(enumKey);
                    }
                }
            }
        }
        
        
        return layerId;
    }
    
    
    public boolean equals(Object obj) {
        if (obj instanceof Service) {
            Service other = (Service) obj;
            if(other.pathName != null 
                    && other.pathName.equalsIgnoreCase(pathName)) {
                return true;
            }
        }
        
        return false;
    }
    
    public int hashCode() {
        return pathName.hashCode();
    }
    
}
