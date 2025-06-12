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
import java.util.ArrayList;
import java.util.List;

public class ServiceInformation implements Serializable {

    @Serial
    private static final long serialVersionUID = -4466383328619274445L;

    private String title;

    private String description;

    private List<String> keywords = new ArrayList<>();

    private ServiceProvider serviceProvider;

    private String fees;

    private String accessConstraints;

    private String providerName;

    private String providerSite;

    // if TRUE the implementation of this service should strictly comply with CITE tests
    private boolean citeCompliant;

    /** @return the title */
    public String getTitle() {
        return title;
    }

    /** @param title the title to set */
    public void setTitle(String title) {
        this.title = title;
    }

    /** @return the description */
    public String getDescription() {
        return description;
    }

    /** @param description the description to set */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return the keywords */
    public List<String> getKeywords() {
        return keywords;
    }

    /** @return the fees */
    public String getFees() {
        return fees;
    }

    /** @param fees the fees to set */
    public void setFees(String fees) {
        this.fees = fees;
    }

    /** @return the accessConstraints */
    public String getAccessConstraints() {
        return accessConstraints;
    }

    /** @param accessConstraints the accessConstraints to set */
    public void setAccessConstraints(String accessConstraints) {
        this.accessConstraints = accessConstraints;
    }

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

    /** @return the serviceProvider */
    public ServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    /** @param serviceProvider the serviceProvider to set */
    public void setServiceProvider(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    public boolean isCiteCompliant() {
        return citeCompliant;
    }

    public void setCiteCompliant(boolean citeCompliant) {
        this.citeCompliant = citeCompliant;
    }
}
