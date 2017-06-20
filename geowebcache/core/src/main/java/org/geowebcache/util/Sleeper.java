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
 * @author Kevin Smith, Boundless, Copyright 2017
 */

package org.geowebcache.util;

/**
 * Functional interface for a sleep method. Used to allow mocking during unit
 * tests.
 * @see Thread.sleep
 */
@FunctionalInterface
public interface Sleeper {
    /**
     * @see Thread.sleep
     * @param millis
     * @throws InterruptedException
     */
    public void sleep(long millis) throws InterruptedException;
}