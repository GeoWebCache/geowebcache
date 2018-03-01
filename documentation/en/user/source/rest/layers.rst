.. _rest.layers:

Managing Layers through the REST API
====================================

The REST API for Layer management provides a RESTful interface through which clients can 
programatically add, modify, or remove cached Layers.

Layers list
-----------

``/rest/layers.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the list of available layers
     - 200
     - XML
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

.. note:: JSON representation is intentionally left aside as the library used for JSON marshaling has issues with multi-valued properties such as `parameterFilters`.

Sample request:

.. code-block:: xml

 curl -u geowebcache:secured  "http://localhost:8080/geowebcache/rest/layers"

Sample response:
 
.. code-block:: xml

 <layers>
  <layer>
    <name>img states</name>
    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/layers/img+states.xml" type="text/xml"/>
  </layer>
  <layer>
    <name>raster test layer</name>
    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/layers/raster+test+layer.xml" type="text/xml"/>
  </layer>
  <layer>
    <name>topp:states</name>
    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/layers/topp%3Astates.xml" type="text/xml"/>
  </layer>
 </layers>

Layer Operations
----------------

``/rest/layers/<layer>.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the XML representation of the Layer
     - 200
     - XML
   * - POST
     - Modify the definition/configuration of a Layer. DEPRECATED - use PUT instead.
     - 200
     - XML
   * - PUT
     - Add a new layer or modify the definition/configuration of a Layer.
     - 200
     - XML
   * - DELETE
     - Delete a Layer
     - 200
     -

*Representations*:

- :download:`XML minimal <representations/wmslayer_minimal.xml.txt>`
- :download:`XML <representations/wmslayer.xml.txt>`

.. note:: JSON representation is intentionally left aside as the library used for JSON marshaling has issues with multi-valued properties such as `parameterFilters`.

REST API for Layers, cURL Examples
----------------------------------

The examples in this section use the `cURL <http://curl.haxx.se/>`_
utility, which is a handy command line tool for executing HTTP requests and 
transferring files. Though cURL is used the examples apply to any HTTP-capable
tool or library.

Add Layer
+++++++++

Sample request:

Given a `layer.xml` file as the following:

.. code-block:: xml

 <wmsLayer>
   <name>layer1</name>
   <mimeFormats>
     <string>image/png</string>
   </mimeFormats>
   <gridSubsets>
     <gridSubset>
       <gridSetName>EPSG:900913</gridSetName>
     </gridSubset>
   </gridSubsets>
   <wmsUrl>
     <string>http://localhost:8080/geoserver/wms</string>
   </wmsUrl>
   <wmsLayers>topp:states</wmsLayers>
 </wmsLayer>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: text/xml" -d @layer.xml  "http://localhost:8080/geowebcache/rest/layers/layer1.xml"

Or if using the GeoServer integrated version of GeoWebCache:

.. code-block:: xml 

 curl -v -u user:password -XPUT -H "Content-type: text/xml" -d @layer.xml  "http://localhost:8080/geoserver/gwc/rest/layers/layer1.xml"

.. note:: the addressed resource ``layer1.xml``, without the ``.xml`` extension, must match the name of the layer in the xml representation.


Modify Layer
++++++++++++

Now, make some modifications to the layer definition on the `layer.xml` file:


.. code-block:: xml

 <wmsLayer>
   <name>layer1</name>
   <mimeFormats>
     <string>image/png</string>
     <string>image/jpeg</string>
     <string>image/gif</string>
   </mimeFormats>
   <gridSubsets>
     <gridSubset>
       <gridSetName>EPSG:900913</gridSetName>
     </gridSubset>
     <gridSubset>
       <gridSetName>EPSG:4326</gridSetName>
     </gridSubset>
   </gridSubsets>
   <wmsUrl>
     <string>http://localhost:8080/geoserver/wms</string>
   </wmsUrl>
   <wmsLayers>topp:states,nurc:Img_Sample</wmsLayers>
 </wmsLayer>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: text/xml" -d @layer.xml  "http://localhost:8080/geoserver/gwc/rest/layers/layer1.xml"
 
Delete Layer
++++++++++++

Finally, to delete a layer, use the HTTP DELETE method against the layer resource:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XDELETE "http://localhost:8080/geoserver/gwc/rest/layers/layer1.xml"

