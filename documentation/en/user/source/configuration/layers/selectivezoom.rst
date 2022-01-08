.. _configuration.layers.selectivezoomlevel:

Selective zoom level caching
============================

By default, GeoWebCache will cache tiles at all zoom levels that are configured for a given layer.  However, in some situations, such as to save disk space, it may be desirable to cache only tiles from a specific set of zoom levels, leaving others fetched but not stored.  In this case, GeoWebCache can be configured read a range of zoom levels for the layer and only cache tiles that are inside that range.  Tiles requested outside this range will still be displayed, but aside from being briefly held in memory, they will not be retained.  Such a feature is known as either **cache pass-through** or **selective zoom level caching**.

The zoom levels to be cached are specified as a single *contiguous* range, with all zoom levels outside this range not being cached.  So, for a layer with twelve zoom levels (0-11), the following configurations would all be valid:

* Caching zoom levels 0-7 (leaving 5-11 unsaved)
* Caching zoom levels 3-6 (leaving 0-2, 7-11 unsaved)
* Caching zoom levels 0-11 (all zoom levels cached; this is the default behavior)


Configuration
-------------

The minimum and maximum cached zoom levels are set in the :file:`geowebcache.xml` file.  (Read more in :ref:`configuration.layers.howto`).  To specify this, set the ``minCachedLevel`` and ``maxCachedLevel`` values to the minimum and maximum cached zoom levels respectively.  Alternately, it is possible to set only an upper or lower bound, by specifying only one of the two parameters.

The following sample layer definitions shows the proper placement of these parameters, with other tags added in for context.  This example caches zoom levels 0-6:

.. code-block:: xml

   <wmsLayer>
     <!-- ... -->
     <gridSubsets>
       <gridSubset>
         <gridSetName> ... </gridSetName>
         <extent> ... </extent>
         <zoomStart> ... </zoomStart>
         <zoomStop> ... </zoomStop>

         <minCachedLevel>0</minCachedLevel>
         <maxCachedLevel>6</maxCachedLevel>

       </gridSubset>
     </gridSubsets>
     <!-- ... -->
   </wmsLayer>

This example would cache all zoom levels greater than or equal to 3:

.. code-block:: xml

   <wmsLayer>
     <!-- ... -->
     <gridSubsets>
       <gridSubset>
         <gridSetName> ... </gridSetName>
         <extent> ... </extent>
         <zoomStart> ... </zoomStart>
         <zoomStop> ... </zoomStop>

         <minCachedLevel>3</minCachedLevel>

       </gridSubset>
     </gridSubsets>
     <!-- ... -->
   </wmsLayer>


Error checking
--------------

Values are not checked for correctness, which may cause improper results.  Values outside the full range of zoom levels for that layer will be treated as if they were the maximum or minimum valid zoom level.  Also, setting the ``minCachedLevel`` to a value higher than the ``maxCachedLevel`` will cause caching to be bypassed entirely for all zoom levels.

Interaction with seeding
------------------------

The :ref:`seeding process <concepts.operations>` will respect selective zoom level caching.  It will still appear that the entire zoom range is being seeded, but levels outside the cache range will be ignored.

Interaction with metatiling
---------------------------

Even when not caching tiles, GeoWebCache will still take advantage of :ref:`metatiling <concepts.metatiles>`.  When a request that is outside of the cached zoom level range comes in, metatiles will still be fetched and temporarily saved in memory in order to minimize redundant requests.

