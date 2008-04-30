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
package org.geowebcache.cache;

import org.geowebcache.layer.SRS;

/**
 * Interface to define cache keys.
 * 
 * @author Arne Kepp, The Open Planning Project, (C) 2007
 */
public interface CacheKey {
	static int KEY_FILE_PATH = 1;

	static int KEY_SERIALIZABLE_OBJECT = 2;

	public void init();

	public Object createKey(String prefix, int x, int y, int z, SRS srs,
			String format);

	int getType();
}
