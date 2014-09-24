.. _rest.statistics:

In Memory Cache Statistics
==========================

The REST API allows you to get the in memory cache statistics if the **blobstore** used is an instance of **MemoryBlobStore**.

Operations
----------

``/statistics``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return a representation of the statistics
     - 200
     - XML, JSON
   * - POST
     - 
     - 405
     - 
   * - PUT
     - 
     - 405
     - 
   * - DELETE
     -
     - 405
     -

Available Requests
+++++++++++++++++++

Request in XML:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XGET "http://localhost:8080/geowebcache/rest/statistics.xml"
 
Sample response:

.. code-block:: xml 

	<gwcInMemoryCacheStatistics>
		<hitCount>0</hitCount>
		<missCount>0</missCount>
		<evictionCount>0</evictionCount>
		<totalCount>0</totalCount>
		<hitRate>100.0</hitRate>
		<missRate>0.0</missRate>
		<currentMemoryOccupation>0.0</currentMemoryOccupation>
		<totalSize>67108864</totalSize>
		<actualSize>0</actualSize>
	</gwcInMemoryCacheStatistics>

Request in JSON:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XGET "http://localhost:8080/geowebcache/rest/statistics.json"
 
Sample response:

.. code-block:: xml 

	{"gwcInMemoryCacheStatistics":{"missRate":0,"totalCount":0,"missCount":0,"hitCount":0,"actualSize":0,"evictionCount":0,"hitRate":100,"totalSize":67108864,"currentMemoryOccupation":0}}