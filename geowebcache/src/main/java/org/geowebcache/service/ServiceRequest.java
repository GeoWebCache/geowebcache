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
    public final static int SERVICE_REQUEST_DIRECT = 0x0001;
    
    public final static int SERVICE_REQUEST_METATILE = 0x0002;

    public final static int SERVICE_REQUEST_USE_JAI = 0x0004;

    String layerIdent = null;

    String[] data = null;

    int requestType = 0x0000 | SERVICE_REQUEST_METATILE;

    public ServiceRequest(String layerIdent) {
        this.layerIdent = layerIdent;
    }

    public ServiceRequest(String layerIdent, String[] data) {
        this.layerIdent = layerIdent;
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

    public void setFlag(boolean enable, int flag) {
        if(enable) {
            requestType = requestType | flag;
        } else {
            requestType = requestType & (~ flag); 
        }
    }
    
    public boolean getFlag(int flag) {
        return (requestType & flag) != 0x0000;
    }
}
