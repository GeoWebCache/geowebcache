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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.api.geometry.Bounds;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.mbtiles.MBTilesFile;
import org.geotools.mbtiles.MBTilesMetadata;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.layer.meta.TileJSON;
import org.geowebcache.layer.meta.VectorLayerMetadata;

/** Info Object storing basic MBTiles Cached info */
public class MBTilesInfo {

    private static Logger log = Logging.getLogger(MBTilesInfo.class.getName());

    private static final CoordinateReferenceSystem WGS_84;

    private static final BoundingBox WORLD_MERCATOR_WGS_84_BOUNDS;

    static {
        try {
            WGS_84 = CRS.decode("EPSG:4326", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        WORLD_MERCATOR_WGS_84_BOUNDS = new BoundingBox(-180.0, -85, 180, 85.0);
    }

    private MBTilesMetadata metadata;

    private BoundingBox bounds;

    private BoundingBox wgs84Bounds;

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

        Bounds env = metadata.getBounds();
        ReferencedEnvelope envelope = null;
        if (env != null) {
            try {
                wgs84Bounds = getBBoxFromEnvelope(env);
                envelope =
                        ReferencedEnvelope.create(env, WGS_84).transform(SPHERICAL_MERCATOR, true);
            } catch (TransformException | FactoryException e) {
                throw new IllegalArgumentException(
                        "Exception occurred while transforming the bound of: "
                                + file.getFile().getAbsolutePath(),
                        e);
            }
        } else {
            if (log.isLoggable(Level.WARNING)) {
                log.warning(
                        "Provided MBTile has a Null Envelope: "
                                + file.getFile().getAbsolutePath()
                                + ". Using full GridSet extent ");
            }
            envelope = WORLD_ENVELOPE;
            wgs84Bounds = WORLD_MERCATOR_WGS_84_BOUNDS;
        }
        bounds = getBBoxFromEnvelope(envelope);
    }

    private BoundingBox getBBoxFromEnvelope(Bounds envelope) {
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

    public void decorateTileJSON(TileJSON tileJSON) {
        tileJSON.setMinZoom(minZoom);
        tileJSON.setMaxZoom(maxZoom);
        tileJSON.setBounds(
                new double[] {
                    wgs84Bounds.getMinX(),
                    wgs84Bounds.getMinY(),
                    wgs84Bounds.getMaxX(),
                    wgs84Bounds.getMaxY()
                });
        if (metadata != null) {
            String description = metadata.getDescription();
            if (description != null) {
                tileJSON.setDescription(description);
            }
            tileJSON.setCenter(metadata.getCenter());
            tileJSON.setAttribution(metadata.getAttribution());
            String json = metadata.getJson();

            int index = -1;
            if (json != null && ((index = json.indexOf("[")) > 0)) {
                // skip the "vector_layers initial part and go straight to the array
                json = json.substring(index, json.length() - 1).trim();
                ObjectMapper mapper = new ObjectMapper();
                List<VectorLayerMetadata> layers = null;
                try {
                    layers =
                            mapper.readValue(
                                    json, new TypeReference<List<VectorLayerMetadata>>() {});
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException(
                            "Exception occurred while parsing the layers metadata. " + e);
                }
                tileJSON.setLayers(layers);
            }
        }
    }
}
