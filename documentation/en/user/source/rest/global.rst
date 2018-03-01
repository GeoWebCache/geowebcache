.. _rest.global:

Managing Global Server Configuration through the REST API
=========================================================

The REST API for server configuration provides a RESTful interface through which clients can view and modify global server configuration.

Global Operations
-----------------

``/rest/global.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the XML representation of the global server configuration
     - 200
     - XML, JSON
   * - POST
     - 
     - 405
     - 
   * - PUT
     - Modify global server configuration
     - 200
     - XML, JSON
   * - DELETE
     - 
     - 405
     -

*Representations*:

- :download:`XML <representations/global_xml.txt>`
- :download:`JSON <representations/global_json.txt>`

REST API for Global Server Configuration, cURL Examples
-------------------------------------------------------

The examples in this section use the `cURL <http://curl.haxx.se/>`_
utility, which is a handy command line tool for executing HTTP requests and 
transferring files. Though cURL is used the examples apply to any HTTP-capable
tool or library.

Get Global Configuration
++++++++++++++++++++++++

.. code-block:: xml 

 curl -v -u geowebcache:secured -XGET "http://localhost:8080/geowebcache/rest/global.xml"

Or if using the GeoServer integrated version of GeoWebCache:

.. code-block:: xml 

 curl -v -u user:password -XGET "http://localhost:8080/geoserver/gwc/rest/global.xml"

Modify Global Configuration
+++++++++++++++++++++++++++

Sample request:

Given a `global.xml` file as the following:

.. code-block:: xml

    <global>
      <backendTimeout>180</backendTimeout>
    </global>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: text/xml" -d @global.xml  "http://localhost:8080/geowebcache/rest/global.xml"

This will modify the backend timeout of the server, leaving other global configuration values unchanged.

.. note:: If you modify the service provider, you will need to provide the entire serviceProvider structure - modifying single values within serviceProvider is not supported at this time.