/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache;

import jakarta.servlet.ServletContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class MockWepAppContextRule extends MockExtensionRule {

    public MockWepAppContextRule() {
        super();
    }

    public MockWepAppContextRule(boolean staticContext) {
        super(staticContext);
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
        servletContext = (ServletContext) Proxy.newProxyInstance(
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
            if (method.getName().equals("getServletContext")) {
                return servletContext;
            } else {
                return super.invoke(proxy, method, args);
            }
        }
    }

    class ServletContextInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getInitParameter")) {
                return servletInitParameters.get(args[0]);
            }
            throw new UnsupportedOperationException();
        }
    }

    public static void subContext(org.junit.runners.model.Statement statement) throws Exception {
        MockWepAppContextRule subRule = new MockWepAppContextRule(false);

        subRule.apply(statement, org.junit.runner.Description.EMPTY);
    }
}
