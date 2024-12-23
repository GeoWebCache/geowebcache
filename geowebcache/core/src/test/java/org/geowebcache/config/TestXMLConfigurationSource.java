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
package org.geowebcache.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.grid.GridSetBroker;

/**
 * Encapsulates the setup of an XMLConfiguration test unit for different sets of tests
 *
 * @author smithkm
 */
public class TestXMLConfigurationSource {

    protected boolean failNextRead;
    protected boolean failNextWrite;

    public XMLConfiguration create(MockWepAppContextRule extensions, File configDir) throws Exception {
        GridSetBroker gridSetBroker = new GridSetBroker();
        gridSetBroker.setApplicationContext(extensions.getMockContext());
        DefaultGridsets defaultGridsets = new DefaultGridsets(true, true);
        extensions.addBean(
                "DefaultGridSets",
                defaultGridsets,
                DefaultGridsets.class,
                GridSetConfiguration.class,
                BaseConfiguration.class);

        ConfigurationResourceProvider configProvider =
                new XMLFileResourceProvider(
                        XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME,
                        extensions.getMockContext(),
                        configDir.getAbsolutePath(),
                        null) {

                    @Override
                    public InputStream in() throws IOException {
                        if (failNextRead) {
                            failNextRead = false;
                            throw new IOException("Test failure on read");
                        }
                        return super.in();
                    }

                    @Override
                    public OutputStream out() throws IOException {
                        if (failNextWrite) {
                            failNextWrite = false;
                            throw new IOException("Test failure on write");
                        }
                        return super.out();
                    }
                };
        XMLConfiguration config = new XMLConfiguration(extensions.getContextProvider(), configProvider);
        extensions.addBean("XMLConfiguration", config, XMLConfiguration.class.getInterfaces());
        config.setGridSetBroker(gridSetBroker);
        config.afterPropertiesSet();
        defaultGridsets.afterPropertiesSet();
        gridSetBroker.afterPropertiesSet();
        return config;
    }

    /** Should the next read from persistence fail */
    public boolean isFailNextRead() {
        return failNextRead;
    }

    /** Should the next read from persistence fail */
    public void setFailNextRead(boolean failNextRead) {
        this.failNextRead = failNextRead;
    }

    /** Should the next read from persistence fail */
    public boolean isFailNextWrite() {
        return failNextWrite;
    }

    /** Should the next read from persistence fail */
    public void setFailNextWrite(boolean failNextWrite) {
        this.failNextWrite = failNextWrite;
    }
}
