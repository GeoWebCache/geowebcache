package org.geowebcache;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class MockWepAppContextRule extends MockExtensionRule {

    public MockWepAppContextRule() {
        
    }
    
    ServletContext servletContext;
    
    Map<String, String> servletInitParameters;

    @Override
    public WebApplicationContext getMockContext() {

        return (WebApplicationContext) super.getMockContext();
    }
    
    public ApplicationContextProvider getContextProvider() {
        ApplicationContextProvider provider = new ApplicationContextProvider();
        provider.setApplicationContext(getMockContext());
        return provider;
    }

    @Override
    protected ApplicationContext makeContext() throws IllegalArgumentException {
        servletInitParameters = new HashMap<>();
        servletContext =  (ServletContext) Proxy.newProxyInstance(
                MockExtensionRule.class.getClassLoader(),
                new Class[] {ServletContext.class},
                new ServletContextInvocationHandler());
        return (ApplicationContext) Proxy.newProxyInstance(
            MockExtensionRule.class.getClassLoader(),
            new Class[] {ApplicationContext.class, WebApplicationContext.class},
            new WebContextInvocationHandler());
    }

    class WebContextInvocationHandler extends ContextInvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if(method.getName().equals("getServletContext")) {
                return servletContext;
            } else {
                return super.invoke(proxy, method, args);
            }
        }
    }
    
    class ServletContextInvocationHandler implements InvocationHandler {
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if(method.getName().equals("getInitParameter")) {
                return servletInitParameters.get(args[0]);
            }
            throw new UnsupportedOperationException();
        }
        
    }

}
