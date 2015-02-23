.. _whichgwc:

Standalone vs. GeoServer-integrated
===================================

GeoWebCache exists in two varieties: as a **standalone web application**, and as a built-in **extension to GeoServer** (`<http://geoserver.org>`_).

Both varieties are largely identical, but with some important differences.  

The GeoServer-integrated GeoWebCache automatically configures all the layers GeoServer serves as tiled maps with default styles and the two most commonly used spatial reference systems (SRS): EPSG:900913 (Google Spherical Mercator) and EPSG:4326 (lat/lon).

The standalone GeoWebCache is not automatically configured, but can be used with many different types of layers form different sources, not just GeoServer.  The standalone GeoWebCache can also run in a separate servlet container from the data server(s), which is usually desired in production environments.


Which GeoWebCache should you use?
---------------------------------

You should use the GeoServer-integrated GeoWebCache if you are only going to be connecting to a single GeoServer instance, and don't mind it being in the same servlet container as GeoServer.

You should use the standalone GeoWebCache if you want to load layers from multiple sources or want to isolate GeoWebCache from any data servers.

This documentation discusses the standalone version of GeoWebCache.

For more information about GeoWebCache in GeoServer, see `<http://docs.geoserver.org/stable/en/user/geowebcache/index.html>`_.




