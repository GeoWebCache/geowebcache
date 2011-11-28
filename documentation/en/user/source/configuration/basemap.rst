.. _configuration.basemap:

Seed Form Basemap
=================

The seed form (and potentially other pages) includes an interactive map that provide a preview of the tile layer. This interactive map can be configured to show a basemap. Configuration of the basemap is done by adding a section of JavaScript to the GeoWebCache configuration file that returns a created basemap layer for OpenLayers.

This configuration can inspect information about the layer selected in the seed form to ensure the appropriate basemap is used. This is done through javascript variables. The following is an example of how layer information is made available during the basemap configuration:

   .. code-block:: xml

     var layerName = 'topp:states';
     var layerFormat = 'image/png';
     var layerTileSize = new OpenLayers.Size(200,200);
     var layerProjection = new OpenLayers.Projection('EPSG:2163');
     var layerResolutions = [6999.999999999999, 280.0, 27.999999999999996, 6.999999999999999];
     var layerExtents = new OpenLayers.Bounds(-2495667.977678598,-2223677.196231552,3291070.6104286816,959189.3312465074);


If null is returned, no basemap is shown. In the example below, layerProjection is checed to see if it is one supported by the basemap.

   .. code-block:: xml

	<basemapConfig>
		if(layerProjection.projCode == "EPSG:4326" || layerProjection.projCode == "EPSG:900913") {
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
		} else {
			return null;
		}
	</basemapConfig>
