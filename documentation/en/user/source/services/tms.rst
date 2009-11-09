.. _tms:

TMS - Tile Map Service
======================

TMS is a predecessor to WMTS, defined by OSGeo. See http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification

The patch for this functionality was provided by Mikael Nyberg. The paths are of the following form:

``http://servername/contextpath/service/tms/1.0.0/layername/z/x/y.formatExtension``


for instance

``http://localhost:8080/geowebcache/service/tms/1.0.0/topp:states/0/0/0.png``


GeoWebCache is currently not capable of defining a GetCapabilities document for this service.


Note that TMS does not provide a way to communicate the SRS, so your layer should only have one grid set. You ensure correctness by assigning a single gridSubset to layer, for example

.. code-block:: xml

   <wmsLayer>
     <!-- ... -->
     <gridSubsets>
       <gridSubset>
         <gridSetName>EPSG:4326</gridSetName>
       </gridSubset>
     </gridSubsets>
     <!-- ... -->
   </wmsLayer>

