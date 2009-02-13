/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geowebcache.security.wms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/**
 * A simple class to support reloadable property files. Watches last modified
 * date on the specified file, and allows to read a Properties out of it.
 *
 * @author Andrea Aime
 *
 */
public class PropertyFileWatcher {
    File file;
    private long lastModified = Long.MIN_VALUE;
    private long lastCheck;
    private boolean stale;

    public PropertyFileWatcher(File file) {
        this.file = file;
    }

    public Properties getProperties() throws IOException {
        Properties p = new Properties();

        if (file.exists()) {
            InputStream is = null;

            try {
                is = new FileInputStream(file);
                p.load(is);
                lastModified = file.lastModified();
                lastCheck = System.currentTimeMillis();
                stale = false;
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        return p;
    }

    public boolean isStale() {
        long now = System.currentTimeMillis();
        if((now - lastCheck) > 1000) {
            lastCheck = now;
            stale = file.exists() && (file.lastModified() > lastModified);
        }
        return stale;
    }
}
