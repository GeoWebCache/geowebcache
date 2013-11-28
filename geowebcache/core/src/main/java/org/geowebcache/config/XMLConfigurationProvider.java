package org.geowebcache.config;

import com.thoughtworks.xstream.XStream;

/**
 * Extension point for {@link XMLConfiguration} to allow decoupled modules to contribute to the
 * configuration set up in order to extend the {@code geowebcache.xml} contents with new constructs.
 * 
 * @author Gabriel Roldan
 * 
 */
public interface XMLConfigurationProvider {

    /**
     * Allows an extension to enhance the {@link XMLConfiguration} XStream persister to handle new
     * contructs.
     * 
     * @param xs
     *            the XStream persister configured with the default elements from
     *            {@link XMLConfiguration}
     * @return the modified (possibly the same) XStream persister with the extension point's added
     *         xml mappings
     */
    XStream getConfiguredXStream(XStream xs);
}
