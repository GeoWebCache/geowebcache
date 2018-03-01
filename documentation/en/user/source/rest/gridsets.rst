.. _rest.gridsets:

Managing GridSets through the REST API
======================================

The REST API for GridSet management provides a RESTful interface through which clients can 
programatically add, modify, or remove GridSets.

GridSets list
-------------

``/rest/gridsets.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the list of available gridsets
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
     - 400
     -

Sample request:

.. code-block:: xml

 curl -u geowebcache:secured  "http://localhost:8080/geowebcache/rest/gridsets"

Sample response:
 
.. code-block:: xml

    <gridSets>
      <gridSet>
        <name>EPSG:2163</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/gridsets/EPSG:2163.xml" type="text/xml"/>
      </gridSet>
      <gridSet>
        <name>GlobalCRS84Pixel</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/gridsets/GlobalCRS84Pixel.xml" type="text/xml"/>
      </gridSet>
      <gridSet>
        <name>EPSG:4326</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/gridsets/EPSG:4326.xml" type="text/xml"/>
      </gridSet>
      <gridSet>
        <name>GoogleCRS84Quad</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/gridsets/GoogleCRS84Quad.xml" type="text/xml"/>
      </gridSet>
      <gridSet>
        <name>EPSG:900913</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/gridsets/EPSG:900913.xml" type="text/xml"/>
      </gridSet>
      <gridSet>
        <name>GlobalCRS84Scale</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/gridsets/GlobalCRS84Scale.xml" type="text/xml"/>
      </gridSet>
    </gridSets>

GridSet Operations
------------------

``/rest/gridsets/gridset.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the XML representation of the GridSet
     - 200
     - XML, JSON
   * - POST
     - 
     - 405
     - 
   * - PUT
     - Add a new GridSet or modify the definition of a GridSet.
     - 200
     - XML, JSON
   * - DELETE
     - Delete a GridSet
     - 200
     -

*Representations*:

- :download:`XML <representations/gridset_xml.txt>`
- :download:`JSON <representations/gridset_json.txt>`

REST API for GridSets, cURL Examples
--------------------------------------

The examples in this section use the `cURL <http://curl.haxx.se/>`_
utility, which is a handy command line tool for executing HTTP requests and 
transferring files. Though cURL is used the examples apply to any HTTP-capable
tool or library.

Add GridSet
+++++++++++

Given a `gridset.xml` file as the following:

.. code-block:: xml

    <gridSet>
      <name>EPSG:2163</name>
      <srs>
        <number>2163</number>
      </srs>
      <extent>
        <coords>
          <double>-2495667.977678598</double>
          <double>-2223677.196231552</double>
          <double>3291070.6104286816</double>
          <double>959189.3312465074</double>
        </coords>
      </extent>
      <alignTopLeft>false</alignTopLeft>
      <scaleDenominators>
        <double>2.5E7</double>
        <double>1000000.0</double>
        <double>100000.0</double>
        <double>25000.0</double>
      </scaleDenominators>
      <metersPerUnit>1.0</metersPerUnit>
      <pixelSize>2.8E-4</pixelSize>
      <scaleNames>
        <string>EPSG:2163:0</string>
        <string>EPSG:2163:1</string>
        <string>EPSG:2163:2</string>
        <string>EPSG:2163:3</string>
      </scaleNames>
      <tileHeight>200</tileHeight>
      <tileWidth>200</tileWidth>
      <yCoordinateFirst>false</yCoordinateFirst>
    </gridSet>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: application/xml" -d @gridset.xml  "http://localhost:8080/geowebcache/rest/gridsets/gridSet1.xml"

Or if using the GeoServer integrated version of GeoWebCache:

.. code-block:: xml 

 curl -v -u user:password -XPUT -H "Content-type: application/xml" -d @gridset.xml  "http://localhost:8080/geoserver/gwc/rest/gridsets/gridSet1.xml"

Modify GridSet
++++++++++++++

Now, make some modifications to the gridset definition on the `gridset.xml` file:

.. code-block:: xml

    <gridSet>
      <name>EPSG:2163</name>
      <srs>
        <number>2163</number>
      </srs>
      <extent>
        <coords>
          <double>-2495667.977678598</double>
          <double>-2223677.196231552</double>
          <double>3291070.6104286816</double>
          <double>959189.3312465074</double>
        </coords>
      </extent>
      <alignTopLeft>false</alignTopLeft>
      <scaleDenominators>
        <double>2.5E7</double>
        <double>1000000.0</double>
        <double>100000.0</double>
        <double>25000.0</double>
        <double>5000.0</double>
        <double>1000.0</double>
      </scaleDenominators>
      <metersPerUnit>1.0</metersPerUnit>
      <pixelSize>2.8E-4</pixelSize>
      <scaleNames>
        <string>EPSG:2163:0</string>
        <string>EPSG:2163:1</string>
        <string>EPSG:2163:2</string>
        <string>EPSG:2163:3</string>
        <string>EPSG:2163:4</string>
        <string>EPSG:2163:5</string>
      </scaleNames>
      <tileHeight>256</tileHeight>
      <tileWidth>256</tileWidth>
      <yCoordinateFirst>false</yCoordinateFirst>
    </gridSet>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: application/xml" -d @gridset.xml  "http://localhost:8080/geowebcache/rest/gridsets/gridSet1.xml"

Delete GridSet
++++++++++++++

Finally, to delete a gridset, use the HTTP DELETE method against the gridset configuration:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XDELETE "http://localhost:8080/geoserver/gwc/rest/gridsets/gridSet1.xml"
