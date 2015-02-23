.. _concepts.tiles:

Tiles
=====

GeoWebCache caches images retrieved from a WMS.  The smallest unit of image cached is known as a **tile**.  All tiles are assumed to be the same dimensions and are typically square (i.e. 256 pixels by 256 pixels).

It is important to note that GeoWebCache's tile cache is spatially agnostic.  The tiles are stored in a rectangular grid, indexed by (x,y) coordinates.  A z coordinate (zero-indexed) is used to denote the zoom level, resulting in each tile being indexed as a triplet (x,y,z).

Tile pyramid
------------

As saved by GeoWebCache, the tile system  is "pyramidal", meaning that increasing zoom levels have a larger amount of tiles.  By default, and by far the most common case, for increasing values of z, the amount of tiles in that zoom level is four times the amount of the previous zoom level::

  [number of tiles in zoom level z] = 4 * [number of tiles in zoom level (z-1)]

When applied to square map projections where zoom level z = 0, the number of tiles would result in a single tile (such as the Google projection EPSG:900913 / EPSG:3857)::

  [number of tiles in zoom level z] = 4^z

When applied to rectangular map projections where zoom level z=0, the number of tiles would result in two tiles (such as EPSG:4326 / lat/lon)::

  [number of tiles in zoom level z] = 2 * 4^z

