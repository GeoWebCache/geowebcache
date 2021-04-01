/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2021
 */
package org.geowebcache.mbtiles.layer;

import static org.geotools.mbtiles.MBTilesFile.SPHERICAL_MERCATOR;
import static org.geotools.mbtiles.MBTilesFile.WORLD_ENVELOPE;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.mbtiles.MBTilesFile;
import org.geotools.mbtiles.MBTilesMetadata;
import org.geotools.referencing.CRS;
import org.geowebcache.grid.BoundingBox;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

/** Info Object storing basic MBTiles Cached info */
public class MBTilesInfo {

    private static Log log = LogFactory.getLog(MBTilesInfo.class);

    private static final CoordinateReferenceSystem WGS_84;

    static {
        try {
            WGS_84 = CRS.decode("EPSG:4326", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MBTilesMetadata metadata;

    private BoundingBox bounds;

    public MBTilesMetadata.t_format getFormat() {
        return format;
    }

    private final MBTilesMetadata.t_format format;

    private int minZoom;

    private int maxZoom;

    private String metadataName;

    public int getMinZoom() {
        return minZoom;
    }

    public int getMaxZoom() {
        return maxZoom;
    }

    public BoundingBox getBounds() {
        return bounds;
    }

    public String getMetadataName() {
        return metadataName;
    }

    public MBTilesInfo(MBTilesFile file) throws IOException {
        metadata = file.loadMetaData();
        metadataName = metadata.getName();
        format = metadata.getFormat();
        minZoom = metadata.getMinZoom();
        maxZoom = metadata.getMaxZoom();

        Envelope env = metadata.getBounds();
        ReferencedEnvelope envelope = null;
        if (env != null) {
            try {
                envelope =
                        ReferencedEnvelope.create(env, WGS_84).transform(SPHERICAL_MERCATOR, true);
            } catch (TransformException | FactoryException e) {
                throw new IllegalArgumentException(
                        "Exception occurred while transforming the bound of: "
                                + file.getFile().getAbsolutePath(),
                        e);
            }
        } else {
            if (log.isWarnEnabled()) {
                log.warn(
                        "Provided MBTile has a Null Envelope: "
                                + file.getFile().getAbsolutePath()
                                + ". Using full GridSet extent ");
            }
            envelope = WORLD_ENVELOPE;
        }
        bounds = getBBoxFromEnvelope(envelope);
    }

    private BoundingBox getBBoxFromEnvelope(Envelope envelope) {
        BoundingBox bbox = null;
        if (envelope != null) {
            bbox =
                    new BoundingBox(
                            envelope.getMinimum(0),
                            envelope.getMinimum(1),
                            envelope.getMaximum(0),
                            envelope.getMaximum(1));
        }
        return bbox;
    }
}
