.. _wms:

WMS - Web Map Service
=====================
Web Map Service (WMS) is an OGC standard that supports requests such as ``getcapabilities``, ``getmap`` and ``getfeatureinfo``. GeoWebCache supports the former two natively and can proxy other requests to the WMS backend server. GeoWebCache was developed with WMS 1.1.0 in mind, but can support elevation and height through the use of modifiable parameters.

To achieve good performance, requests should conform to the tiles that GeoWebCache stores. However, many WMS clients cannot make tiled requests. Since version 1.2.2, GeoWebCache can recombine tiles to answer arbitrary WMS requests. See Support for Regular WMS Clients below.


WMS-C - WMS Tiling Clients
==========================

The WMS Tiling Client Recommendation, or WMS-C for short, is a recommendation set forth by OSGeo for making tiled requests using WMS. You can read more about it here: http://wiki.osgeo.org/wiki/WMS_Tiling_Client_Recommendation. 

By default, any layer that does not specify a gridSubset will be configured to implement the recommended Unprojected and Mercator profiles. 
However, GeoWebCache supports any set of resolutions (scales), tile sizes and projections.

Clients: uDig, GeoExplorer
--------------------------

On the front page of your GeoWebCache instance you will find a link to the WMS GetCapabilities document. Recent versions of uDig can use this URL to configure all layers automatically. The same applies to GeoExplorer and other GeoExt based applications.

Note that each output format results in a new set of tiles. The GetCapabilities document can also be downloaded and edited manually or be filtered automatically using XSL templates.

Manual Configuration of OpenLayers
----------------------------------

The easiest way to create an OpenLayers client is to copy the source from one of the automatically generated demos. To ensure that requests match the underlying grid properly you must pay particular attention to the map options ``resolutions``, ``projection`` and ``maxExtent``.

Note that in current versions of OpenLayers the *basel layer* has a special meaning, hence certain parameters may be quitely ingored if you transfer a layer from the demo applications into an existing application.

Important Notes
---------------
Clients written to make tiled WMS requests assume that the origin is the bottom left coordinate of the bounding box. The gridSet used should therefore not be defined with ``<alignTopLeft>TRUE</alignTopLeft>``, if omitted the value is assumed to be false.

Note that to use WMS you should not have two grid sets with the same SRS defined for a layer. GeoWebCache will use the SRS to look up the grid set and simply use the first one it finds. The case where two grid sets may be useful is if you have several sets of scales that you do not want combine into one large set.


Support for Regular WMS Clients
-------------------------------

GeoWebCache can recombine and resample tiles to answer arbitrary WMS requests. To enable this feature, open ``geowebcache-wmsservice-context.xml``, find ``<property name="fullWMS"><value>FALSE</value></property>`` and change to ``<property name="fullWMS"><value>TRUE</value></property>``. Another way to enable this feature is to add the following string to the ``geowebcache.xml`` file: ``<fullWMS>TRUE</fullWMS>``. All layers that are to support this feature must currently be configured to support a PNG format. Inside the WMS request the user can add a new WMS parameter called **hints** which can be set to one of the following configurations: *speed*, *default*, *quality*. Going from *speed* to *quality* the image quality is increased but also the computation time.    

Note that this requires GeoWebCache to decompress many tiles and recompress the resulting canvas; also for PNG8 and GIF output formats an optimal palette is calculated. Response times will therefore be on the order of seconds, depending on the size of the requested image and the tile sizes. You may have to increase the heap size of the Java process (``-Xmx256M``) to use this functionality.
