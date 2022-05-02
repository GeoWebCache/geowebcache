.. _bingmaps:

Bing Maps
=========

Bing Maps, formerly known as Microsoft Virtual Earth, is another mapping service that primarily operates on spherical mercator tiles. This means the layer must available in EPSG:900913 (now known as EPSG:3857) to use this API.

It appears that the Bing Maps API continues to use ``VETileSourceSpecification`` objects. The following code has not been tested exhaustively for some time, please send feedback to the geowebcache-users mailinglist.

.. code-block:: javascript

   var map = new VEMap('myMap');
   var tileSourceSpec = 
       new VETileSourceSpecification(
         'TITLE_OF_LAYER', 
         'http://localhost:8080/geowebcache/service/ve?quadkey=%4&format=image/png&layers=layer-name'
       );
   tileSourceSpec.Opacity = 0.5;
   map.AddTileLayer(tileSourceSpec, true);

.. code-block:: html
 
   <body onload="GetMap();">

In the above example you must replace ``layer-name`` and ``http://localhost:8080/geowebcache`` as appropriate. You may also change ``image/png``. Bing Maps will automatically replace ``%4`` with the quad-key for the tile.


