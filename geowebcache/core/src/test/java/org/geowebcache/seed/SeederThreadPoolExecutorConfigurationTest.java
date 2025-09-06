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
 */
package org.geowebcache.seed;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test class to verify that the SeederThreadPoolExecutor configuration properly reads environment variables for core
 * pool size and maximum pool size.
 */
public class SeederThreadPoolExecutorConfigurationTest {

    /** Allows to set environment variables for each individual test */
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private ApplicationContext applicationContext;

    @After
    public void tearDown() {
        if (applicationContext != null) {
            ((ClassPathXmlApplicationContext) applicationContext).close();
        }
    }

    /** Test that default values are used when environment variables are not set. */
    @Test
    public void testDefaultValuesWhenEnvironmentVariablesNotSet() {
        // Clear any existing environment variables
        environmentVariables.clear("GWC_SEEDER_CORE_POOL_SIZE");
        environmentVariables.clear("GWC_SEEDER_MAX_POOL_SIZE");

        // Load the Spring context with test configuration
        applicationContext = new ClassPathXmlApplicationContext("seeder-thread-pool-test-context.xml");

        // Get the seeder thread pool executor bean
        SeederThreadPoolExecutor executor =
                applicationContext.getBean("gwcSeederThreadPoolExec", SeederThreadPoolExecutor.class);

        // Verify default values are used
        assertEquals("Core pool size should default to 16", 16, executor.getCorePoolSize());
        assertEquals("Maximum pool size should default to 32", 32, executor.getMaximumPoolSize());

        // Clean up
        executor.shutdown();
    }

    /** Test that custom environment variable values are properly loaded. */
    @Test
    public void testCustomEnvironmentVariableValues() {
        // Set custom environment variables
        environmentVariables.set("GWC_SEEDER_CORE_POOL_SIZE", "8");
        environmentVariables.set("GWC_SEEDER_MAX_POOL_SIZE", "16");

        // Load the Spring context with test configuration
        applicationContext = new ClassPathXmlApplicationContext("seeder-thread-pool-test-context.xml");

        // Get the seeder thread pool executor bean
        SeederThreadPoolExecutor executor =
                applicationContext.getBean("gwcSeederThreadPoolExec", SeederThreadPoolExecutor.class);

        // Verify custom values are used
        assertEquals("Core pool size should be set to 8", 8, executor.getCorePoolSize());
        assertEquals("Maximum pool size should be set to 16", 16, executor.getMaximumPoolSize());

        // Clean up
        executor.shutdown();
    }

    /** Test that only one environment variable is set (partial configuration). */
    @Test
    public void testPartialEnvironmentVariableConfiguration() {
        // Set only core pool size environment variable
        environmentVariables.set("GWC_SEEDER_CORE_POOL_SIZE", "12");
        environmentVariables.clear("GWC_SEEDER_MAX_POOL_SIZE");

        // Load the Spring context with test configuration
        applicationContext = new ClassPathXmlApplicationContext("seeder-thread-pool-test-context.xml");

        // Get the seeder thread pool executor bean
        SeederThreadPoolExecutor executor =
                applicationContext.getBean("gwcSeederThreadPoolExec", SeederThreadPoolExecutor.class);

        // Verify mixed configuration
        assertEquals("Core pool size should be set to 12", 12, executor.getCorePoolSize());
        assertEquals("Maximum pool size should default to 32", 32, executor.getMaximumPoolSize());

        // Clean up
        executor.shutdown();
    }

    /** Test that invalid environment variable values fall back to defaults. */
    @Test
    public void testInvalidEnvironmentVariableValues() {
        // Set invalid environment variables
        environmentVariables.set("GWC_SEEDER_CORE_POOL_SIZE", "invalid");
        environmentVariables.set("GWC_SEEDER_MAX_POOL_SIZE", "not_a_number");

        // Load the Spring context with test configuration
        applicationContext = new ClassPathXmlApplicationContext("seeder-thread-pool-test-context.xml");

        // Get the seeder thread pool executor bean
        SeederThreadPoolExecutor executor =
                applicationContext.getBean("gwcSeederThreadPoolExec", SeederThreadPoolExecutor.class);

        // Verify default values are used when invalid values are provided
        assertEquals("Core pool size should default to 16 when invalid value provided", 16, executor.getCorePoolSize());
        assertEquals(
                "Maximum pool size should default to 32 when invalid value provided",
                32,
                executor.getMaximumPoolSize());

        // Clean up
        executor.shutdown();
    }

    /** Test that the SeederThreadPoolExecutor can be created with various configurations. */
    @Test
    public void testSeederThreadPoolExecutorCreation() {
        // Test with different pool sizes
        SeederThreadPoolExecutor executor1 = new SeederThreadPoolExecutor(4, 8);
        assertEquals("Core pool size should be 4", 4, executor1.getCorePoolSize());
        assertEquals("Maximum pool size should be 8", 8, executor1.getMaximumPoolSize());
        executor1.shutdown();

        SeederThreadPoolExecutor executor2 = new SeederThreadPoolExecutor(1, 1);
        assertEquals("Core pool size should be 1", 1, executor2.getCorePoolSize());
        assertEquals("Maximum pool size should be 1", 1, executor2.getMaximumPoolSize());
        executor2.shutdown();

        SeederThreadPoolExecutor executor3 = new SeederThreadPoolExecutor(64, 128);
        assertEquals("Core pool size should be 64", 64, executor3.getCorePoolSize());
        assertEquals("Maximum pool size should be 128", 128, executor3.getMaximumPoolSize());
        executor3.shutdown();
    }
}
