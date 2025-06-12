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

public class ServiceContact implements Serializable {

    @Serial
    private static final long serialVersionUID = 2137310743590604314L;

    private String individualName;

    private String positionName;

    private String addressType;

    private String addressStreet;

    private String addressCity;

    private String addressAdministrativeArea;

    private String addressPostalCode;

    private String addressCountry;

    private String phoneNumber;

    private String faxNumber;

    private String addressEmail;

    /** @return the individualName */
    public String getIndividualName() {
        return individualName;
    }

    /** @param individualName the individualName to set */
    public void setIndividualName(String individualName) {
        this.individualName = individualName;
    }

    /** @return the positionName */
    public String getPositionName() {
        return positionName;
    }

    /** @param positionName the positionName to set */
    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    /** @return the phoneNumber */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** @param phoneNumber the phoneNumber to set */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /** @return the faxNumber */
    public String getFaxNumber() {
        return faxNumber;
    }

    /** @param faxNumber the faxNumber to set */
    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }

    /** @return the addressStreet */
    public String getAddressStreet() {
        return addressStreet;
    }

    /** @param addressStreet the addressStreet to set */
    public void setAddressStreet(String addressStreet) {
        this.addressStreet = addressStreet;
    }

    /** @return the addressPostalCode */
    public String getAddressPostalCode() {
        return addressPostalCode;
    }

    /** @param addressPostalCode the addressPostalCode to set */
    public void setAddressPostalCode(String addressPostalCode) {
        this.addressPostalCode = addressPostalCode;
    }

    /** @return the addressCity */
    public String getAddressCity() {
        return addressCity;
    }

    /** @param addressCity the addressCity to set */
    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    /** @return the addressAdministrativeArea */
    public String getAddressAdministrativeArea() {
        return addressAdministrativeArea;
    }

    /** @param addressAdministrativeArea the addressAdministrativeArea to set */
    public void setAddressAdministrativeArea(String addressAdministrativeArea) {
        this.addressAdministrativeArea = addressAdministrativeArea;
    }

    /** @return the addressCountry */
    public String getAddressCountry() {
        return addressCountry;
    }

    /** @param addressCountry the addressCountry to set */
    public void setAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
    }

    /** @return the addressEmail */
    public String getAddressEmail() {
        return addressEmail;
    }

    /** @param addressEmail the addressEmail to set */
    public void setAddressEmail(String addressEmail) {
        this.addressEmail = addressEmail;
    }

    /** @return the addressType */
    public String getAddressType() {
        return addressType;
    }

    /** @param addressType the addressType to set */
    public void setAddressType(String addressType) {
        this.addressType = addressType;
    }
}
