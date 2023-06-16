/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache.diskquota.jdbc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;

public class OnlineTestRule extends ExternalResource {

    static final Logger LOG = Logging.getLogger(OnlineTestRule.class.getName());

    /** System property set to totally disable any online tests */
    public static final String ONLINE_TEST_PROFILE = "onlineTestProfile";
    /**
     * The key in the test fixture property file used to set the behaviour of the online test if
     * {@link #connect()} fails.
     */
    public static final String SKIP_ON_FAILURE_KEY = "skip.on.failure";
    /** The default value used for {@link #SKIP_ON_FAILURE_KEY} if it is not present. */
    public static final String SKIP_ON_FAILURE_DEFAULT = "true";
    /**
     * A static map which tracks which fixtures are offline. This prevents continually trying to run
     * a test when an external resource is offline.
     */
    protected static Map<String, Boolean> online = new HashMap<>();
    /**
     * A static map which tracks which fixture files can not be found. This prevents continually
     * looking up the file and reporting it not found to the user.
     */
    protected static Map<String, Boolean> found = new HashMap<>();
    /** The test fixture, {@code null} if the fixture is not available. */
    protected Properties fixture;
    /**
     * Flag that determines effect of exceptions in connect/disconnect. If true (the default),
     * exceptions in connect cause the the test to be disabled, and exceptions in disconnect to be
     * ignored. If false, exceptions will be rethrown, and cause the test to fail.
     */
    protected boolean skipOnFailure = true;

    protected final String fixtureId;

    /**
     * Check whether the fixture is available. This method also loads the configuration if present,
     * and tests the connection using {@link #isOnline()}.
     *
     * @return true if fixture is available for use
     */
    void checkAvailable() {
        configureFixture();
        Assume.assumeThat("No fixture available", fixture, notNullValue());

        // do an online/offline check
        Boolean available = OnlineTestRule.online.get(fixtureId);
        Assume.assumeThat("Fixture offline", available, anyOf(nullValue(), equalTo(true)));

        try {
            available = isOnline();
        } catch (Throwable t) {
            LOG.info(
                    "Skipping " + fixtureId + " tests, resources not available: " + t.getMessage());
            LOG.log(Level.WARNING, t.getMessage(), t);
            available = Boolean.FALSE;
        }
        OnlineTestRule.online.put(fixtureId, available);
    }

    /** Load fixture configuration. Create example if absent. */
    private void configureFixture() {
        if (fixture == null) {
            if (fixtureId == null) {
                return; // not available (turn test off)
            }
            try {
                // load the fixture
                File base = FixtureUtilities.getFixtureDirectory();
                // look for a "profile", these can be used to group related fixtures
                String profile = System.getProperty(OnlineTestRule.ONLINE_TEST_PROFILE);
                if (profile != null && !"".equals(profile)) {
                    base = new File(base, profile);
                }
                File fixtureFile = FixtureUtilities.getFixtureFile(base, fixtureId);
                Boolean exists = OnlineTestRule.found.get(fixtureFile.getCanonicalPath());
                if (exists == null || exists.booleanValue()) {
                    if (fixtureFile.exists()) {
                        fixture = FixtureUtilities.loadProperties(fixtureFile);
                        OnlineTestRule.found.put(fixtureFile.getCanonicalPath(), true);
                    } else {
                        // no fixture file, if no profile was specified write out a template
                        // fixture using the offline fixture properties
                        if (profile == null) {
                            Properties exampleFixture = createExampleFixture();
                            if (exampleFixture != null) {
                                File exFixtureFile =
                                        new File(fixtureFile.getAbsolutePath() + ".example");
                                if (!exFixtureFile.exists()) {
                                    createExampleFixture(exFixtureFile, exampleFixture);
                                }
                            }
                        }
                        OnlineTestRule.found.put(fixtureFile.getCanonicalPath(), false);
                    }
                }
                if (fixture == null) {
                    fixture = createOfflineFixture();
                }
                if (fixture == null && exists == null) {
                    // only report if exists == null since it means that this is
                    // the first time trying to load the fixture
                    FixtureUtilities.printSkipNotice(fixtureId, fixtureFile);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    void createExampleFixture(File exFixtureFile, Properties exampleFixture) {
        try {
            exFixtureFile.getParentFile().mkdirs();
            exFixtureFile.createNewFile();

            try (FileOutputStream fout = new FileOutputStream(exFixtureFile)) {

                exampleFixture.store(
                        fout,
                        "This is an example fixture. Update the "
                                + "values and remove the .example suffix to enable the test");
                fout.flush();
            }
            LOG.info("Wrote example fixture file to " + exFixtureFile);
        } catch (IOException ioe) {
            LOG.info("Unable to write out example fixture " + exFixtureFile);
            LOG.log(Level.WARNING, ioe.getMessage(), ioe);
        }
    }

    /**
     * Loads the test fixture for the test case.
     *
     * <p>The fixture id is obtained via {@link #fixtureId}.
     */
    @Override
    protected final void before() throws Exception {
        checkAvailable();
        setUpInternal();

        skipOnFailure =
                Boolean.parseBoolean(
                        fixture.getProperty(
                                OnlineTestRule.SKIP_ON_FAILURE_KEY,
                                OnlineTestRule.SKIP_ON_FAILURE_DEFAULT));
        // call the setUp template method
        try {
            connect();
        } catch (Exception e) {
            if (skipOnFailure) {
                fixture = null;
                throw new AssumptionViolatedException(
                        "Failure during connection to fixture " + fixtureId, e);
            } else {
                // do not swallow the exception
                throw e;
            }
        }
    }

    /** Method for subclasses to latch onto the setup phase. */
    protected void setUpInternal() throws Exception {}

    /** Tear down method for test, calls through to {@link #disconnect()} if the test is active. */
    @Override
    protected final void after() {
        try {
            tearDownInternal();
        } catch (Exception e) {
            throw new AssertionError("Exception during tear down of fixture " + fixtureId, e);
        } finally {
            if (fixture != null) {
                try {
                    disconnect();
                } catch (Exception e) {
                    if (!skipOnFailure) {
                        throw new AssertionError(
                                "Exception during disconnect of fixture " + fixtureId, e);
                    }
                }
            }
        }
    }

    /** Method for subclasses to latch onto the teardown phase. */
    protected void tearDownInternal() throws Exception {}

    /**
     * Tests if external resources needed to run the tests are online.
     *
     * <p>This method can return false to indicate the online resources are not up, or can simply
     * throw an exception.
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
     * <p>Subclasses should do all initialization / connection here. In the event of a connection
     * not being available, this method should throw an exception to abort the test case.
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
     * Allows tests to create an offline fixture in cases where the user has not specified an
     * explicit fixture for the test.
     *
     * <p>Note, that this should method should on be implemented if the test case is created of
     * creating a fixture which relies soley on embedded or offline resources. It should not
     * reference any external or online resources as it prevents the user from running offline.
     */
    protected Properties createOfflineFixture() {
        return null;
    }

    /**
     * Allows test to create a sample fixture for users.
     *
     * <p>If this method returns a value the first time a fixture is looked up and not found this
     * method will be called to create a fixture file with teh same id, but suffixed with .template.
     */
    protected Properties createExampleFixture() {
        return null;
    }

    public OnlineTestRule(String fixtureId) {
        this.fixtureId = fixtureId;
    }

    public Properties getFixture() {
        return fixture;
    }
}
