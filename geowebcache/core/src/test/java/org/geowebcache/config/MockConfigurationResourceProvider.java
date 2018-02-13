package org.geowebcache.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public class MockConfigurationResourceProvider implements ConfigurationResourceProvider {

    Supplier <InputStream> inputSupplier;
    
    public MockConfigurationResourceProvider(Supplier <InputStream> inputSupplier) {
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
