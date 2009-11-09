.. _how_it_works:

How It Works
============


Maps are often static. As most mapping clients render WMS (Web Map Service) data every time they are queried, this can result in unnecessary processing and increased wait times. GeoWebCache optimizes this experience by saving (caching) map tiles as they are requested, in effect acting as a proxy between client (such as OpenLayers or Google Maps) and server (such as GeoServer, or any WMS-compliant server). As new maps and tiles are requested, GeoWebCache intercepts these calls and returns pre-rendered tiles if stored, or calls the server to render new tiles as necessary. Thus, once tiles are stored, the speed of map rendering increases many times, making for a more seamless user experience.

.. figure:: how_it_works.png
   :align: center
   


In the picture above, the blue box on the GeoWebCache machine represents the tile storage. Ideally, most requests are answered from this storage without consulting the WMS server(s). Hence the arrow to the clients is drawn much larger, because GeoWebCache can answer hundreds or thousands of requests per second.
