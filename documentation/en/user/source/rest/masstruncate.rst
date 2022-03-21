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

.. code-block:: bash 

   curl -v -u geowebcache:secured -XGET -H "Content-type: text/xml"   "http://localhost:8080/geowebcache/rest/masstruncate"
 
Sample response::

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

.. code-block:: bash

   curl -v -u geowebcache:secured -X POST -H "Content-type: text/xml" -d "<truncateLayer><layerName>topp:states</layerName></truncateLayer>"  "http://localhost:8080/geowebcache/rest/masstruncate"

Sample response::

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

Truncate extent across parameters and formats
+++++++++++++++++++++++++++++++++++++++++++++

This will issue truncate jobs within the extent <-100, 40, -99, 41> for each parameter set and format in the ``EPSG:432g`` gridset of layer ``points``.

.. code-block:: xml 

   <truncateExtent>
     <layerName>points</layerName>
     <gridSetId>EPSG:4326</gridSetId>
     <bounds>
       <coords>
         <double>-100</double>
         <double>40</double>
         <double>-99</double>
         <double>41</double>
       </coords>
     </bounds>
   </truncateExtent>

Purge orphan parameters from a layer
++++++++++++++++++++++++++++++++++++

Checks the layer ``points`` for cached tiles that are not accessible to its current parameter filters and truncates them.

.. code-block:: xml 

   <truncateOrphans>
     <layerName>points</layerName>
   </truncateOrphans>

Truncate parameter set
++++++++++++++++++++++++++++++++++++

Checks the layer ``points`` for cached tiles that are not accessible to its current parameter filters and truncates them.  Depending on the Blob Store used, this may be considerably faster than using a regular truncate job.  The File System blob store in particular can use directory deletes which are usually much faster than having GeoWebCache traverse all the tile files to delete them individually.  Depending on the OS/File System a traverse may be done, but it will usually be significantly faster than an application can manage.

.. code-block:: xml 

   <truncateParameters>
     <layerName>points</layerName>
     <parameters>
       <entry>
         <string>STYLES</string>
         <string>point</string>
       </entry>
     </parameters>
   </truncateParameters>

Truncate All Layers
++++++++++++++++++++++++++++++++++++

To clear the entire GWC cache.

.. code-block:: xml 

   <truncateAll></truncateAll>