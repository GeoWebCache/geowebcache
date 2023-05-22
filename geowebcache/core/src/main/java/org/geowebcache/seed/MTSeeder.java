/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Marius Suta / The Open Planning Project 2008
 */
package org.geowebcache.seed;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;

class MTSeeder implements Callable<GWCTask> {
    private static Logger log = Logging.getLogger(MTSeeder.class.getName());

    protected GWCTask task = null;

    public MTSeeder(GWCTask task) {
        this.task = task;
    }

    @Override
    public GWCTask call() {
        try {
            task.doAction();
        } catch (GeoWebCacheException gwce) {
            log.log(Level.SEVERE, gwce.getMessage(), gwce);
        } catch (InterruptedException e) {
            log.info(task.getType() + " task #" + task.getTaskId() + " has been interrupted");
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, task.getType() + " task #" + task.getTaskId() + " failed", e);
        }
        return task;
    }
}
