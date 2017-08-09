.. _configuration.security:

Security
--------

GeoWebCache requires authentication to perform certain operations, such as reconfiguration or seeding. By default ``WEB-INF\users.properties`` looks like this:

.. code-block:: c

   geowebcache=secured,ROLE_ADMINISTRATOR


Change ``geowebcache`` to your desired username and ``secured`` to your password of choice. It is recommended that you use only ASCII characters.

Fine-grained control can be implemented using an external proxy or servlet filter, or within GeoWebCache by using a request filter. Advanced users who understand ACEGI may want to edit ``acegi-config.xml`` as well.

Proxy Requests
==============

The WMS Service can proxy requests it does not understand to a WMS back end.  By specifying ``GEOWEBCACHE_WMS_PROXY_REQUEST_WHITELIST``.  It should be set to a semicolon separated list of request types.  ``*`` will allow all requests.  

By default, it is set to ``*`` unless there are ``SecurityFilter`` extensions installed in which case it defaults to ``GetLegendGraphic``.  ``GetMap``, ``GetCapabillities``, and ``GetFeatureInfo`` are all interpreted by GWC rather than proxied and so are not affected by the whitelist.
