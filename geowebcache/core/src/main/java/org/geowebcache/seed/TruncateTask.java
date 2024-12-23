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
 * @author Arne Kepp / The Open Planning Project 2008
 */
package org.geowebcache.seed;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileRange;

class TruncateTask extends GWCTask {
    private static Logger log = Logging.getLogger(TruncateTask.class.getName());

    private final TileRange tr;

    private final TileLayer tl;

    private final boolean doFilterUpdate;

    private final StorageBroker storageBroker;

    public TruncateTask(StorageBroker sb, TileRange tr, TileLayer tl, boolean doFilterUpdate) {
        this.storageBroker = sb;
        this.tr = tr;
        this.tl = tl;
        this.doFilterUpdate = doFilterUpdate;

        super.parsedType = GWCTask.TYPE.TRUNCATE;
        super.layerName = tl.getName();
    }

    @Override
    protected void doActionInternal() throws GeoWebCacheException, InterruptedException {
        super.state = GWCTask.STATE.RUNNING;
        checkInterrupted();
        try {
            storageBroker.delete(tr);
        } catch (Exception e) {
            super.state = GWCTask.STATE.DEAD;
            log.log(Level.SEVERE, "During truncate request", e);
        }

        checkInterrupted();
        if (doFilterUpdate) {
            runFilterUpdates();
        }

        if (super.state != GWCTask.STATE.DEAD) {
            super.state = GWCTask.STATE.DONE;
            log.log(Level.FINE, "Completed truncate request.");
        }
    }

    /** Updates any request filters */
    private void runFilterUpdates() {
        // We will assume that all filters that can be updated should be updated
        List<RequestFilter> reqFilters = tl.getRequestFilters();
        if (reqFilters != null && !reqFilters.isEmpty()) {
            Iterator<RequestFilter> iter = reqFilters.iterator();
            while (iter.hasNext()) {
                RequestFilter reqFilter = iter.next();
                if (reqFilter.update(tl, tr.getGridSetId())) {
                    log.log(Level.FINE, "Updated request filter " + reqFilter.getName());
                } else {
                    log.log(Level.FINE, "Request filter " + reqFilter.getName() + " returned false on update.");
                }
            }
        }
    }

    @Override
    protected void dispose() {
        // do nothing
    }
}
