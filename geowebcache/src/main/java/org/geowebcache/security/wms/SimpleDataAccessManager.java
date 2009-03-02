/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geowebcache.security.wms;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.geotools.util.logging.Logging;

/**
 * Simple implementation of {@link DataAccessManager}, loads simple access
 * rules from a properties file or Properties object. The format of each
 * property is:<br>
 * <code>layer=[role]*</code><br>
 * where:
 * <ul>
 * <li> layer: a layer name (feature type, coverage, layer group) or * to
 * indicate any layer </li>
 * <li> role: a user role</li>
 * </ul>
 * 
 * If no {@link Properties} is provided, one will be looked upon in
 * <code>GEOWEBCACHE_DATA_DIR/layers.properties</code>, and the class will
 * keep up to date vs changes in the file.
 * 
 * Note: This is a simplified variant of GeoServers layer handling mechanism.
 * Since GeoWebCache is unaware of workspaces and the operation will always be
 * READ then these two properties of the rules are omitted.
 * 
 * For further reading on Per layer security in GeoServer please see the &lt;a
 * href=&quot;http://geoserver.org/display/GEOS/GSIP+19+-+Per+layer+security&quot;/&gt;per
 * layer security proposal&lt;/a&gt; on the &lt;a
 * href=&quot;www.geoserver.org&quot;&gt;GeoServer&lt;/a&gt; web site.
 * 
 * @author Per Engstrom
 */
public class SimpleDataAccessManager implements DataAccessManager {
    static final Logger LOGGER = Logging.getLogger(DataAccessManager.class);
    
    Properties layerProperies;

    PropertyFileWatcher watcher;

    File layers;
    
    String layersPropertiesDirectory;
    
    Set<String> EVERYBODY = Collections.singleton("*");

    SimpleDataAccessManager(Properties layerProperties) {
        this.layerProperies = layerProperties;
    }
    
    SimpleDataAccessManager() {
    }
    
    public boolean canAccess(Authentication user, String layerName) {
        checkPropertyFile();
        String allLayersRoleKey = (String) layerProperies.get("*");
        // Can user access all layers?
        if (allLayersRoleKey != null && testRoleKey(allLayersRoleKey, user)) {
            return true;
        } else {
            String layerRoleKey = (String) layerProperies.get(layerName);
            // Can user see specific layer?
            if (layerRoleKey != null) {
                return testRoleKey(layerRoleKey, user);
            } else {
                return false;
            }
        }
    }
    
    private boolean testRoleKey(String roleKey, Authentication user) {
        Set<String> layersRoles = parseRoles(roleKey);
        if (EVERYBODY.equals(layersRoles)) {
            return true;
        }
        if (user != null) {
            GrantedAuthority[] authorities = user.getAuthorities();
            for (GrantedAuthority auth : authorities) {
                if (layersRoles.contains(auth.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks the property file is up to date, eventually rebuilds the tree
     */
    private void checkPropertyFile() {
        try {
            if (layerProperies == null) {
                File security = new File(layersPropertiesDirectory);
                LOGGER.log(Level.INFO, "Looking for layers.properties in " + security.getAbsolutePath());

                // no security folder, let's work against an empty properties then
                if (security == null || !security.exists()) {
                    LOGGER.log(Level.WARNING, "Did not find directory for layers.properties, creating default.");
                    setLayerProperties(new Properties());
                } else {
                    // no security config, let's work against an empty properties then
                    layers = new File(security, "layers.properties");
                    if (!layers.exists()) {
                        LOGGER.log(Level.WARNING, "Did not find layers.properties, creating default!");
                        setLayerProperties(new Properties());
                    } else {
                        // ok, something is there, let's load it
                        LOGGER.log(Level.INFO, "Found layers.properties, setting layer properties.");
                        watcher = new PropertyFileWatcher(layers);
                        setLayerProperties(watcher.getProperties());
                    }
                }
            } else if(watcher != null && watcher.isStale()) { 
                setLayerProperties(watcher.getProperties());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to reload data access rules from " + layers
                    + ", keeping old rules", e);
        }
    }
    
    private Set<String> parseRoles(String roleCsv) {
        // regexp: treat extra spaces as separators, ignore extra commas
        // "a,,b, ,, c" --> ["a","b","c"]
        String[] rolesArray = roleCsv.split("[\\s,]+");
        Set<String> roles = new HashSet<String>(rolesArray.length);
        roles.addAll(Arrays.asList(rolesArray));

        // if any of the roles is * we just remove all of the others
        for (String role : roles) {
            if ("*".equals(role))
                return Collections.singleton("*");
        }

        return roles;
    }

    public void setLayersPropertiesDirectory(String layersPropertiesFile) {
        this.layersPropertiesDirectory = layersPropertiesFile;
    }
    
    private void setLayerProperties(Properties properties) {
        this.layerProperies = new Properties();
        layerProperies.put("*", "*");
        layerProperies.putAll(properties);
    }

}
