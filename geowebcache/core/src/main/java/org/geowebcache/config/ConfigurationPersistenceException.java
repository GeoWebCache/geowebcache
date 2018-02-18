package org.geowebcache.config;

public class ConfigurationPersistenceException extends RuntimeException {

    public ConfigurationPersistenceException() { }

    public ConfigurationPersistenceException(String message) {
        super(message);
    }

    public ConfigurationPersistenceException(Throwable cause) {
        super(cause);
    }

    public ConfigurationPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

}
