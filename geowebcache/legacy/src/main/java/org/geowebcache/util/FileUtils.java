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
 *  
 */
package org.geowebcache.util;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileUtils {
    private static Log log = LogFactory.getLog(org.geowebcache.util.FileUtils.class);
    
    static public boolean rmFileCacheDir(File path, ExtensionFileLister extfl) {
        if (path.exists()) {
            File[] files = null;
            
            if(extfl != null) {
                files = path.listFiles(extfl);
            } else {
                files = path.listFiles();
            }
            
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    rmFileCacheDir(files[i], extfl);
                } else {
                    if(! files[i].delete()) {
                        log.error("Unable to delete " + files[i].getAbsolutePath());
                    }
                }
            }
        }
        return (path.delete());
    }
}

class ExtensionFileLister implements FilenameFilter {
    private String prefix;

    private String extension;

    public ExtensionFileLister(String prefix, String extension) {
        this.prefix = prefix;
        this.extension = extension;
    }

    public boolean accept(File directory, String filename) {
        if (prefix != null && ! filename.startsWith(prefix)) {
            return false;
        }
        
        if(extension != null && ! filename.endsWith('.' + extension)) {
            return false;
        }
        
        return true;
    }
}