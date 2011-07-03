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
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage;

/**
 * A taskstore manages all meta information related to tasks
 */
public interface JobStore {
    public boolean delete(long taskId) throws StorageException;

    public boolean get(JobObject obj) throws StorageException;

    public void put(JobObject stObj) throws StorageException;
    
    public long getCount() throws StorageException;
    
    /**
     * Gets all jobs in the system.
     * Not very futureproof yet - some systems could end up with lots and lots of jobs.
     * @return Threadsafe list of all jobs known to the system
     * @throws StorageException
     */
    public Iterable<JobObject> getJobs() throws StorageException;
    
    /**
     * Wipes the entire storage. Should only be invoked during testing.
     * 
     * @throws StorageException
     */
    public void clear() throws StorageException;

    /**
     * Destroy method for Spring
     */
    public void destroy();
}
