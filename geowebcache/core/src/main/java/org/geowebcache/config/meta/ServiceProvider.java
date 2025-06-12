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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.config.meta;

import java.io.Serial;
import java.io.Serializable;

public class ServiceProvider implements Serializable {

    @Serial
    private static final long serialVersionUID = 6318645093724139893L;

    private String providerName;

    private String providerSite;

    private ServiceContact serviceContact;

    /** @return the providerName */
    public String getProviderName() {
        return providerName;
    }

    /** @param providerName the providerName to set */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /** @return the providerSite */
    public String getProviderSite() {
        return providerSite;
    }

    /** @param providerSite the providerSite to set */
    public void setProviderSite(String providerSite) {
        this.providerSite = providerSite;
    }

    /** @return the serviceContact */
    public ServiceContact getServiceContact() {
        return serviceContact;
    }

    /** @param serviceContact the serviceContact to set */
    public void setServiceContact(ServiceContact serviceContact) {
        this.serviceContact = serviceContact;
    }
}
