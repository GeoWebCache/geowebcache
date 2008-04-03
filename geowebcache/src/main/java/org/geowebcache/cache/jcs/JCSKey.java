/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.cache.jcs;

import java.io.Serializable;

import org.geowebcache.cache.CacheKey;
import org.geowebcache.layer.SRS;

public class JCSKey implements Serializable, CacheKey {
    private String prefix;

    private int x;

    private int y;

    private int z;
    
    private String srsStr;

    private String format;

    public JCSKey() {
    }

    private JCSKey(String prefix, int x, int y, int z, SRS srs, String format) {
        this.prefix = prefix;
    	this.x = x;
        this.y = y;
        this.z = z;
        this.srsStr = srs.toString();
        this.format = format;
    }

    public void init() {
    	// Do nothing
    }

    public JCSKey createKey(String prefix, int x, int y, int z, SRS srs, String format) {
        return new JCSKey(prefix, x, y, z, srs, format);
    }

    public int getType() {
        return KEY_SERIALIZABLE_OBJECT;
    }

}
