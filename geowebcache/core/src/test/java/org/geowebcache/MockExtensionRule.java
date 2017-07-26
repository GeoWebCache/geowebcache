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
 * @author Kevin Smith, Boundless, 2017
 * 
 */

package org.geowebcache;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.junit.rules.ExternalResource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * JUnit rule to temporarily register a mock extension with GeoWebCacheExtensions
 *
 */
public class MockExtensionRule extends ExternalResource {
    
    /*
     * If doing multithreaded testing, we don't want two tests fiddling with the extensions at the 
     * same time
     */
    static Lock lock = new ReentrantLock();
    
    ApplicationContext mockContext;
    ApplicationContext oldContext;
    Map<String, Object> beans = new HashMap<>();
    Map<Class<?>, Collection<String>> types = new HashMap<>();
    
    @Override
    protected void before() throws Throwable {
        mockContext = (ApplicationContext) Proxy.newProxyInstance(
                MockExtensionRule.class.getClassLoader(),
                new Class[] {ApplicationContext.class},
                new ContextInvocationHandler());
        lock.lock();
        oldContext = GeoWebCacheExtensions.context;
        GeoWebCacheExtensions.extensionsCache.clear();
        GeoWebCacheExtensions.context = mockContext;
    }
    
    /**
     * Register a mock extension bean
     * @param name
     * @param bean
     * @param classes
     */
    public void addBean(String name, Object bean, Class<?>... classes) {
        for(Class<?> clazz: classes) {
            Collection<String> c = types.getOrDefault(clazz, new ArrayList<>());
            c.add(name);
            types.put(clazz,  c);
        }
        beans.put(name,  bean);
        GeoWebCacheExtensions.extensionsCache.clear();
    }
    
    @Override
    protected void after() {
        try {
            GeoWebCacheExtensions.extensionsCache.clear();
            GeoWebCacheExtensions.context = oldContext;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get the mock ApplicationContext that contains the mock extension beans
     * @return
     */
    public ApplicationContext getMockContext() {
        if(Objects.isNull(mockContext)) {
            throw new IllegalStateException("Mock context only available while rule is active");
        }
        return mockContext;
    }
    
    class ContextInvocationHandler implements InvocationHandler {
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if(method.getName().equals("getBean")) {
                if(method.getParameterTypes()[0].equals(String.class)) {
                    return Optional.ofNullable(beans.get(args[0]))
                        .orElseThrow(()->new NoSuchBeanDefinitionException((String) args[0]));
                }
            } else if(method.getName().equals("getBeansOfType")) {
                return types.getOrDefault(args[0], Collections.emptySet()).stream()
                    .collect(Collectors.toMap(name->name, beans::get));
            }
            throw new UnsupportedOperationException();
        }
        
    }
}
