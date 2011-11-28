.. _webinterface.status:

Status Page
===========

The Status page is the main page of the GeoWebCache web interface.  It as accessible at ``http://<GEOWEBCACHE_URL>``.  For example, if geowebcache is running in Tomcat on port 8080, the URL would be ``http://localhost:8080/geowebcache``.

This page also links to the :ref:`webinterface.demo` and the :ref:`webinterface.jobs`.

Capabilities documents
----------------------

GeoWebCache operates as a WMS 1.1.1, WMTS 1.0.0, and a TMS 1.0.0 server, and thus publishes capabilities documents for all of these services.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Service
     - URL
   * - WMS 1.1.1
     - ``http://<GEOWEBCACHE_URL>/service/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities&TILED=true``
   * - WMTS 1.0.0
     - ``http://<GEOWEBCACHE_URL>/service/wmts?REQUEST=getcapabilities``
   * - TMS 1.0.0
     - ``http://<GEOWEBCACHE_URL>/service/tms/1.0.0``

Runtime statistics
------------------

The Status page also displays basic runtime statistics, including uptime, how many requests have been made, total and peak throughput, as well as statitics over intervals of 3, 15, and 60 seconds.

