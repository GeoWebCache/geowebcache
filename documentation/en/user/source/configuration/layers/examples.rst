.. _configuration.layers.examples:

Examples
========

The following are example configurations of layers in GeoWebCache.

Minimal configuration
---------------------

This example shows the absolute minimum information needed to configure a layer in GeoWebCache, as well as the assumptions that will be made.

At the very least, GeoWebCache needs to know only two things to be able to cache a layer:

#. The URL of a to the WMS server
#. The name of the layer as known to the WMS

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

In this case the following will be assumed:

#. WMS version 1.1.1 will be used (``version=1.1.1``)
#. The layer will be valid for EPSG:4326 and EPSG:900913, with world extent (``srs=EPSG:4326``, ``srs=EPSG:900913``)
#. The layer name on the WMS will be identical to the name of the layer as specified here (``layers=topp:states``)
#. The image formate supported will be PNG (image/png) and JPEG (image/jpeg) (``format=image/png``, ``format=image/jpeg``)
#. The default style will be used (``styles=``)
#. Exceptions will reported inside images (``exceptions=application/vnd.ogc.se_inimage``)
#. Requests using OpenLayers will be made with ``transparent=true``


Simple configuration
--------------------

The following is a more realistic but still simple example for configuring a layer in GeoWebCache.

In this case, the following are our requirements:

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

.. note:: The reference to the gridset EPSG:4326 may be a bit mysterious.  It is one of only two gridsets (the other being EPSG:900913) that is automatically defined inside GeoWebCache.  Other gridsets will need to be manually configured.  (Learn more about :ref:`concepts.gridsets` and :ref:`configuration.layers.projections`.)