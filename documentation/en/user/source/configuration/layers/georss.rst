.. _configuration.layers.georss:

Expiring tiles with GeoRSS
==========================

`GeoRSS <http://www.georss.org>`_ is an `RSS <http://en.wikipedia.org/wiki/RSS>`_ feed containing geographic information in `GML (Geography Markup Language) <http://www.opengeospatial.org/standards/gml>`_ format.  GML is an XML grammar for expressing geographical features.

GeoWebCache can read a GeoRSS feed, and use the information about features contained in that feed to determine which tiles to expire.  This allows for a more dynamic and updated caching system.  

GeoRSS expiration is set on a per-layer basis.

Reading a GeoRSS feed
---------------------

You can configure a GeoRSS feed that GeoWebCache will read in the :file:`geowebcache.xml` file.  Add the following tags inside a ``<wmsLayer>`` immediately after any ``<gridSubset>``:

.. code-block:: xml

  <wmsLayer>
    <!-- ... -->
    <gridSubset>
      <!-- ... -->
    </gridSubset>
    <updateSources>
      <geoRssFeed>
        <feedUrl>GEORSS_FEED_URL</feedUrl>
        <gridSetId>GRIDSET</gridSetId>
        <pollInterval>INTERVAL</pollInterval>
        <operation>OPERATION</operation>
        <format>IMAGE_MIMETYPE</format>
        <seedingThreads>THREADS</seedingThreads>
        <maxMaskLevel>MAXMASKLEVEL</maxMaskLevel>
      </geoRssFeed>
    </updateSources>
    <!-- ... -->
  </wmsLayer>

The above variables are defined as:

.. list-table::
   :widths: 20 10 70
   :header-rows: 1

   * - Variable
     - Required?
     - Description
   * - GEORSS_FEED_URL
     - Yes
     - The URL to valid GeoRSS feed.  An example would be ``http://someserver/georss?layers=somelayer&amp;lastupdate=${lastUpdate}&amp;srs=EPSG:4326``
   * - GRIDSET
     - No
     - The relevant grid set for the layer.  The geometries in the feed must be given in the same SRS as the grid set.
   * - INTERVAL
     - Yes
     - How often (in seconds) the GeoRSS feed is polled.
   * - OPERATION
     - No
     - One of the three GeoWebCache :ref:`concepts.operations`: **seed**, **reseed**, or **truncate**.  The default is **truncate**.  Since truncation happens before any other task, seed when used here is identical to reseed.
   * - IMAGE_MIMETYPE
     - No
     - Used to specify a single format (MIME type, such as "image/png") so that only tiles of that format are updated.  If omitted,  all tiles in all formats will be updated.
   * - THREADS
     - No
     - Controls the number of threads to use, if the operation is set to seed or reseed.  This value is for a single image format, so if multiple image formats are specified, they will each have this number of threads apply to them.
   * - MAXMASKLEVEL
     - Yes
     - In order to determine what tiles are affected, the geometries from the feed are rendered onto canvases where every pixel represents a tile.  This number determines the highest zoom level where such a raster is created.  A higher number means a higher resolution image and thus fewer tiles, but requires more memory. **11** is usually a good number.

Note also the ``lastupdate=${lastUpdate}`` from the above GeoRSS feed example.  This variable sets the timestamp of the last update, so that older features are not processed again.  During the first poll, this value is not set, so all features are processed.  The value is taken from the ``<updated>`` field in the GeoRSS feed.  

