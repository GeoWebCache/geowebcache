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
package org.geowebcache.cachekey;

import java.io.Serializable;

public class JCSKey implements Serializable, CacheKey {
    private int prefix;

    private int x;

    private int y;

    private int z;

    private String format;

    public JCSKey() {
    }

    private JCSKey(int x, int y, int z, String format) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.format = format;
    }

    public void init(String prefix) {
        this.prefix = prefix.hashCode();
    }

    public JCSKey createKey(int x, int y, int z, String format) {
        return new JCSKey(x, y, z, format);
    }

    public int getType() {
        return KEY_SERIALIZABLE_OBJECT;
    }

}
