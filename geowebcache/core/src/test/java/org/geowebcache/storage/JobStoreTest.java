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

import java.io.File;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import junit.framework.TestCase;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.SRS;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.storage.JobLogObject.LOG_LEVEL;
import org.geowebcache.storage.jdbc.jobstore.JDBCJobBackend;

public class JobStoreTest extends TestCase {
    public static final String TEST_DB_NAME = "gwcTestJobStore";
    
    public void testJob() throws Exception {
        JobObject jo = new JobObject();
        JobObject jo2 = new JobObject();
        
        try {
            JobStore js = setup();

            prepJobObject(jo);
            
            js.put(jo);
            
            jo2.setJobId(jo.getJobId());
            assertTrue(js.get(jo2));

        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }
        assertEquals(jo.getBounds(), jo2.getBounds());
        assertEquals(jo.getFailedTileCount(), jo2.getFailedTileCount());
        assertEquals(jo.getFormat(), jo2.getFormat());
        assertEquals(jo.getGridSetId(), jo2.getGridSetId());
        assertEquals(jo.getJobType(), jo2.getJobType());
        assertEquals(jo.getLayerName(), jo2.getLayerName());
        assertEquals(jo.getMaxThroughput(), jo2.getMaxThroughput());
        assertEquals(jo.getParameters(), jo2.getParameters());
        assertEquals(jo.getPriority(), jo2.getPriority());
        assertEquals(jo.isRunOnce(), jo2.isRunOnce());
        assertEquals(jo.getSchedule(), jo2.getSchedule());
        assertEquals(jo.getSrs().getNumber(), jo2.getSrs().getNumber());
        assertEquals(jo.getState(), jo2.getState());
        assertEquals(jo.getThreadCount(), jo2.getThreadCount());
        assertEquals(jo.getThroughput(), jo2.getThroughput());
        assertEquals(jo.getTilesDone(), jo2.getTilesDone());
        assertEquals(jo.getTilesTotal(), jo2.getTilesTotal());
        assertEquals(jo.getTimeFirstStart(), jo2.getTimeFirstStart());
        assertEquals(jo.getTimeLatestStart(), jo2.getTimeLatestStart());
        assertEquals(jo.getTimeRemaining(), jo2.getTimeRemaining());
        assertEquals(jo.getTimeSpent(), jo2.getTimeSpent());
        assertEquals(jo.getZoomStart(), jo2.getZoomStart());
        assertEquals(jo.getZoomStop(), jo2.getZoomStop());
    }

    private void prepJobObject(JobObject jo) {
        jo.setBounds(new BoundingBox(-125.90859375, 28.5617875, -106.7484375, 44.20625));
        jo.setFailedTileCount(20);
        jo.setFormat("image/gif");
        jo.setGridSetId("EPSG:4326");
        jo.setJobType(GWCTask.TYPE.RESEED);
        jo.setLayerName("topp:states");
        jo.setMaxThroughput(3);

        Map<String, String> params = new HashMap<String, String>();
        params.put("TIME", "2010-12-25T00:00:00Z");
        params.put("style", "style1,style2");
        params.put("EXCEPTIONS", "XML");
        
        jo.setParameters(params);
        
        jo.setPriority(GWCTask.PRIORITY.LOWEST);
        jo.setRunOnce(true);
        jo.setSchedule("* * * * *");
        jo.setSrs(SRS.getEPSG4326());
        jo.setState(GWCTask.STATE.READY);
        jo.setThreadCount(4);
        jo.setThroughput(0.5f);
        jo.setTilesDone(123456789);
        jo.setTilesTotal(987654321);
        jo.setTimeFirstStart(new Timestamp(new Date().getTime() - (60*60*24*1000)));
        jo.setTimeLatestStart(new Timestamp(new Date().getTime()));
        jo.setTimeRemaining(60*60*24);
        jo.setTimeSpent(60*60*24*2);
        jo.setZoomStart(8);
        jo.setZoomStop(13);
    }
    
    public void testJobDelete() throws Exception {
        JobObject jo = new JobObject();
        JobObject jo2 = new JobObject();
        JobObject jo3 = new JobObject();
        
        JobLogObject jlo = new JobLogObject();
        
        try {
            JobStore js = setup();

            prepJobObject(jo);
            
            // test foreign record delete
            prepJobLogObject(jlo);
            jo.addLog(jlo);

            js.put(jo);
            
            jo2.setJobId(jo.getJobId());
            assertTrue(js.get(jo2));
            
            // Delete
            assertTrue(js.delete(jo.getJobId()));
            
            // Check
            jo3.setJobId(jo.getJobId());
            assertFalse(js.get(jo3));

            assertTrue(jo3.getState() == GWCTask.STATE.UNSET);
            
        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }

    }

    public void testJobLog() throws Exception {
        JobObject jo = new JobObject();
        JobLogObject jlo = new JobLogObject();
        JobLogObject jlo2;
        
        try {
            JobStore js = setup();

            prepJobObject(jo);
            
            js.put(jo);
            
            prepJobLogObject(jlo);
            
            jo.addLog(jlo);
            
            js.put(jo);
            
            Iterator<JobLogObject> logsIter = js.getLogs(jo.getJobId()).iterator();
            
            assertTrue(logsIter.hasNext());
            jlo2 = logsIter.next();

        } catch (StorageException se) {
            System.out.println(se.getMessage());
            throw se;
        }
        assertEquals(jlo.getJobId(), (jlo2.getJobId()));
        assertEquals(jlo.getJobLogId(), (jlo2.getJobLogId()));
        assertEquals(jlo.getLogSummary(), (jlo2.getLogSummary()));
        assertEquals(jlo.getLogText(), (jlo2.getLogText()));
        assertEquals(jlo.getLogLevel(), (jlo2.getLogLevel()));
        assertEquals(jlo.getLogTime(), (jlo2.getLogTime()));
    }


    private void prepJobLogObject(JobLogObject jlo) {
        jlo.setLogLevel(LOG_LEVEL.WARN);
        jlo.setLogSummary("Summary:æøå");
        jlo.setLogText("Text:æøå");
        jlo.setLogTime(new Timestamp(new Date().getTime()));
        
    }

    public JobStore setup() throws Exception {
        StorageBrokerTest.deleteDb(TEST_DB_NAME);
        
        return new JDBCJobBackend("org.h2.Driver", 
                "jdbc:h2:file:" + StorageBrokerTest.findTempDir() 
                + File.separator +TEST_DB_NAME + ";TRACE_LEVEL_FILE=0",
                "sa",
                "");
    }
}

