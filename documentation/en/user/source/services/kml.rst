.. _kml:

KML
===

KML, Keyhole Markup Language, is an XML format and a OGC standard. The most common client is Google Earth. All features are referenced in lat/lon, hence GeoWebCache uses the EPSG:4326 grid set to publish tiles to such clients.


Background
----------
KML primarily refers to a way of creating linked documents that contain overlays or placemarks. Overlays are pyramids comprised of tiles from the cache. GeoWebCache stores the images in the cache. The hierarchies are generated on the fly, since reading them from disk would be slower.

However, a subset of KML can also be used to described actual geometries in a vector format. This format is very similar to GML. In conjunction with GeoServer, GeoWebCache can also cache regionated KML tiles. This means that not only is the hierarchy described in KML, but the actual features are also sent to the as vector KML.

The available KML hierarchies should be linked from the page with automatically generated demos. The syntax is

``http://localhost:8080/geowebcache/service/kml/<layer name>.<tile format extension>.<wrapper format extension>``

where, depending on your configuration
layer name - The name of the layer
tile format extension - most likey png or jpeg, but png8 and gif8 do also work
wrapper format extenion - kml or kmz, the latter is a zipped version of the former

A typical example is

``http://localhost:8080/geowebcache/service/kml/topp:states.png.kml``


Advanced Topics
---------------
Regionating is an advanced topic refers to a process of ordering elements by importance. This enables us to add more and more information as the user zooms in on a smaller area, without sending the complete dataset in advance. See the GeoServer documentation for more information.

Request filters can be used to limit the pyramids to a non-rectangular shape. This is particularly important for KML clients as they can make thousands of requests while panning over areas where the WMS server has no data.

Parameter filters are currently not supported.

TODO: This section is incomplete, please help make it better
