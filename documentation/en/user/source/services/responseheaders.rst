.. _responseheaders:

Special HTTP Response Headers
=============================

GeoWebCache writes down some standard and custom HTTP response headers when serving a tile request, either to aid in debugging
problems or to adhere to an HTTP 1.1 transfer control mechanism.

Custom Response Headers
-----------------------

Whenever GeoWebCache serves a tile request, it will write the following custom headers on the HTTP response:

 * ``geowebcache-cache-result`` : one of ``HIT``, ``MISS``, ``WMS``, ``OTHER``. ``HIT`` means that the tile requested
   was found on the cache, ``MISS`` that the tile wasn't found on the cache but was acquired from the layer's data source,
   ``WMS`` indicates the request was proxied to the origin WMS verbatim (for example, for GetFeatureInfo requests), and 
   ``OTHER`` is used when the response was the default white/transparent tile or an error occurred.
 * ``geowebcache-tile-index`` : contains the three-dimensional tile index in x,y,z order of the returned tile image in the corresponding grid space (e.g. ``[1, 0, 0]``).
 * ``geowebcache-tile-bounds`` : the bounds of the returned tile in the corresponding coordinate reference system (e.g. ``-180,-90,0,90``). 
 * ``geowebcache-gridset`` : the name of the gridset the tile belongs to (e.g. ``EPSG:900913``, ``GoogleCRS84Scale``, etc).
 * ``geowebcache-crs`` : the code of the coordinate reference system of the matching gridset (e.g. ``EPSG:900913``, ``EPSG:4326``, etc).
 
This is a sample request/response using cURL:

.. code-block:: bash

   curl -v "http://localhost:8080/geowebcache/service/wms?LAYERS=sde%3Abmworld&FORMAT=image%2Fpng&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&STYLES=&SRS=EPSG%3A4326&BBOX=-180,-38,-52,90&WIDTH=256&HEIGHT=256&tiled=true">/dev/null 

::

   < HTTP/1.1 200 OK
   < geowebcache-tile-index: [0, 1, 2]
   < geowebcache-cache-result: HIT
   < geowebcache-tile-index: [0, 1, 2]
   < geowebcache-tile-bounds: -180.0,-38.0,-52.0,90.0
   < geowebcache-gridset: GlobalCRS84Pixel
   < geowebcache-crs: EPSG:4326
   < Content-Type: image/png
   < Content-Length: 102860
   < Server: Jetty(6.1.8)
 

Last-Modified and If-Modified-Since
-----------------------------------

Well behaved HTTP 1.1 clients and server applications can make use of this HTTP control mechanism to know when the
locally cached content (like a tile image already downloaded by the browser) hasn't changed on the server and hence 
it's local copy is up to date, hence there's no need to re-download the same content.
This can result in considerable bandwidth savings.

The mechanism is explained in HTTP's 1.1 `RCF 2616 <http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html>`_, sections 14.29 and 14.25.

GeoWebCache will write a ``Last-Modified`` HTTP response header when serving a tile image as an RFC-1123 ``HTTP-Date`` \
(e.g. ``Last-Modified: Wed, 15 Nov 1995 04:58:08 GMT``). Clients can use the value of
that response header in the following requests for the same tile using the ``If-Modified-Since`` request header.
If the tile wasn't modified after that instant, GeoWebCache will return a 304 status code (304) indicating that the
conditional GET operation found that the resource was available and not modified.

Try it yourself:

.. code-block:: bash

   curl -v "http://localhost:8080/geowebcache/service/wms?LAYERS=img%20states&FORMAT=image%2Fpng&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&STYLES=&EXCEPTIONS=application%2Fvnd.ogc.se_inimage&SRS=EPSG%3A4326&BBOX=-135,45,-90,90&WIDTH=256&HEIGHT=256">/dev/null 

::

   > Host: localhost:8080
   > Accept: */*
   >
   < HTTP/1.1 200 OK
   ...
   < Last-Modified: Wed, 25 Jul 2012 00:42:00 GMT
   < Content-Type: image/png
   < Content-Length: 31192

Now use the value of the returned ``Last-Modified`` response header in the request and check thre response code. No content is 
actually transferred, but the client is informed it's copy of the tile is up to date:


.. code-block:: bash

   curl --header "If-Modified-Since: Wed, 25 Jul 2012 00:42:00 GMT" -v "http://localhost:8080/geowebcache/service/wms?...">/dev/null 

::

   > Host: localhost:8080
   > Accept: */*
   > If-Modified-Since: Wed, 25 Jul 2012 00:42:00 GMT
   > 
   < HTTP/1.1 304 Not Modified
   < Last-Modified: Wed, 25 Jul 2012 00:42:00 GMT
   < Content-Type: image/png
   < Content-Length: 31192

Set the If-Modified-Since header to one second before, and you should get a 200 status code and the tile content instead:

.. code-block:: bash

   curl --header "If-Modified-Since: Wed, 25 Jul 2012 00:41:59 GMT" -v "http://localhost:8080/geowebcache/service/wms?...">/dev/null 

::

   > Host: localhost:8080
   > Accept: */*
   > If-Modified-Since: Wed, 25 Jul 2012 00:41:59 GMT
   > 
   < HTTP/1.1 200 OK
   ...
   < Last-Modified: Wed, 25 Jul 2012 00:42:00 GMT
   < Content-Type: image/png
   < Content-Length: 31192


