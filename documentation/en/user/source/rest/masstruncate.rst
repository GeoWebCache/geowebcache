.. _rest.masstruncate:

Mass Truncation
===============

The REST API for mass truncation provides a mechanism for completely clearing caches more conveniently than with the seeding system.

Operations
----------

``/masstruncate``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return a list of mass truncate requests available
     - 200
     - XML
   * - POST
     - Issue a mass truncate request
     - 200
     - XML
   * - PUT
     - 
     - 405
     - 
   * - DELETE
     -
     - 405
     -

Seed/Truncate cURL Examples
---------------------------

The examples in this section use the `cURL <http://curl.haxx.se/>`_
utility, which is a handy command line tool for executing HTTP requests and 
transferring files. Though cURL is used the examples apply to any HTTP-capable
tool or library.

Available Requests
+++++++++++++++++++

Sample request:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XGET -H "Content-type: text/xml"   "http://localhost:8080/geowebcache/rest/masstruncate"
 
Sample response:

.. code-block:: xml 

 * About to connect() to localhost port 8080 (#0)
 *   Trying 127.0.0.1... connected
 * Server auth using Basic with user 'geowebcache'
 > GET /geowebcache/rest/masstruncate HTTP/1.1
 > Authorization: Basic Z2Vvd2ViY2FjaGU6c2VjdXJlZA==
 > User-Agent: curl/7.22.0 (x86_64-pc-linux-gnu) libcurl/7.22.0 OpenSSL/1.0.1 zlib/1.2.3.4 libidn/1.23 librtmp/2.3
 > Host: localhost:8080
 > Accept: */*
 > Content-type: text/xml
 > 
 < HTTP/1.1 200 OK
 < Date: Fri, 31 May 2013 21:10:39 GMT
 < Server: Noelios-Restlet-Engine/1.0..8
 < Content-Type: application/xml; charset=ISO-8859-1
 < Content-Length: 145
 < 
 * Connection #0 to host localhost left intact
 * Closing connection #0
 <massTruncateRequests href="http://localhost:8080/geowebcache/rest/masstruncate"> <requestType>truncateLayer</requestType></massTruncateRequests>

The single ``requestType`` listed, ``truncateLayer``, will clear all caches associated with a named layer, including all permutations of gridset, parameter filter values, and image formats.  Other types may become available in future versions or as extensions/plugins.

Truncate a layer
+++++++++++++++++++++

Sample request to truncate all cached tiles for the ``topp:states`` layer using a ``truncateLayer`` request.

.. code-block:: xml 

 curl -v -u geowebOST -H "Content-type: text/xml" -d "<truncateLayer><layerName>topp:states</layerName></truncateLayer>"  "http://localhost:8080/geowebcache/rest/masstruncate"

Sample response:

.. code-block:: xml 

 * About to connect() to localhost port 8080 (#0)
 *   Trying 127.0.0.1... connected
 * Server auth using Basic with user 'geowebcache'
 > POST /geowebcache/rest/masstruncate HTTP/1.1
 > Authorization: Basic Z2Vvd2ViY2FjaGU6c2VjdXJlZA==
 > User-Agent: curl/7.22.0 (x86_64-pc-linux-gnu) libcurl/7.22.0 OpenSSL/1.0.1 zlib/1.2.3.4 libidn/1.23 librtmp/2.3
 > Host: localhost:8080
 > Accept: */*
 > Content-type: text/xml
 > Content-Length: 64
 > 
 * upload completely sent off: 64out of 64 bytes
 < HTTP/1.1 200 OK
 < Date: Fri, 31 May 2013 22:01:21 GMT
 < Server: Noelios-Restlet-Engine/1.0..8
 < Transfer-Encoding: chunked
 < 
 * Connection #0 to host localhost left intact
 * Closing connection #0
 * About to connect() to localhost port 8080 (#0)
