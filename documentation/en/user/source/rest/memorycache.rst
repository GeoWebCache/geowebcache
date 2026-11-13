.. _rest.memorycache:

Managing the In-memory Tile Cache through the REST API
======================================================

The REST API for the in-memory tile cache allows to view and replace its settings, the ``memoryCache`` element of
:file:`geowebcache.xml` described in :ref:`configuration.storage`.

Operations
----------

``/rest/memorycache``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the saved settings
     - 200
     - XML, JSON
   * - POST
     -
     - 405
     -
   * - PUT
     - Replace the settings
     - 200, 400
     - XML, JSON
   * - DELETE
     -
     - 405
     -

GET returns the settings as saved: a value missing from the response takes its default.

PUT replaces all the settings, **a value left out goes back to its default**, it does not keep its current value. Changing the
settings empties the cache, while sending the same settings again leaves it as it is. Settings the cache could not start with,
a ``timeToLive`` below 1 or a ``backend`` class that is not installed, are rejected with a 400 and not saved.

The ``backend`` element takes the same ``class`` and settings as in :file:`geowebcache.xml`, for example ``hazelcastClientKeyValueStore``
for the Hazelcast client backend when that module is installed. Saving does not connect to the backend: it is reached the next time the
cache is used, and an unreachable one is then reported in the log.

As for any other change of :file:`geowebcache.xml`, other nodes sharing the file only see the new settings after a reload or a restart.

cURL Examples
-------------

Get the settings:

.. code-block:: bash

 curl -u geowebcache:secured "http://localhost:8080/geowebcache/rest/memorycache.xml"

Or if using the GeoServer integrated version of GeoWebCache:

.. code-block:: bash

 curl -u user:password "http://localhost:8080/geoserver/gwc/rest/memorycache.xml"

Given a :file:`memorycache.xml` file as the following:

.. code-block:: xml

    <memoryCache>
      <enabled>true</enabled>
      <timeToLive>120</timeToLive>
      <backend class="heapKeyValueStore">
        <maxSizeMB>256</maxSizeMB>
      </backend>
    </memoryCache>

replace the settings with:

.. code-block:: bash

 curl -u geowebcache:secured -XPUT -H "Content-type: text/xml" -d @memorycache.xml "http://localhost:8080/geowebcache/rest/memorycache.xml"

This turns the cache on, with tiles kept 120 seconds in up to 256 MB of heap, and ``passthrough`` and ``statsInterval`` back to their defaults.
