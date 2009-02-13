/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geowebcache.security.wms;

import org.acegisecurity.Authentication;

/**
 * Data access manager provides the {@link SecureCatalogImpl} with directives on
 * what the specified user can access.
 * @author Andrea Aime - TOPP
 *
 */
public interface DataAccessManager {

    public boolean canAccess(Authentication user, String layerName);
}
