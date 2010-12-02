.. _configuration:

Configuration
=============

Once you have GeoWebCache up and running, it is time to configure it. You can either do it using a "getcapabilities" document from a WMS server, or manually.

After each change, be sure to reload the configuration and check `<http://localhost:8080/geowebcache/demo>`

.. toctree::
   :maxdepth: 2

   access_control.rst
   xml/index
   storage.rst
   gridsets.rst
   layers.rst
   projections.rst
   services.rst
   staticcaps.rst
   parameterfilter.rst
   requestfilter.rst
   diskquota.rst


Note: GeoWebCache is looking for funding to build a JavaScript client, based on GeoExt, to provide a graphical web interface and ease configuration.
 