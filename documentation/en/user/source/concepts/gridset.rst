.. _gridset:

Grid Sets and Grid Subsets
==========================

A ``<gridSet>`` defines
 - a spatial reference system (EPSG code)
 - a bounding box describing an extent. This should generally be the maximum extent for the reference system above
 - a set of scale denominators, resolutions OR a range of zoom levels
 - tile dimensions in pixels (the same for all zoom levels)
 - to calculate scales, a notion of pixel size is required. The default is 0.28mm/pixel, corresponding to 90.71428571428572 DPI

A ``<gridSubset>`` is a hint to GWC that a layer uses a particular gridSet, it optionally defines
 - a bounding box contained by the gridSet bounding box
 - a subset of zoom levels 


Illustrated Description
-----------------------

The process described below is repeated for every zoom level.


.. figure:: gridset_boundingbox.png
   :align: left
   :class: float_left

   *Initial bounding box.* Assume the grid set bounding box is -10.0,-30.0,85.0,21.0 and that the resolution for the zoom level is set to 11.25 deg / 256 pixels = 0.04395 (Scale 1:1.8E7)


.. figure:: gridset_bl.png
   :align: left
   :class: float_left

   *Grid set aligned bottom-left to the bounding box.* The specified bounding box does not correspond to an integer number of tiles, hence GWC expands the bounding box to -10.0,-30.0,91.25,26.25


.. figure:: gridset_tl.png
   :align: left
   :class: float_left

   *Grid set aligned to the top-left of the bounding box.* The default is to align the bounding box to the bottom left. This is assumed by tiling WMS clients and TMS. However, for WMTS it makes more sense to align to the top left corner. If ``<alignTopLeft>`` is set to true, GWC will expand towards the bottom instead. In this case the bounding box becomes -10.0,-36.75,91.25,21.0 

.. figure:: gridsubset_boundingbox.png
   :align: left
   :class: float_left

   *Grid subset bounding box.* Since a particular layer may not cover the entire grid set, GWC supports the notion of an grid subset. If no extent is specified, it is assumed that the layer covers the entire grid set. You may also specify a subset of zoom levels, as defined by the array of resolutions / scale denominators in the grid set definition.

 
.. figure:: gridsubset_tiles.png
   :align: left
   :class: float_left

   *Grid subset tiles.* Again, GWC will round the extents to the nearest tile index


Corresponding XML
-----------------

.. code-block:: xml

   <gridSet>
       <name>testGridSet</name>
       <srs><number>4326</number></srs>
       <extent>
         <coords>
          <double>-10.0</double>
          <double>-30.0</double>
          <double>85.0</double>
          <double>21.0</double>
         </coords>
       </extent>
       <alignTopLeft>false</alignTopLeft>
       <resolutions>
         <double>0.0879</double>
         <double>0.04395</double>
         <double>0.021975</double>
         <double>0.0109875</double>
       </resolutions>
       <tileHeight>250</tileHeight>
       <tileWidth>250</tileWidth>
   </gridSet>


.. code-block:: xml

   <wmsLayer>
     <!-- ... -->
     <gridSubset>
       <gridSetName>testGridSet</gridSetName>
       <extent>
         <coords>
          <double>-14.0</double>
          <double>-15.0</double>
          <double>48.0</double>
          <double>16.0</double>
         </coords>
       </extent>
     </gridSubset>
     <!-- ... -->
   </wmsLayer>
