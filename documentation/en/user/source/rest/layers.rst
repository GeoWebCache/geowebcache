.. _rest.layers:

Managing Layers through the REST API
====================================

The REST API for Layer management provides a RESTful interface through which clients can 
programatically add, modify, or remove cached Layers.

Operations
----------

``/rest/seed/layers/<layer>.xml``

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
     - Modify the definition/configuration of a Layer
     - 200
     - XML
   * - PUT
     - Add a new Layer
     - 200
     - XML
   * - DELETE
     - Delete a Layer
     - 200
     -

*Representations*:

- :download:`XML minimal <representations/wmslayer_minimal.xml.txt>`
- :download:`XML <representations/wmslayer.xml.txt>`

Note: JSON representation is intentionally left aside as the library used for JSON marshaling has issues with multi-valued properties such as `parameterFilters`.

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

Note that the addressed resource ``layer1.xml``, without the ``.xml`` extension, must match the name of the layer in the xml representation.


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

And use the HTTP POST method instead:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPOST -H "Content-type: text/xml" -d @layer.xml  "http://localhost:8080/geoserver/gwc/rest/layers/layer1.xml"
 
Delete Layer
++++++++++++

Finally, to delete a layer, use the HTTP DELETE method against the layer resource:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XDELETE "http://localhost:8080/geoserver/gwc/rest/layers/layer1.xml"

