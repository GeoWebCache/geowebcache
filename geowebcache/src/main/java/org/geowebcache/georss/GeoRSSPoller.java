package org.geowebcache.georss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;
import org.geowebcache.layer.updatesource.UpdateSourceDefinition;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.seed.SeedRestlet;


/**
 * 
 * @author groldan
 * @version $Id: GeoRSSPoller.java 903 2010-01-26 00:25:22Z groldan $
 */
public class GeoRSSPoller {

    private static final Log logger = LogFactory.getLog(GeoRSSPoller.class);

    private TileLayerDispatcher layerDispatcher;
    
    private SeedRestlet seedRestlet;

    private List<PollDef> scheduledPolls;

    private GeoRSSFeedDefinition runningPoll;

    private final ScheduledExecutorService schedulingPollExecutorService;

    /**
     * Upon instantiation, spawns out a thread after #{@code startUpDelaySecs} seconds that
     * periodically (at least every each layer's {@link GeoRSSFeedDefinition#getPollInterval() poll
     * interval} polls the layers feed for change sets and if changes are found spawns a reseed
     * process on the tiles affected by the change set.
     * 
     * @param layerDispatcher
     * @param storageBroker
     * @param startUpDelaySecs
     *            seconds to wait before start polling the layers
     */
    @SuppressWarnings("unchecked")
    public GeoRSSPoller(final TileLayerDispatcher layerDispatcher,
            final int startUpDelaySecs) {

        this.layerDispatcher = layerDispatcher;
        this.scheduledPolls = new ArrayList<PollDef>();

        findEnabledPolls();

        if (pollCount() > 0) {
            final int corePoolSize = 1;
            schedulingPollExecutorService = Executors.newScheduledThreadPool(corePoolSize);

            final TimeUnit seconds = TimeUnit.SECONDS;
            for (PollDef poll : this.scheduledPolls) {
                GeoRSSPollTask command;
                command = new GeoRSSPollTask(poll, seedRestlet);
                GeoRSSFeedDefinition pollDef = poll.getPollDef();
                long period = pollDef.getPollInterval();

                logger.info("Scheduling layer " + poll.getLayer().getName()
                        + " to poll the GeoRSS feed " + pollDef.getFeedUrl() + " every "
                        + pollDef.getPollIntervalStr());

                ScheduledFuture<GeoRSSPollTask> scheduledTask;
                scheduledTask = (ScheduledFuture<GeoRSSPollTask>) schedulingPollExecutorService
                        .scheduleAtFixedRate(command, startUpDelaySecs, period, seconds);
            }
            logger.info("Will wait " + startUpDelaySecs + " seconds before launching the "
                    + pollCount() + " GeoRSS polls found");
        } else {
            schedulingPollExecutorService = null;
            logger.info("No enabled GeoRSS feeds found, poller will not run.");
        }
    }
    
    public void setSeedRestlet(SeedRestlet seedRestlet) {
        this.seedRestlet = seedRestlet;
    }

    private void findEnabledPolls() {
        logger.info("Initializing GeoRSS poller...");

        final Iterator<TileLayer> layers = layerDispatcher.getLayers().values().iterator();
        TileLayer layer;
        while (layers.hasNext()) {
            layer = layers.next();
            List<UpdateSourceDefinition> updateSourceList = layer.getUpdateSources();
            
            if(updateSourceList == null) {
                continue;
            }
            
            for (UpdateSourceDefinition usd : layer.getUpdateSources()) {
                if (usd instanceof GeoRSSFeedDefinition) {
                    final GeoRSSFeedDefinition georssDef = (GeoRSSFeedDefinition) usd;

                    final String gridSetId = georssDef.getGridSetId();
                    final GridSubset gridSubset = layer.getGridSubset(gridSetId);
                    if (gridSubset == null) {
                        throw new IllegalStateException("Layer " + layer.getName()
                                + " has no grid subset " + gridSetId
                                + " as configured by its GeoRSS seeding feed " + georssDef);
                    }
                    
                    final String mimeFormat = georssDef.getMimeFormat();
                    try {
                        MimeType.createFromFormat(mimeFormat);
                    } catch (MimeException e) {
                        throw new IllegalStateException("Layer " + layer.getName()
                                + " has an unidentifiable mime type for its GeoRSS feed "
                                + georssDef.getFeedUrl(), e);
                    }

                    if (georssDef.getPollInterval() > 0) {
                        logger.info("Scheduling GeoRSS feed for layer " + layer.getName() + ":"
                                + georssDef);
                        scheduledPolls.add(new PollDef(layer, georssDef));
                    } else {
                        logger.info("Feed disabled for layer " + layer.getName() 
                                + ", ignoring: " + georssDef);
                    }
                }
            }
        }
    }

    /**
     * @return number of scheduled polls
     */
    public int pollCount() {
        return scheduledPolls.size();
    }

    public synchronized GeoRSSFeedDefinition getRunningPoll() {
        return runningPoll;
    }
}
