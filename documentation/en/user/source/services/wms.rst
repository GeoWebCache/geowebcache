.. _wms:

WMS-C - WMS Tiling Clients
==========================

The WMS Tiling Client Recommendation, or WMS-C for short, is a recommendation set forth by OSGeo for making tiled requests using WMS. You can read more about it here: http://wiki.osgeo.org/wiki/WMS_Tiling_Client_Recommendation  By default, any layer that does not specify a gridSubset will be configured to implement the recommended Unprojected and Mercator profiles.

However, GeoWebCache supports any set of resolutions (scales), tile sizes and projections.

Clients: uDig, GeoExplorer
--------------------------

On the front page of your GeoWebCache instance you will find a link to the WMS GetCapabilities document. Recent versions of uDig can use this URL to configure all layers automatically. The same is true for GeoExplorer and other GeoExt based applications.

Note that each output format results in a new set of tiles. The GetCapabilities document can also be downloaded and edited manually or be filtered automatically using XSL templates.

Manual Configuration of OpenLayers
----------------------------------

The easiest way to create an OpenLayers client is to copy the source from one of the automatically generated demos. To ensure that requests match the underlying grid properly you must pay particular attention to the map options ``resolutions``, ``projection`` and ``maxExtent``.

Note that in current versions of OpenLayers the *basel layer* has a special meaning, hence certain parameters may be quitely ingored if you transfer a layer from the demo applications into an existing application.

Important Notes
---------------
Clients written to make tiled WMS requests assume that the origin is the bottom left coordinate of the bounding box. The gridSet used should therefore not be defined with ``<alignTopLeft>TRUE</alignTopLeft>``, if omitted the value is assumed to be false.

Note that to use WMS you should not have two grid sets with the same SRS defined for a layer. GeoWebCache will use the SRS to look up the grid set and simply use the first one it finds. The case where two grid sets may be useful is if you have several sets of scales that you do not want combine into one large set.

Improvements
------------

GeoWebCache is seeking funding for a full WMS implementation, i.e. recombining and subsampling tiles to answer arbitrary WMS requests.

