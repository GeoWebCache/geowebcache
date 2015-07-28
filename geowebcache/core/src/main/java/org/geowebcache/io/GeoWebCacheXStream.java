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

package org.geowebcache.io;

import org.geowebcache.GeoWebCacheExtensions;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.ConverterRegistry;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;

/**
 * XStream subclass 
 * @author Kevin Smith, Boundless
 *
 */
public class GeoWebCacheXStream extends XStream {
    
    public GeoWebCacheXStream() {
        super();
        secure();
    }
    
    public GeoWebCacheXStream(HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(hierarchicalStreamDriver);
        secure();
    }
    
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver,
            ClassLoaderReference classLoaderReference, Mapper mapper,
            ConverterLookup converterLookup, ConverterRegistry converterRegistry) {
        super(reflectionProvider, driver, classLoaderReference, mapper,
                converterLookup, converterRegistry);
        secure();
    }
    
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver,
            ClassLoaderReference classLoaderReference, Mapper mapper) {
        super(reflectionProvider, driver, classLoaderReference, mapper);
        secure();
    }
    
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver,
            ClassLoaderReference classLoaderReference) {
        super(reflectionProvider, driver, classLoaderReference);
        secure();
    }
    
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(reflectionProvider, hierarchicalStreamDriver);
        secure();
    }
    
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider) {
        super(reflectionProvider);
        secure();
    }
    
    @Deprecated
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver, ClassLoader classLoader,
            Mapper mapper, ConverterLookup converterLookup,
            ConverterRegistry converterRegistry) {
        super(reflectionProvider, driver, classLoader, mapper, converterLookup,
                converterRegistry);
        secure();
    }
    
    @Deprecated
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver, ClassLoader classLoader,
            Mapper mapper) {
        super(reflectionProvider, driver, classLoader, mapper);
        secure();
    }
    
    @Deprecated
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver, ClassLoader classLoader) {
        super(reflectionProvider, driver, classLoader);
        secure();
    }
    
    @Deprecated
    public GeoWebCacheXStream(ReflectionProvider reflectionProvider,
            Mapper mapper, HierarchicalStreamDriver driver) {
        super(reflectionProvider, mapper, driver);
        secure();
    }
    
    /**
     * Add security permission for a type hierarchy.
     * 
     * @param type the base type to allow
     * @since 1.4.7
     */
     public void allowTypeHierarchies(Class<?>... types){
        for(Class<?> type: types) {
            this.allowTypeHierarchy(type);
        }
    }
    
    private void secure() {
        // Require classes to be on whitelist
        addPermission(NoTypePermission.NONE);
        
        // Allow primitive types
        addPermission(new PrimitiveTypePermission());
        
        // Common non-primitives
        allowTypes(new Class[] { 
            java.lang.String.class,
            
            java.util.Date.class, 
            
            java.sql.Date.class, 
            java.sql.Timestamp.class, 
            java.sql.Time.class,
        });
        
        // Common collections
        allowTypes(new Class[] { 
            java.util.TreeSet.class, 
            java.util.SortedSet.class, 
            java.util.Set.class, 
            java.util.HashSet.class,
            java.util.List.class, 
            java.util.ArrayList.class, 
            java.util.Map.class, 
            java.util.HashMap.class,
            
            java.util.concurrent.CopyOnWriteArrayList.class, 
            java.util.concurrent.ConcurrentHashMap.class, 
        });
        
        String whitelistProp = GeoWebCacheExtensions.getProperty("GEOWEBCACHE_XSTREAM_WHITELIST");
        if(whitelistProp != null) {
            String[] wildcards = whitelistProp.split("\\s+|(\\s*;\\s*)");
            this.allowTypesByWildcard(wildcards);
        }
    }
    
}
