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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.config.meta;

import java.io.Serializable;

public class XsltTemplates implements Serializable{

    private static final long serialVersionUID = 2137310753590604314L;

    private String wmsGetCapabilities;

    private String wmtsGetCapabilities;


    /**
     * @return the wmsGetCapabilities XSLT URL
     */
    public String getWmsGetCapabilities() {
        return wmsGetCapabilities;
    }

    /**
     * @param wmsGetCapabilities 
     *            the wmsGetCapabilities XSLT URL to set
     */
    public void setWmsGetCapabilities(String wmsGetCapabilities) {
        this.wmsGetCapabilities = wmsGetCapabilities;
    }

    /**
     * @return the wmtsGetCapabilities XSLT URL
     */
    public String getWmtsGetCapabilities() {
        return wmtsGetCapabilities;
    }

    /**
     * @param wmtsGetCapabilities 
     *            the wmtsGetCapabilities XSLT URL to set
     */
    public void setWmtsGetCapabilities(String wmtsGetCapabilities) {
        this.wmtsGetCapabilities = wmtsGetCapabilities;
    }

}
