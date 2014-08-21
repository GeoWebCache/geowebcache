package org.geowebcache.io;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.spi.ImageWriterSpi;

import com.sun.media.imageioimpl.plugins.gif.GIFImageWriterSpi;

/**
 * This class is used for handling the registration of the various {@link ImageReaderWriterSpi} instances inside GWC. This class must be instantiated
 * before the creation of the various {@link ImageDecoder} and {@link ImageDecoder} implementations in order to check if the installed plugins are
 * registered. If they are not already registered, it scans for all the plugins and register each of them. Optionally, after the registration the
 * {@link ImageIOInitializer} is able to deregister the reader/writer plugins as defined by the user inside the application context contained in the
 * file geowebcache-wmsservice-context.xml .
 * 
 * @author Nicola Lagomarsini geosolutions
 * 
 */
public class ImageIOInitializer {
    /** Logger used for logging the exceptions inside the ImageIOInitializer class */
    private static final Logger LOGGER = Logger.getLogger(ImageIOInitializer.class.toString());

    /** {@link IIORegistry} instance used for registering/deregistering ImageIO plugins */
    private IIORegistry registry;

    /** List of the Service Provider Interfaces (SPIs) objects associated to the plugin readers/writers */
    private List<String> excludedSpis;

    /** SIngleton instance of the {@link ImageIOInitializer} class */
    private static ImageIOInitializer instance;

    /**
     * Static initializer for the {@link ImageIOInitializer} class.
     * 
     * @param excludedSpis List of the Spis to exclude.
     * @return
     */
    public static synchronized ImageIOInitializer getInstance(ArrayList<String> excludedSpis) {
        if (instance == null) {
            instance = new ImageIOInitializer(excludedSpis);
        }
        return instance;
    }

    private ImageIOInitializer(List<String> excludedSpis) {
        // Setting of the excluded spis
        this.excludedSpis = excludedSpis;
        // Check if the plugins for Writers and Readers have been already registered
        registry = IIORegistry.getDefaultInstance();
        ImageWriterSpi provider = registry.getServiceProviderByClass(GIFImageWriterSpi.class);
        // If it is not registered you have to scan for the requested plugins
        if (provider == null) {
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.log(Level.FINER, "Something must be registered");
            }
            ImageIO.scanForPlugins();
            removeSpis();
        }
    }

    /**
     * Private method for deregistering the Spi class defined by the user
     */
    private void removeSpis() {
        // Cycle on the defined SPIs for deregistering them
        if (excludedSpis != null && !excludedSpis.isEmpty()) {
            for (String spi : excludedSpis) {
                try {
                    // Reflection: Get the class associated to the spi name
                    Class<?> clazz = Class.forName(spi);
                    // Check if the Service Provider has been registered
                    Object provider = registry.getServiceProviderByClass(clazz);
                    // If so, the Provider is deregistered
                    if (provider != null) {
                        registry.deregisterServiceProvider(provider);
                    }
                } catch (ClassNotFoundException e) {
                    // If the Class is not present, then the Provider cannot have been
                    // registered
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINER, e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * @return the {@link IIORegistry} instance used by the initializer
     */
    public synchronized IIORegistry getRegistry() {
        if(registry == null){
            registry = IIORegistry.getDefaultInstance();
        }
        return registry;
    }

    /**
     * @return The list of the SPI names to deregister
     */
    public List<String> getExcludedSpis() {
        return excludedSpis;
    }

    /**
     * Sets the list of the SPI names to deregister
     * 
     * @param excludedSpis
     */
    public void setExcludedSpis(List<String> excludedSpis) {
        this.excludedSpis = excludedSpis;
    }
}
