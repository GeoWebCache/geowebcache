.. _simple:

Simple Configuration Examples
=============================

The examples below are exerpts, it is recommended that you use the standard geowebcache.xml in WEB-INF/classes and keep the XML headers.

Minimal 
--------

At the very least, GeoWebCache needs to know only two things to be able to cache a layer:

1. The name of the layer
2. The URL to the WMS server

.. code-block:: xml

   <gwcConfiguration>
     <!-- ... -->
     <layers>
       <!-- ... -->
       <wmsLayer>
         <name>topp:states</name>
         <wmsUrl><string>http://atlas.opengeo.org:8080/geoserver/wms</string></wmsUrl>
       </wmsLayer>
       <!-- ... -->
     </layers>
   </gwcConfiguration>

In this case a lot of things will be assumed:

1. The ``LAYERS=`` parameter in the requests will be the name specified above, topp:states
2. The layer will be valid for EPSG:4326 and EPSG:900913, world bounds
3. PNG and JPEG will be supported
4. The default style will be used
5. Requests will be made with ``transparent=true``
6. Exceptions will reported as XML
7. WMS ``VERSION=1.1.0`` will be used for the GetpMap request


Simple
------
A simple, but more realistic configuration would be to say that we want
1. We want to combine two layers, such as ``LAYERS=topp:states,sf:streams``
2. The combined layer should be named ``sf:state Streams`` (with a space in the name)
3. EPSG:4326, but only for the extent of the the United States
4. The layer should only support GIF
5. Where we have no data, the background color should blue. In hex that is 0x0066FF
6. We have two WMS servers, and we want to use them in a round-robin fashion, with automatic fail-over
7. We want the WMS servers to use a palette on the server, named ``popshade``

.. code-block:: xml

   <gwcConfiguration>
     <!-- ... -->
     <layers>
       <!-- ... -->
       <wmsLayer>
         <name>img states</name>
         <mimeFormats><string>image/gif</string></mimeFormats>
         <gridSubsets>
           <gridSubset>
             <gridSetName>EPSG:4326</gridSetName>
             <extent>
               <coords>
                 <double>-129.6</double>
                 <double>3.45</double>
                 <double>-62.1</double>
                 <double>70.9</double>
               </coords>
             </extent>
           </gridSubset>
         </gridSubsets>
         <wmsLayers>nurc:Img_Sample,topp:states</wmsLayers>
         <wmsUrl>
           <string>http://atlas.openplans.org:8080/geoserver/wms</string>
           <string>http://geo.openplans.org:8080/geoserver/wms</string>
         </wmsUrl>
         <bgColor>0x0066FF</bgColor>
         <palette>popshade</palette>
       </wmsLayer>
       <!-- ... -->
     </layers>
   </gwcConfiguration>

Note: The reference to the grid set EPSG:4326 may be a bit mysterious. It is one of a very few grid sets that is automatically defined for backwards compatibility.

Definining a Grid Set
---------------------
The concept behind a grid set is documented in-depth separately.
