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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import java.util.Collection;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class MockExtensionRuleTest {

    @Test
    public void testRestoresPreviousState() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);

        rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean = "THISISTHEBEAN";
                                rule.addBean("foo", bean, String.class);
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate();

        assertThat(GeoWebCacheExtensions.extensions(Object.class), Matchers.equalTo(old));
    }

    @Test
    public void testRestoresPreviousStateOnException() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);
        try {
            rule.apply(
                            new Statement() {

                                @Override
                                public void evaluate() throws Throwable {
                                    String bean = "THISISTHEBEAN";
                                    rule.addBean("foo", bean, String.class);
                                    throw new RuntimeException("TEST EXCEPTION");
                                }
                            },
                            Description.createSuiteDescription("MOCK"))
                    .evaluate();
        } catch (RuntimeException ex) {

        }
        assertThat(GeoWebCacheExtensions.extensions(Object.class), Matchers.equalTo(old));
    }

    @Test
    public void testPropagatesException() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean = "THISISTHEBEAN";
                                rule.addBean("foo", bean, String.class);
                                throw new RuntimeException("TEST EXCEPTION");
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate());
        assertThat(exception.getMessage(), containsString("TEST EXCEPTION"));
    }

    @Test
    public void testAddsNamedBean() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);

        rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean = "THISISTHEBEAN";
                                rule.addBean("foo", bean, String.class);

                                assertThat(GeoWebCacheExtensions.bean("foo"), sameInstance(bean));
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate();

        assertThat(GeoWebCacheExtensions.extensions(Object.class), equalTo(old));
    }

    @Test
    public void testAddsTypedBean() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);

        rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean = "THISISTHEBEAN";
                                rule.addBean("foo", bean, String.class);

                                assertThat(GeoWebCacheExtensions.bean(String.class), sameInstance(bean));
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate();

        assertThat(GeoWebCacheExtensions.extensions(Object.class), equalTo(old));
    }

    @Test
    public void testAddsTypedExtension() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);

        rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean = "THISISTHEBEAN";
                                rule.addBean("foo", bean, String.class);

                                assertThat(
                                        GeoWebCacheExtensions.extensions(String.class), contains(sameInstance(bean)));
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate();

        assertThat(GeoWebCacheExtensions.extensions(Object.class), equalTo(old));
    }

    @Test
    public void testAddsTypedExtensions() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);

        rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean1 = "THISISTHEBEAN";
                                String bean2 = "THISISTHEOTHERBEAN";
                                String bean3 = "THISISNOTTHERBEAN";
                                rule.addBean("foo", bean1, String.class);
                                rule.addBean("bar", bean2, String.class);
                                rule.addBean("baz", bean3, Double.class);

                                assertThat(
                                        GeoWebCacheExtensions.extensions(String.class),
                                        containsInAnyOrder(sameInstance(bean1), sameInstance(bean2)));
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate();

        assertThat(GeoWebCacheExtensions.extensions(Object.class), equalTo(old));
    }

    @Test
    public void testParallelRules() throws Throwable {
        MockExtensionRule rule = new MockExtensionRule();
        MockExtensionRule rule2 = new MockExtensionRule(false);

        Collection<Object> old = GeoWebCacheExtensions.extensions(Object.class);

        rule.apply(
                        new Statement() {

                            @Override
                            public void evaluate() throws Throwable {
                                String bean1 = "THISISTHEBEAN";
                                String bean2 = "THISISTHEOTHERBEAN";
                                rule.addBean("foo", bean1, String.class);
                                rule2.apply(
                                        new Statement() {

                                            @Override
                                            public void evaluate() throws Throwable {
                                                rule2.addBean("foo", bean2, String.class);
                                                assertThat(
                                                        GeoWebCacheExtensions.extensions(String.class),
                                                        contains(sameInstance(bean2)));
                                            }
                                        },
                                        Description.createSuiteDescription("MOCK"));

                                assertThat(
                                        GeoWebCacheExtensions.extensions(String.class), contains(sameInstance(bean1)));
                            }
                        },
                        Description.createSuiteDescription("MOCK"))
                .evaluate();

        assertThat(GeoWebCacheExtensions.extensions(Object.class), equalTo(old));
    }
}
