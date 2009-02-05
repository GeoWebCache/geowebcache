package org.geowebcache.util.wms;

import java.lang.reflect.Constructor;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ExtentHandlerMap {

    private Map<String, String> extents;
    public static final String defaultExtentClass = "org.geowebcache.util.wms.NumericExtentHandler";

    public ExtentHandlerMap() {

    }

    public ExtentHandler getHandler(String units) {
        String className = defaultExtentClass;
        if (units != null) {
            className = getExtents().get(units);
        }
        ExtentHandler handler = null;
        try {
            Class clazz = Class.forName(className);
            Class cTypes[] = {};
            Constructor<? extends ExtentHandler> constructor = clazz.getConstructor(cTypes);
            Object cParams[] = {};
            handler =  constructor.newInstance(cParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return handler;
    }

    public Map<String, String> getExtents() {
        return extents;
    }

    public void setExtents(Map<String, String> extents) {
        this.extents = extents;
    }

}
