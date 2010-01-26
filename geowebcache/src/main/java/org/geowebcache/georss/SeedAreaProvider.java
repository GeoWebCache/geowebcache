package org.geowebcache.georss;

import org.geowebcache.mime.MimeType;

/**
 * Iterator like interface to provide {@link SeedTask2 SeedTasks} the locations of tiles to seed.
 * 
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id: SeedAreaProvider.java 905 2010-01-26 00:26:44Z groldan $
 */
public interface SeedAreaProvider {

    long getTotalTileCount();

    String getGridsetId();

    MimeType getMimeType();

    /**
     * Returns the next tile location to fetch/seed.
     * <p>
     * The method shall be thread safe in order to account for various seeding tasks accessing the
     * same provider concurrently.
     * </p>
     * <p>
     * In case there is meta tiling involved, the returned location shall account for it.
     * </p>
     * 
     * @return the location of the next tile to fetch/seed (as an {x, y, z} array), or {@code null}
     *         if there are no more tiles to fetch
     */
    long[] nextGridLocation();

}