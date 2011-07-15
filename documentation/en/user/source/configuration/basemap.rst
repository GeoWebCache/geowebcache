.. _configuration.basemap:

Seed Form Basemap
=================

The seed form (and potentially other pages) includes an interactive map that provide a preview of the tile layer. This interactive map can be configured to show a basemap. Configuration of the basemap is done by adding a section of JavaScript to the GeoWebCache configuration file that returns a created basemap layer for OpenLayers.

Currently this one basemap layer must support all projections for gridsets configured in GeoWebCache or missing tiles will be returned.

   .. code-block:: xml

	<basemapConfig>
		return new OpenLayers.Layer.WMS(
		    "basemap", "../../service/wms",
		    {
			layers: 'raster test layer',
			format: 'image/jpeg'
		    },
		    {
			tiled: true,
			buffer: 0,
			wrapDateLine: true,
			tileSize: new OpenLayers.Size(256,256),
			displayOutsideMaxExtent: true
		    }
		);
	</basemapConfig>
