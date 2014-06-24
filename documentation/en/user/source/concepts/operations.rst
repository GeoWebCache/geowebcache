.. _concepts.operations:

Operations
==========

When working with GeoWebCache tiles, there are three main operations associated with any task:  **seeding**, **reseeding**, and **truncating**.

Seeding
-------

The **seeding** operation will generate any tiles not already saved in the cache.  If tiles are already in the cache, these will be preserved.  This operation is best for typical usage on a static cache.

Reseeding
---------

The **reseeding** operation will regenerate all tiles, even if already saved in the cache.  This operation is used when data and tiles have become stale and need to be updated.

Truncating
----------

The **truncating** operation will remove/delete tiles from the cache.  This operation is used when data tiles are stable and need to be removed, but without replacing them with newer tiles.  This operation can be used to free disk space, either manually, with :ref:`configuration.diskquotas`, or with :ref:`GeoRSS <configuration.layers.georss>`. 
