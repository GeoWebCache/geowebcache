.. _diskquota:

Seeding and truncating through the REST API
===========================================

The REST API for cache seeding and truncation provides a RESTful interface through which clients can 
programatically add or remove tiles from the cache, on a layer by layer basis.

Operations
----------

``/rest/seed/<layer>.<format>``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the status of the seeding threads
     - 200
     - JSON
   * - POST
     - Issue a seed or truncate task request
     - 200
     - XML, JSON
   * - PUT
     - 
     - 405
     - 
   * - DELETE
     -
     - 405
     -

*Representations*:

- :download:`XML <representations/seed_xml.txt>`
- :download:`JSON <representations/seed_json.txt>`


Seed/Truncate cURL Examples
---------------------------

The examples in this section use the `cURL <http://curl.haxx.se/>`_
utility, which is a handy command line tool for executing HTTP requests and 
transferring files. Though cURL is used the examples apply to any HTTP-capable
tool or library.

Seeding XML example
+++++++++++++++++++

Sample request:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPOST -H "Content-type: text/xml" -d '<seedRequest><name>nurc:Arc_Sample</name><srs><number>4326</number></srs><zoomStart>1</zoomStart><zoomStop>12</zoomStop><format>image/png</format><type>truncate</type><threadCount>2</threadCount></seedRequest>'  "http://localhost:8080/geowebcache/rest/seed/nurc:Arc_Sample.xml"
 
Sample response:

.. code-block:: xml 

 * About to connect() to localhost port 8080 (#0)
 *   Trying 127.0.0.1... connected
 * Connected to localhost (127.0.0.1) port 8080 (#0)
 * Server auth using Basic with user 'admin'
 > POST /geoserver/gwc/rest/seed/nurc:Arc_Sample.xml HTTP/1.1
 > Authorization: Basic YWRtaW46Z2Vvc2VydmVy
 > User-Agent: curl/7.21.3 (x86_64-pc-linux-gnu) libcurl/7.21.3 OpenSSL/0.9.8o zlib/1.2.3.4 libidn/1.18
 > Host: localhost:8080
 > Accept: */*
 > Content-type: text/xml
 > Content-Length: 209
 > 
 < HTTP/1.1 200 OK
 < Date: Fri, 14 Oct 2011 22:12:27 GMT
 < Server: Noelios-Restlet-Engine/1.0..8
 < Transfer-Encoding: chunked
 < 
 * Connection #0 to host localhost left intact
 * Closing connection #0


Truncate JSON example
+++++++++++++++++++++

Sample request:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPOST -H "Content-type: application/json" -d "{'seedRequest':{'name':'topp:states','bounds':{'coords':{ 'double':['-124.0','22.0','66.0','72.0']}},'srs':{'number':4326},'zoomStart':1,'zoomStop':12,'format':'image\/png','type':'truncate','threadCount':4}}}"  "http://localhost:8080/geowebcache/rest/seed/nurc:Arc_Sample.json"
 
Sample response:

.. code-block:: xml 

 * About to connect() to localhost port 8080 (#0)
 *   Trying 127.0.0.1... connected
 * Connected to localhost (127.0.0.1) port 8080 (#0)
 * Server auth using Basic with user 'admin'
 > POST /geoserver/gwc/rest/seed/nurc:Arc_Sample.json HTTP/1.1
 > Authorization: Basic YWRtaW46Z2Vvc2VydmVy
 > User-Agent: curl/7.21.3 (x86_64-pc-linux-gnu) libcurl/7.21.3 OpenSSL/0.9.8o zlib/1.2.3.4 libidn/1.18
 > Host: localhost:8080
 > Accept: */*
 > Content-type: application/json
 > Content-Length: 205
 > 
 < HTTP/1.1 200 OK
 < Date: Fri, 14 Oct 2011 22:09:21 GMT
 < Server: Noelios-Restlet-Engine/1.0..8
 < Transfer-Encoding: chunked
 < 
 * Connection #0 to host localhost left intact
 * Closing connection #0


Querying and Terminating the running tasks
==========================================

Operations
----------

``/rest/seed/<layer>``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - 
     - 405
     - 
   * - POST
     - Issue a terminate running tasks request
     - 200
     - 
   * - PUT
     - 
     - 405
     - 
   * - DELETE
     -
     - 405
     -

Getting the current state of the seeding threads
++++++++++++++++++++++++++++++++++++++++++++++++

Sample request:

.. code-block:: xml 

  curl -u <user>:<password> -v -XGET http://localhost:8080/geowebcache/rest/seed/topp:states.json

Sample response:

.. code-block:: xml 

   {"long-array-array":[[17888,44739250,18319],[17744,44739250,18468],[16608,44739250,19733],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]}
  

The returned array of arrays contains one array per seeding thread.
The meaning of each long value in each thread array is: [tiles processed, total # of tiles to process, # of remaining tiles].
In the sample response above only the first three threads are active.


Terminating running tasks
+++++++++++++++++++++++++

The following request terminates all running seed and truncate tasks. Note it doesn't matter which layer name you use at the end of the URL, but it has to be the name of an existing layer.

Sample request:

.. code-block:: xml 

 curl -v -u geowebcache:secured -d "kill_all=1"  "http://localhost:8080/geowebcache/rest/seed/nurc:Arc_Sample"
 
Sample response:

.. code-block:: xml 

 * About to connect() to localhost port 8080 (#0)
 *   Trying 127.0.0.1... connected
 * Connected to localhost (127.0.0.1) port 8080 (#0)
 * Server auth using Basic with user 'admin'
 > POST /geoserver/gwc/rest/seed/nurc:Arc_Sample HTTP/1.1
 > Authorization: Basic YWRtaW46Z2Vvc2VydmVy
 > User-Agent: curl/7.21.3 (x86_64-pc-linux-gnu) libcurl/7.21.3 OpenSSL/0.9.8o zlib/1.2.3.4 libidn/1.18
 > Host: localhost:8080
 > Accept: */*
 > Content-Length: 10
 > Content-Type: application/x-www-form-urlencoded
 > 
 < HTTP/1.1 200 OK
 < Date: Fri, 14 Oct 2011 22:23:04 GMT
 < Server: Noelios-Restlet-Engine/1.0..8
 < Content-Type: text/html; charset=ISO-8859-1
 < Content-Length: 426
 < 
 <html>
 <head>
 <title>GWC Seed Form</title><style type="text/css">
 body, td {
 font-family: Verdana,Arial,'Bitstream Vera Sans',Helvetica,sans-serif;
 font-size: 0.85em;
 vertical-align: top;
 }
 </style>
 </head>
 <body>
 <a id="logo" href="../../"><img src="../../rest/web/geowebcache_logo.png"height="70" width="247" border="0"/></a>
 <ul><li>Requested to terminate all tasks.</li></ul><p><a href="./nurc:Arc_Sample">Go back</a></p>
 * Connection #0 to host localhost left intact
 * Closing connection #0


