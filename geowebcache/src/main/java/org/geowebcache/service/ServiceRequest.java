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
 */
package org.geowebcache.service;

public class ServiceRequest {
    public final static int SERVICE_REQUEST_TILE = 0;

    public final static int SERVICE_REQUEST_DIRECT = 1;

    String layerIdent = null;

    String[] data = null;

    int requestType = SERVICE_REQUEST_TILE;

    public ServiceRequest(String layerIdent) {
        this.layerIdent = layerIdent;
    }

    public ServiceRequest(String layerIdent, int requestType) {
        this.layerIdent = layerIdent;
        this.requestType = requestType;
    }

    public ServiceRequest(String layerIdent, int requestType, String[] data) {
        this.layerIdent = layerIdent;
        this.requestType = requestType;
        this.data = data;
    }

    public String getLayerIdent() {
        return layerIdent;
    }

    public int getType() {
        return requestType;
    }

    public String[] getData() {
        return data;
    }
}
