.. _gmaps:

Google Maps
===========

Google Maps is based on spherical mercator, so the layer must available in EPSG:900913 (now known as EPSG:3857) to use this API.

.. code-block:: javascript

   var tilelayer = new GTileLayer(null, null, null, {
         tileUrlTemplate: 'http://localhost:8080/geowebcache/service/gmaps?layers=layer-name&zoom={Z}&x={X}&y={Y}&format=image/png', 
         isPng:true,
         opacity:0.5 } );

In the example above Google Maps will automatically fill in {Z}, {X} and {Y} with the appropriate location. You must replace ``http://localhost:8080/geowebcache`` with the appropriate URL and replace ``layer-name``. If you wish you can also replace image/png to use JPEG, 8 bit PNG or GIF.
