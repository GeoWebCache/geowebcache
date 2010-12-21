.. _requestfilter:

Request Filter
==============

.. note:: This section is under construction.

Request Filters filter requests before they reach the cache. They can for example be used to return transparent tiles for areas that are within the bounding box of the layer but where not actual data is available. 

The two included implementations are FileRasterFilter and WMSRasterFilter. Java developers should easily be able to write additional classes that implement the RequestFilter interface.

This section is incomplete, please contribute. See the exhaustive configuration example and the XSD documentation for how to use the included request filter implementations.
