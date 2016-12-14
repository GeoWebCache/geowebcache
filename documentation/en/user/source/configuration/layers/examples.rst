.. _configuration.layers.examples:

Examples
========

The following are example configurations of layers in GeoWebCache.

Minimal configuration
---------------------

This example shows the absolute minimum information needed to configure a layer in GeoWebCache together with the assumptions made.

At the very least, GeoWebCache needs to know only two things to cache a layer:

#. The URL of the WMS server
#. The name of the layer as defined in the WMS

.. code-block:: xml

   <gwcConfiguration>
     <!-- ... -->
     <layers>
       <!-- ... -->
       <wmsLayer>
         <name>topp:states</name>
         <wmsUrl><string>http://demo.opengeo.org/geoserver/wms</string></wmsUrl>
       </wmsLayer>
       <!-- ... -->
     </layers>
   </gwcConfiguration>

In this case, the assumptions are:

#. WMS version 1.1.1 is used (``version=1.1.1``)
#. The layer will be valid for EPSG:4326 and EPSG:900913, with world extent (``srs=EPSG:4326``, ``srs=EPSG:900913``)
#. The layer name on the WMS is identical to the name of the layer as specified here (``layers=topp:states``)
#. The supported image format is PNG (image/png) and JPEG (image/jpeg) (``format=image/png``, ``format=image/jpeg``)
#. The default style used (``styles=``)
#. Exceptions are reported inside images (``exceptions=application/vnd.ogc.se_inimage``)
#. Requests using OpenLayers are made with ``transparent=true``

Explicit blob store configuration
---------------------------------

Unless otherwise specified, the tiles produced by a ``wmsLayer`` will be persisted to the cache defined by the default blob store.
The ``blobStoreId`` element allows to define an alternate blob store where to store the layer tiles.
See the :ref:`configuration.storage` page for more information on how to configure blob stores.

In this example, the layer is configured to use the ``myBlobStore`` blob store, which must be defined in the ``blobStores`` section
of ``gwcConfiguration`` with the same identifier.

.. code-block:: xml

   <gwcConfiguration>
     <!-- ... -->
     <layers>
       <!-- ... -->
       <wmsLayer>
         <blobStoreId>myBlobStore</blobStoreId>
         <name>topp:states</name>
         <wmsUrl><string>http://demo.opengeo.org/geoserver/wms</string></wmsUrl>
       </wmsLayer>
       <!-- ... -->
     </layers>
   </gwcConfiguration>


Simple configuration
--------------------

The following is a more realistic but still simple example for configuring a layer in GeoWebCache.

In this case, the requirements are:

#. Combine two layers from a WMS, (``nurc:Img_Sample`` and ``topp:states``)
#. Name the layer ``img states`` (note the space in the name)
#. Set the projection to EPSG:4326
#. Limit the extent to the continental United States
#. Support only GIF
#. Draw the background color (where there is no data) in a shade of blue
#. Set the transparency to false

The resulting configuration would look like:

.. code-block:: xml

   <gwcConfiguration>
     <!-- ... -->
     <layers>
       <!-- ... -->
         <wmsLayer>
           <name>img states</name>
           <metaInformation>
             <title>Nicer title for Image States</title>
             <description>This is a description. Fascinating.</description>
           </metaInformation>
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
           <wmsUrl>
             <string>http://demo.opengeo.org/geoserver/wms</string>
           </wmsUrl>
           <wmsLayers>nurc:Img_Sample,topp:states</wmsLayers>
           <transparent>false</transparent>
           <bgColor>0x0066FF</bgColor>
         </wmsLayer>
       <!-- ... -->
     </layers>
   </gwcConfiguration>

.. note:: The reference to the gridset EPSG:4326 may look a bit mysterious.  It is one of only two gridsets (the other being EPSG:900913) that is automatically defined inside GeoWebCache.  Other gridsets will need to be configured manually.  (Learn more about :ref:`concepts.gridsets` and :ref:`configuration.layers.projections`.)
