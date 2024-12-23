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
 * @author Arne Kepp, OpenGeo, Copyright 2010
 */
package org.geowebcache.layer.meta;

public class ContactInformation {
    String personName;

    String companyName;

    String url;

    String email;

    String phone;

    String facsimile;

    public String getPersonName() {
        return personName;
    }

    public String companyName() {
        return companyName;
    }

    public String getUrl() {
        return url;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getFacsimile() {
        return facsimile;
    }
}
