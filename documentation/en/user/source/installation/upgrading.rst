.. _upgrading:

Upgrading from a pre 1.4 release
================================

Starting with GeoWebCache 1.4.0 the metastore support has been removed and all its functionality has been moved to the file blob store, including support for tile expiration based on creatin date and request parameter handling.

Depending on the features used in the previous GeoWebCache the migration will take three different paths:

  * if the GeoWebCache used no metastore nor disk quota the migration will be completely transparent and no changes will be made to the contents of the on disk cache.
  * if the disk quota mechanism was used the disk quota database will be removed (since it's internal structure changed) and automatically re-populated by a background thread while GWC is  running
  * if the metastore was in use the informations contained in it will be automatically migrated on the tiles and cache paths on the first startup, after which the metastore database will be deleted

The metastore migration by default will migrate the parameter ids in the new `SHA-1 <http://en.wikipedia.org/wiki/SHA-1>`_ form, however by default the migration of tile creation time is turned off by default in order to avoid waiting hours on existing caches with billions of tiles.
In case the user desires to perform the tile date migration fully the JVM will have to be started with an extra system variable, ``-DMIGRATE_CREATION_DATES=true``.

All of the above will happen at the first time a GeoWebCache 1.4.x runs on a 1.3.x style cache directory. Once the change is over it won't be possible anymore to use said data directory against a 1.3.x series if parameters were used (in case no layer parameters were in use a downgrade is still possible by manually removing the disk quota store folder).

``Configuration`` beans need no longer be passed to the ``Dispatcher``.  Simply declare the beans in ``geowebcache-core-context.xml``, and GWC will use them up automatically.
