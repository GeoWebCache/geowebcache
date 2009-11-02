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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

public class H2Test extends TestCase {
    
    public static long INSERT_COUNT = 1000;
    
    public static String JDBC_STRING = "jdbc:h2:file:/tmp/h2test;TRACE_LEVEL_FILE=0";
    
    public static int ITERATIONS = 3;
    
    public static int THREAD_COUNT = 4;
    
    public static final boolean RUN_H2_TESTS = false;
    
    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(JDBC_STRING,"sa","");
    }
    
    private void doSetup() throws Exception {
        System.out.println("Setting up");
        try {
            Class.forName("org.h2.Driver");
        } catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        }
        
        Connection conn = getConnection();
        
        String dropIdxQuery = "DROP INDEX IF EXISTS IDX_TILES";
        
        String dropQuery = "DROP TABLE IF EXISTS TILES";
        
        String createQuery = 
            "CREATE TABLE TILES ("
            + "TILE_ID BIGINT AUTO_INCREMENT PRIMARY KEY, LAYER_ID BIGINT, "
            + "X BIGINT, Y BIGINT, Z BIGINT, SRS_ID INT, FORMAT_ID BIGINT, "
            + "PARAMETERS_ID BIGINT, BLOB_SIZE INT, "
            + "CREATED BIGINT, ACCESS_LAST BIGINT, ACCESS_COUNT BIGINT"
            +")";
        
        String idxQuery = 
            "CREATE INDEX "
            + "IDX_TILES ON TILES ("
            +"LAYER_ID, X, Y, Z, SRS_ID, FORMAT_ID, PARAMETERS_ID"
            +")";
        
        try {
            Statement st = conn.createStatement();
            st.execute(dropQuery);
            st.close();
            
            st = conn.createStatement();
            st.execute(dropIdxQuery);
            st.close();
            
            st = conn.createStatement();
            st.execute(createQuery);
            st.close();
            
            st = conn.createStatement();
            st.execute(idxQuery);
            st.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        System.out.println("Inserting rows for test.");
        for(int i=0; i<INSERT_COUNT; i++) {
            try {
                Statement st = conn.createStatement();
                st.execute("INSERT INTO TILES (LAYER_ID,X,Y,Z,SRS_ID,FORMAT_ID) "
                        + " VALUES(0,"+i+","+(i%2)+","+(i%3)+",4,5)");
                st.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        conn.close();
        System.out.println("Done setting up.");
    }
    
    private boolean doSelect(long x, long y, long z) throws Exception {        
        String query = "SELECT TILE_ID,BLOB_SIZE,CREATED FROM TILES WHERE " 
                + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND SRS_ID = ? " 
                + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL LIMIT 1 ";
        
        Connection conn = getConnection();
        
        PreparedStatement prep = conn.prepareStatement(query);
        prep.setLong(1, 0);
        prep.setLong(2, x);
        prep.setLong(3, y);
        prep.setLong(4, z);
        prep.setLong(5, 4);
        prep.setLong(6, 5);
        
        ResultSet rs = null;
        
        try {
            rs = prep.executeQuery();

            if (rs.first()) {
                return true;
            } else {
                return false;
            }
        } finally {
            if(rs != null)
                rs.close();
            
            if(prep != null)
                prep.close();
            
            // For single thread:
            // Closing connection: 23 selects per second
            // Without closing connection 520 selects per second 
            //conn.close();
        }
        
    }
    
    private void doBasicTest(String name) throws Exception {
        long start = System.currentTimeMillis();
        for(int i=0; i<INSERT_COUNT; i++) {
            doSelect(i,(i%2),(i%3));
            //if(i % 100 == 0) {
            //    System.out.println(i);
            //}
        }
        long stop = System.currentTimeMillis();
        System.out.println(name + ": Time: " + (stop - start) + "ms for "
                + INSERT_COUNT +" tiles = " 
                + (INSERT_COUNT * 1000)/ (stop - start) + " tiles/s");
        
    }
    
    public void testOpenClose() throws Exception {
        if(! RUN_H2_TESTS)
            return;
        
        System.out.println("\n\n** Open/Close test:");
        try {
            Class.forName("org.h2.Driver");
        } catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        }
        
        //Connection connHolder = getConnection();
        long start = System.currentTimeMillis();
        long cumulativeOpen = 0;
        long cumulativeClose = 0;
        for(int i=0; i<INSERT_COUNT; i++) {
            long startOpen = System.nanoTime(); 
            Connection conn = getConnection();
            cumulativeOpen += System.nanoTime() - startOpen;
            
            long startClose = System.nanoTime();
            conn.close();
            cumulativeClose += System.nanoTime() - startClose;
            
        }
        System.out.println("Open: " + (cumulativeOpen / 1000000));
        System.out.println("Close: " + (cumulativeClose / 1000000));
        
        long stop = System.currentTimeMillis();
        System.out.println("Opening and closing H2 connection: " + (stop - start) + "ms for "
                + INSERT_COUNT + " = " 
                + (INSERT_COUNT * 1000)/ (stop - start) + " /s");
        //connHolder.close();
    }
    
    public void testSingle() throws Exception {
        if(! RUN_H2_TESTS)
            return;
        
        System.out.println("\n\n** Single-threaded test:");
        doSetup();
        
        for(int i=0; i<ITERATIONS; i++) {
            doBasicTest("Single threaded, iteration " + i);
        }
    }
    
    public void testMulti() throws Exception {
        if(! RUN_H2_TESTS)
            return;
        
        System.out.println("\n\n** Multi-threaded test:");
        doSetup();
        
        Thread[] threadAr = new Thread[THREAD_COUNT];
        for(int i=0; i<THREAD_COUNT; i++) {
            threadAr[i] = new H2TestThread("Thread"+i, ITERATIONS);
        }
        for(int i=0; i<THREAD_COUNT; i++) {
            threadAr[i].start();
        }
        for(int i=0; i<THREAD_COUNT; i++) {
            threadAr[i].join();
        }
        long stop = System.currentTimeMillis();
        System.out.println("Done.");
    }
    
    public class H2TestThread extends Thread {
        String fail = null;
        String name = null;
        int iterations;
        
        public H2TestThread(String name, int iterations) {
            this.name = name;
            this.iterations = iterations;
        }
        
        public void run() {
            try {
                for(int i=0;i<iterations; i++) {
                    doBasicTest(name + ", iteration " + i);
                }
            } catch (Exception e) {
                fail = e.getMessage();
            }
        }
    }
}
