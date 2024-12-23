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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import static java.util.Arrays.asList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.geowebcache.GeoWebCacheException;

public class SRS implements Comparable<SRS>, Serializable {

    private static Map<Integer, SRS> list = new ConcurrentHashMap<>();

    private static final SRS EPSG4326 = new SRS(4326);

    /**
     * The EPSG says EPSG:3857 is the identifier for web mercator. ArcGIS 10 says either of EPSG:102113 or EPSG:102100
     * identifies web mercator. The "community" first defined it as EPSG:900913.
     */
    private static final SRS EPSG3857 = new SRS(3857, new ArrayList<>(asList(900913, 102113, 102100)));

    /**
     * The EPSG says EPSG:3857 is the identifier for web mercator. ArcGIS 10 says either of EPSG:102113 or EPSG:102100
     * identifies web mercator. The "community" first defined it as EPSG:900913.
     */
    private static final SRS EPSG900913 = new SRS(900913, new ArrayList<>(asList(3857, 102113, 102100)));

    private int number;

    private transient List<Integer> aliases;

    private SRS() {
        // default constructor for XStream
    }

    private SRS(int epsgNumber) {
        this(epsgNumber, null);
    }

    private SRS(int epsgNumber, List<Integer> aliases) {
        this.number = epsgNumber;
        this.aliases = aliases;
        readResolve();
    }

    // called by XStream for custom initialization
    private Object readResolve() {
        if (!list.containsKey(Integer.valueOf(number))) {
            list.put(number, this);
        }
        return this;
    }

    /**
     * Returns an SRS object for the given epsg code.
     *
     * <p>If an SRS for this code already exists, it's returned. Otherwise a registered SRS is looked up that has an
     * alias defined for the given code, and if found the alias is returned. If no SRS is registered nor an alias is
     * found, a new SRS for this code is registered and returned.
     */
    public static SRS getSRS(final int epsgCode) {
        final Integer code = Integer.valueOf(epsgCode);
        final SRS existing = list.get(code);

        if (existing != null) {
            return existing;
        }
        for (SRS candidate : new ArrayList<>(list.values())) {
            if (candidate.aliases != null && candidate.aliases.contains(Integer.valueOf(code))) {
                list.put(code, candidate);
                return candidate;
            }
        }

        return new SRS(epsgCode);
    }

    public static SRS getSRS(String epsgStr) throws GeoWebCacheException {
        final String crsAuthPrefix = "EPSG:";
        if (epsgStr.substring(0, 5).equalsIgnoreCase(crsAuthPrefix)) {
            int epsgNumber = Integer.parseInt(epsgStr.substring(5, epsgStr.length()));
            return getSRS(epsgNumber);
        } else {
            throw new GeoWebCacheException("Can't parse " + epsgStr + " as SRS string.");
        }
    }

    /**
     * Two SRS are equal if they have the same code or any of them have the other one as an alias.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SRS)) {
            return false;
        }
        boolean equivalent = false;
        SRS other = (SRS) obj;
        if (other.number == this.number) {
            equivalent = true;
        } else if (this.aliases != null && other.aliases != null) {
            equivalent = this.aliases.contains(other.number) || other.aliases.contains(this.number);
        }
        return equivalent;
    }

    public int getNumber() {
        return number;
    }

    @Override
    public int hashCode() {
        return number;
    }

    @Override
    public String toString() {
        return "EPSG:" + Integer.toString(number);
    }

    public static SRS getEPSG4326() {
        return EPSG4326;
    }

    public static SRS getEPSG3857() {
        return EPSG3857;
    }

    public static SRS getEPSG900913() {
        return EPSG900913;
    }

    /** @see java.lang.Comparable#compareTo(java.lang.Object) */
    @Override
    public int compareTo(SRS other) {
        return number - other.number;
    }
}
