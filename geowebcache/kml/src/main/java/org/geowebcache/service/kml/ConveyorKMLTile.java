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
 * @author Arne Kepp / The Open Planning Project 2008
 */
package org.geowebcache.service.kml;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;

public class ConveyorKMLTile extends ConveyorTile {
    public ConveyorKMLTile(
            StorageBroker sb,
            String layerId,
            String gridSetId,
            long[] tileIndex,
            MimeType mimeType,
            Map<String, String> fullParameters,
            HttpServletRequest servletReq,
            HttpServletResponse servletResp) {

        super(sb, layerId, gridSetId, tileIndex, mimeType, fullParameters, servletReq, servletResp);
    }

    String urlPrefix = null;

    protected MimeType wrapperMimeType = null;

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public MimeType getWrapperMimeType() {
        return wrapperMimeType;
    }

    public void setWrapperMimeType(MimeType mimeType) {
        this.wrapperMimeType = mimeType;
    }

    public void setError(String message) {
        this.error = true;
        this.errorMsg = message;
    }

    @Override
    public String getHint() {
        return hint;
    }

    @Override
    public void setHint(String hint) {
        this.hint = hint;
    }
}
