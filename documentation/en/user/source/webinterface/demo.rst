.. _webinterface.demo:

Demo Page
=========

The Demo page contains a listing of all layers known to GeoWebCache and the valid services associated with them.  This information is set in the file :file:`geowebcache.xml`.  To edit layer settings read the section on :ref:`configuration.layers`.

For each layer and depending on settings,  there will be a link to built-in OpenLayers demos for every projection and image format supported.  If EPSG:4326 (lat/lon) is supported by a particular layer, there will also be links to KML output, for display in Google Earth.

The URL format of the demos is as follows:

OpenLayers::

  http://<GEOWEBCACHE_URL>/demo/<LAYER_NAME>?gridSet=<GRID_SET>&format=<IMAGE_MIMEFORMAT>

KML::

  http://<GEOWEBCACHE_URL>/service/kml/<LAYER_NAME>.<IMAGE_FORMAT>.kml

where:

* ``<GEOWEBCACHE_URL>`` - URL to the GeoWebCache instance
* ``<LAYER_NAME>`` - Name of the layer as known to GeoWebCache (``<wmsLayer>``).
* ``<GRID_SET>`` - Name of the grid set as known in GeoWebCache (``<gridSet``).  It does not need to be in the form "EPSG:####".
* ``<IMAGE_MIMEFORMAT>`` - MIME type of image format (``image/png``)
* ``<IMAGE_FORMAT>`` = Typical extension for image format (``png``) 


Seeding layers
--------------

For each layer listed, there is a link to :guilabel:`Seed this layer`.  Clicking one of these links will bring up :ref:`webinterface.seed` where seed tasks can be started or managed.

Reload configuration
--------------------

There is also a button to :ref:`Reload Configuration <configuration.reload>`.  Click this to make any configuration changes known to GeoWebCache.  You will have to :ref:`authenticate <configuration.security>` as an administrator first.

Clear GWC Cache
--------------------

There is also a button to wipe the GWC cache clean. This will internally truncate all layers. 
