.. _upgrading:

Upgrading
=========

1. Before you start:
   
   * Make a note of any customizations made to geowebcache :file:`WEB-INF` folder.

     .. warning:: When updating, be sure to preserve any changes made to :file:`WEB-INF/geowebcache-core-context.xml` or :file:`WEB-INF/web.xml` as these files will be replaced during the upgrading process.

   * To maintain cache location, follow the :ref:`configuration.file` instructions to define ``GEOWEBCACHE_CACHE_DIR`` and the location of :file:`geowebcache.xml` configuration.

2. Stop Tomcat, follow the installation instructions to download and install the latest GeoWebCache version.
   
   Deploying a new version of GeoWebCache will replace :file:`<tomcat dir>/webapps/geowebcache` folder.

3. Re-apply any customizations made to the :file:`WEB-INF` folder.

4. Start tomcat 

Java Compatibility
------------------

GeoWebCache is compiled with Java 17 (LTS) and tested with Java 17 LTS and Java 21 LTS.

============ ================= ================ ================ ==================
Java         Initial           Required         Final            Tested
============ ================= ================ ================ ==================
Java 21 LTS  GeoWebCache 1.25                                    OpenJDK
Java 17 LTS  GeoWebCache 1.22  GeoWebCache 1.28                  OpenJDK
Java 11 LTS  GeoWebCache 1.15  GeoWebCache 1.22 GeoWebCache 1.27 OpenJDK
Java 8 LTS   GeoWebCache 1.9   GeoWebCache 1.9  GeoWebCache 1.22 Oracle and OpenJDK
============ ================= ================ ================ ==================

GeoWebCache 1.18 Update
-----------------------

Java 17 Minimum
^^^^^^^^^^^^^^^

GeoWebCache 1.18 is now compiled with Java 17 LTS, and is tested with Java 17 LTS and Java 21 LTS.

GeoWebCache 1.15 Update
-----------------------

Java 9 Minimum
^^^^^^^^^^^^^^

In 1.15 GeoWebCache changed to work with Java 9 or higher, with Java 11 LTS recommended. This included several changes to package names to avoid splitting packages across modules.  If you used any of the following classes in plugins, while emebdding GWC in a larger application, or using modified application contexts, you will need to make the follwing changes.

+----------------+---------------------------------------+-------------------------------------------+
| Module         | ≤ 1.14                                | ≥ 1.15                                    |
|                +---------------------------------------+-------------------------------------------+
|                | Classes Affected                                                                  |
+================+=======================================+===========================================+
| **gwc-georss** | **org.geowebcache.storage**           | **org.geowebcache.georss**                |
|                +---------------------------------------+-------------------------------------------+
|                | ``GeometryRasterMaskBuilder``, ``RasterMaskTestUtils``                            |
+----------------+---------------------------------------+-------------------------------------------+
| **gwc-kml**    | **org.geowebcache.conveyor**          | **org.geowebcache.service.kml**           |
|                +---------------------------------------+-------------------------------------------+
|                | ``ConveyorKMLTile``                                                               |
+----------------+---------------------------------------+-------------------------------------------+
| **gwc-sqlite** | **org.geotools.mbtiles**              | **org.geowebcache.sqlite**                |
|                +---------------------------------------+-------------------------------------------+
|                | ``GeoToolsMbtilesUtils``                                                          |
+----------------+---------------------------------------+-------------------------------------------+
| **gwc-wms**    | **org.geowebcache.config**            | **org.geowebcache.config.wms**            |
|                +---------------------------------------+-------------------------------------------+
|                | ``GetCapabilitiesConfiguration``                                                  |
+----------------+---------------------------------------+-------------------------------------------+
| **gwc-wms**    | **org.geowebcache.filter.parameters** | **org.geowebcache.config.wms.parameters** |
|                +---------------------------------------+-------------------------------------------+
|                | ``NaiveWMSDimensionFilter``, ``WMSDimensionProvider``                             |
+----------------+---------------------------------------+-------------------------------------------+
| **gwc-wms**    | **org.geowebcache.io**                | **org.geowebcache.io.codec**              |
|                +---------------------------------------+-------------------------------------------+
|                | ``ImageDecoder``, ``ImageDecoderContainer``, ``ImageDecoderImpl``,                |
|                | ``ImageEncoder``, ``ImageEncoderContainer``, ``ImageEncoderImpl``,                |
|                | ``ImageIOInitializer``, ``PNGImageEncoder``                                       |
+----------------+---------------------------------------+-------------------------------------------+


GeoWebCache 1.4.0 Update
------------------------

File Blob Store replaces metastore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Starting with GeoWebCache 1.4.0 the metastore support has been removed and all its functionality has been moved to the file blob store, including support for tile expiration based on creatin date and request parameter handling.

Depending on the features used in the previous GeoWebCache the migration will take three different paths:

  * if the GeoWebCache used no metastore nor disk quota the migration will be completely transparent and no changes will be made to the contents of the on disk cache.
  * if the disk quota mechanism was used the disk quota database will be removed (since it's internal structure changed) and automatically re-populated by a background thread while GWC is  running
  * if the metastore was in use the informations contained in it will be automatically migrated on the tiles and cache paths on the first startup, after which the metastore database will be deleted

The metastore migration by default will migrate the parameter ids in the new `SHA-1 <http://en.wikipedia.org/wiki/SHA-1>`_ form, however by default the migration of tile creation time is turned off by default in order to avoid waiting hours on existing caches with billions of tiles.
In case the user desires to perform the tile date migration fully the JVM will have to be started with an extra system variable, ``-DMIGRATE_CREATION_DATES=true``.

All of the above will happen at the first time a GeoWebCache 1.4.x runs on a 1.3.x style cache directory. Once the change is over it won't be possible anymore to use said data directory against a 1.3.x series if parameters were used (in case no layer parameters were in use a downgrade is still possible by manually removing the disk quota store folder).

``Configuration`` beans need no longer be passed to the ``Dispatcher``.  Simply declare the beans in ``geowebcache-core-context.xml``, and GWC will use them up automatically.
