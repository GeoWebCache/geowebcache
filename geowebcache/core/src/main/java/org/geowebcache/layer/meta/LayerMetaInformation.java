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
package org.geowebcache.layer.meta;

import java.util.LinkedList;
import java.util.List;

public class LayerMetaInformation {
    String title;

    String description;

    List<String> keywords;

    List<ContactInformation> contacts;

    LayerMetaInformation() {
        // default constructor for XStream
    }

    public LayerMetaInformation(
            String title, String description, List<String> keywords, List<ContactInformation> contacts) {
        this.title = title;
        this.description = description;
        this.keywords = keywords;
        this.contacts = contacts;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public synchronized List<String> getKeywords() {
        if (keywords == null) {
            keywords = new LinkedList<>();
        }
        return keywords;
    }

    public synchronized List<ContactInformation> getContacts() {
        if (contacts == null) {
            contacts = new LinkedList<>();
        }
        return contacts;
    }
}
