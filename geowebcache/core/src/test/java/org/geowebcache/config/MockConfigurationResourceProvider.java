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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public class MockConfigurationResourceProvider implements ConfigurationResourceProvider {

    Supplier<InputStream> inputSupplier;

    public MockConfigurationResourceProvider(Supplier<InputStream> inputSupplier) {
        this.inputSupplier = inputSupplier;
    }

    @Override
    public InputStream in() throws IOException {
        return inputSupplier.get();
    }

    @Override
    public OutputStream out() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void backup() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getId() {
        return "MockConfigurationProvider";
    }

    @Override
    public String getLocation() throws IOException {
        return "";
    }

    @Override
    public void setTemplate(String templateLocation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasInput() {
        return true;
    }

    @Override
    public boolean hasOutput() {
        return false;
    }
}
