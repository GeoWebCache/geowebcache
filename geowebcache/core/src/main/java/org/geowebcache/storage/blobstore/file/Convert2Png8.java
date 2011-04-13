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
 * @author Rob Stekelenburg / IDgis 2011 
 *
 */
package org.geowebcache.storage.blobstore.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.PoolableProcessFactory;

/**
 * Converts a png 24 bits file to png 8 bits.<br>
 * Uses pngquant for dithering and keeping proper alpha values.<br>
 * The file name and extension will stay the same after the conversion.
 * @see http://www.libpng.org/pub/png/apps/pngquant.html
 *
 */
public class Convert2Png8 implements FileConverter{
    private static Log log = LogFactory.getLog(org.geowebcache.storage.blobstore.file.Convert2Png8.class);

    private static GenericObjectPool processPool = 
        new GenericObjectPool(new PoolableProcessFactory(), 32, GenericObjectPool.WHEN_EXHAUSTED_FAIL, 1000);
    
    
    public void convert(File fh) throws StorageException {
        Process prc = null;
        boolean exceptionOcurred = false;
        try {
            String commandline = "pngquant -force 256 \"" + fh.getAbsolutePath() + "\"";
            prc = getShellProcess();
            PrintWriter  prcout = new PrintWriter (prc.getOutputStream());
            BufferedReader prcin = new BufferedReader(new InputStreamReader(prc.getInputStream()));
            prcout.println(commandline);
            prcout.flush();
            String cmdout;
            if (log.isDebugEnabled()) {
                log.debug("commandline = " + commandline);
                if (prcin.ready()) {
                    while ((cmdout = prcin.readLine()) != null && cmdout.length() != 0) {
                        log.debug("cmdout = " + cmdout);
                    }                   
                }
            }
            String line = null;
            BufferedReader error = new BufferedReader(
                    new InputStreamReader(prc.getErrorStream()));
            if (error.ready()) {
                log.debug("\nprocess error: \n");
                while ((line = error.readLine()) != null) {
                    log.error(line);
                }                   
            }
            prcout.println("echo hello");
            prcout.flush();
            long startTime = System.currentTimeMillis();
            while (!prcin.ready() && (System.currentTimeMillis() - startTime) < 5000) {
                log.debug("waiting for process inputstream to become ready");
                Thread.sleep(100);
            }
            if (prcin.ready()) {
                while ((cmdout = prcin.readLine()) != null && !cmdout.equals("hello") && (System.currentTimeMillis() - startTime) < 5000) {
                    log.debug("waiting for finish of conversion to png8");
                    Thread.sleep(100);
                }
            }
            String path = fh.getAbsolutePath();
            String base = path.substring(0, path.lastIndexOf("."));
            File png8file1 = new File(path + "-fs8.png");
            File png8file2 = new File(base + "-fs8.png");
            File png8file = null;
            if (png8file1.exists()) {
                png8file = png8file1;
            } else if (png8file2.exists()) {
                png8file = png8file2;
            } 
            if (png8file == null) {
                throw new StorageException("output png8 file not found: " + base + ".png-fs8.png / -fs.png");
            }
            File bak = new File(fh.getAbsolutePath() + ".bak");
            if (bak.exists()) {
                boolean delResult = bak.delete();
                if (!delResult) {
                    throw new IOException(" Delete of " + bak.getAbsolutePath() + " failed" );                                  
                }
            }
            boolean renResult = fh.renameTo(bak);
            if (!renResult) {
                throw new IOException(" Rename from " + fh.getAbsolutePath() + " to " + bak.getAbsolutePath() + " failed" );                
            }
            renResult = png8file.renameTo(fh);
            if (!renResult) {
                throw new IOException(" Rename from " + png8file.getAbsolutePath() + " to " + fh.getAbsolutePath() + " failed" );               
            }
            bak.delete();
        }
        catch (Exception e) {
            exceptionOcurred = true;
            e.printStackTrace();
            throw new StorageException(e.getClass().getName() + " occured, message: " + e.getMessage());
        }
        finally {
            if (prc != null) {
                try {
                    if (exceptionOcurred) {
                        processPool.invalidateObject(prc);
                    } else {
                        processPool.returnObject(prc);
                    }
                } catch (Exception e) {}
            }
        }
    }

    private synchronized Process getShellProcess() throws Exception {
        return (Process)processPool.borrowObject();
    }

    public void destroy() {
        processPool.clear();
    }
    
    public static void main(String[] args) {
        if (args != null && args.length > 0 && !args[0].equals("")){
            File file = new File (args[0]);
            FileConverter convert2png8 = new Convert2Png8();
            System.out.println("Converting "+file.getAbsolutePath());
            try {
                convert2png8.convert(file);
                System.out.println("Converted!");
            } catch (StorageException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Use: Convert2Png8 <FileName>");
        }
    }

}
