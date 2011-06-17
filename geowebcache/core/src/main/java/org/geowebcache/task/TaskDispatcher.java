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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.GWCTaskBits;
import org.geowebcache.seed.TileBreeder;
import org.springframework.beans.factory.DisposableBean;

/**
 * Serves tasks from the metadata.
 */
public class TaskDispatcher implements DisposableBean {

    private static Log log = LogFactory.getLog(org.geowebcache.task.TaskDispatcher.class);

    private TileBreeder seeder;
    
    public TaskDispatcher() {
        reInit();
    }

    /**
     * Returns the tasked based on the {@code taskIdent} parameter.
     * 
     * @throws GeoWebCacheException
     *             if no such task exists
     */
    public GWCTask getTask(final String taskIdent) throws GeoWebCacheException {

        // TODO JIMG getTask - response to rest request

        throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                + " Unknown task " + taskIdent + ". Check the logfiles,"
                + " it may not have loaded properly.");
    }

    /***
     * 
     * 
     */
    public void reInit() {
        // TODO JIMG This is called after something would change the list of tasks so they get reinitialised
        // may not affect tasks like layers because this doesn't just reflect the config file
        initialize();
    }

    public int getTaskCount() {
        // TODO task count
        int count = 0; // init count

        // ???

        return count; // profit
    }

    public Set<String> getTaskIdents() {
        Set<String> taskIdents = new HashSet<String>();

        // TODO JIMG - list of task idents

        return taskIdents;
    }

    /**
     * Returns a list of all the tasks.
     * 
     * @return a list view of this task dispatcher's internal tasks
     */
    public Iterable<GWCTask> getTaskList() {
        ArrayList<GWCTask> tasks = new ArrayList<GWCTask>();

        // TODO JIMG get list of tasks
        Iterator<Entry<Long, GWCTask>> iter = seeder.getRunningTasksIterator();
        
        while(iter.hasNext()) {
            Entry<Long, GWCTask> entry = iter.next();
            tasks.add(entry.getValue());
        }

        return tasks;
    }

    private void initialize() {
        log.debug("initializing task dispatcher...");
        // TODO JIMG - probably need to init access to the metadata or wherever tasks are listed
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        //
    }

    public boolean remove(final String taskIdent) {
        // TODO JIMG remove a task
        return false;
    }

    public void setTileBreeder(TileBreeder seeder) {
        this.seeder = seeder;
    }
}
