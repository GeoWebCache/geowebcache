package org.geowebcache.config;

/**
 * An XMLConfigurationProvider that can restrict itself to certain contexts
 * 
 * @author Kevin Smith, OpenGeo
 *
 */
public interface ContextualConfigurationProvider extends
        XMLConfigurationProvider {

    /**
     * The contexts a provider can apply to
     *
     */
    static public enum Context {
        /**
         * Persistence to storage
         */
        PERSIST,
        
        /**
         * Over the REST API
         */
        REST
    }
    
    /**
     * Does the provider apply to the given context
     * @param ctxt The context
     * @return true of applicable, false otherwise
     */
    public boolean appliesTo(Context ctxt);
}
