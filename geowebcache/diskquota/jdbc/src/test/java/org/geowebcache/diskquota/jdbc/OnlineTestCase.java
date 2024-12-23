/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2005-2010, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */

package org.geowebcache.diskquota.jdbc;

import java.util.Properties;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;

/**
 * This class comes from GeoTools
 *
 * Test support for test cases that require an "online" resource, such as an
 * external server or database.
 * <p>
 * Online tests work off of a "fixture". A fixture is a properties file which
 * defines connection parameters for some remote service. Each online test case
 * must define the id of the fixture is uses with {@link #getFixtureId()}.
 * </p>
 * <p>
 * Fixtures are stored under the users home directory, under the "<code>.geotools</code>"
 * directory. Dots "." in the fixture id represent a subdirectory path under this
 * configuration file directory. For example, a fixture id <code>a.b.foo</code> would be
 * resolved to <code>.geotools/a/b/foo.properties<code>.
 * </p>
 * <p>
 * In the event that a fixture does not exist, the test case is
 * aborted.
 * </p>
 * <p>
 * Online tests connect to remote / online resources. Test cases should do all
 * connection / disconnection in the {@link #connect} and {@link #disconnect()}
 * methods.
 * </p>
 *
 * <p>
 * The default behaviour of this class is that if {@link #connect()} throws an exception, the test
 * suite is disabled, causing each test to pass without being run. In addition, exceptions thrown by
 * {@link #disconnect()} are ignored. This behaviour allows tests to be robust against transient
 * outages of online resources, but also means that local software failures in {@link #connect()} or
 * {@link #disconnect()} will be silent.
 * </p>
 *
 * <p>
 * To have exceptions thrown by {@link #connect()} and {@link #disconnect()} cause tests to fail,
 * set <code>skip.on.failure=false</code> in the fixture property file. This restores the
 * traditional behaviour of unit tests, that is, that exceptions cause unit tests to fail.
 * </p>
 *
 * @since 2.4
 *
 *
 * @source $URL$
 * @version $Id$
 * @author Justin Deoliveira, The Open Planning Project
 * @author Ben Caradoc-Davies, CSIRO Earth Science and Resource Engineering
 */
public abstract class OnlineTestCase {

    private final class CompatibilityRule extends OnlineTestRule {
        private CompatibilityRule(String fixtureId) {
            super(fixtureId);
        }

        @Override
        protected void setUpInternal() throws Exception {
            OnlineTestCase.this.setUpInternal();
        }

        @Override
        protected void tearDownInternal() throws Exception {
            OnlineTestCase.this.tearDownInternal();
        }

        @Override
        protected boolean isOnline() throws Exception {
            return OnlineTestCase.this.isOnline();
        }

        @Override
        protected void connect() throws Exception {
            OnlineTestCase.this.connect();
        }

        @Override
        protected void disconnect() throws Exception {
            OnlineTestCase.this.disconnect();
        }

        @Override
        protected Properties createOfflineFixture() {
            return OnlineTestCase.this.createOfflineFixture();
        }

        @Override
        protected Properties createExampleFixture() {
            return OnlineTestCase.this.createExampleFixture();
        }

        @Override
        void checkAvailable() {
            Assume.assumeTrue(OnlineTestCase.this.checkAvailable());
        }

        void superCheckAvailable() {
            super.checkAvailable();
        }
    }

    @Rule
    public OnlineTestRule fixtureRule = new CompatibilityRule(getFixtureId());

    protected Properties fixture;

    /** Method for subclasses to latch onto the setup phase. */
    protected void setUpInternal() throws Exception {}

    /** Method for subclasses to latch onto the teardown phase. */
    protected void tearDownInternal() throws Exception {}

    /**
     * Tests if external resources needed to run the tests are online.
     *
     * <p>This method can return false to indicate the online resources are not up, or can simply throw an exception.
     *
     * @return True if external resources are online, otherwise false.
     * @throws Exception Any errors that occur determining if online resources are available.
     */
    protected boolean isOnline() throws Exception {
        return true;
    }

    /**
     * Connection method, called from {@link #setUp()}.
     *
     * <p>Subclasses should do all initialization / connection here. In the event of a connection not being available,
     * this method should throw an exception to abort the test case.
     *
     * @throws Exception if the connection failed.
     */
    protected void connect() throws Exception {}

    /**
     * Disconnection method, called from {@link #tearDown()}.
     *
     * <p>Subclasses should do all cleanup here.
     *
     * @throws Exception if the disconnection failed.
     */
    protected void disconnect() throws Exception {}

    /**
     * Allows tests to create an offline fixture in cases where the user has not specified an explicit fixture for the
     * test.
     *
     * <p>Note, that this should method should on be implemented if the test case is created of creating a fixture which
     * relies soley on embedded or offline resources. It should not reference any external or online resources as it
     * prevents the user from running offline.
     */
    protected Properties createOfflineFixture() {
        return null;
    }

    /**
     * Allows test to create a sample fixture for users.
     *
     * <p>If this method returns a value the first time a fixture is looked up and not found this method will be called
     * to create a fixture file with teh same id, but suffixed with .template.
     */
    protected Properties createExampleFixture() {
        return null;
    }

    /**
     * The fixture id for the test case.
     *
     * <p>This name is hierarchical, similar to a java package name. Example: {@code "postgis.demo_bc"}.
     *
     * @return The fixture id.
     */
    protected abstract String getFixtureId();

    @Before
    public void setFixture() throws Exception {
        this.fixture = fixtureRule.getFixture();
    }

    boolean checkAvailable() {
        try {
            ((CompatibilityRule) fixtureRule).superCheckAvailable();
            return true;
        } catch (AssumptionViolatedException ex) {
            return false;
        }
    }
}
