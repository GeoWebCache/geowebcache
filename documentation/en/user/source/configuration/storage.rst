.. _configuration.storage:

Storage
=======

.. note:: The cache is the physical location of the layers tiles, whether it is on the local file system, an S3 bucket, a pure in-memory cache, 
    or anything else. A "blobstore" is a software component that provides the operations to store and retrieve tiles to and from a given
    storage mechanism.


Cache
-----

Starting with version 1.8.0, GeoWebCache supports multiple persistent storage mechanisms for tiles:

* File blob store: stores tiles in a directory structure consisting of various image files organized by layer and zoom level.
* S3 blob store: stores tiles in an `Amazon Simple Storage Service <http://aws.amazon.com/s3/>`_ bucket, as individual "objects" following a
  `TMS <http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification>`_-like key structure.
* Google Cloud Storage blob store: stores tiles in a GCS bucket using the same TMS-like structure as S3.
* Azure blob store, MBTiles blob store, Swift blob store: additional storage backends described below.

Zero or more blobstores can be configured in the configuration file to store tiles at different locations and on different storage back-ends.
One of the configured blobstores will be the **default** one. Meaning that it will be used to store the tiles of every layer whose configuration
does not explicitly indicate which blobstore shall be used.

.. note:: **there will always be a "default" blobstore**. If a blobstore to be used by default is not explicitly configured, one will
   be created automatically following the legacy cache location lookup mechanism used in versions prior to 1.8.0.

.. _configuration.file:

Configuration File
------------------

The location of the configuration file, :file:`geowebcache.xml`, will be defined by the ``GEOWEBCACHE_CACHE_DIR`` application parameter.

There are a few ways to define ``GEOWEBCACHE_CACHE_DIR``:

* JVM system environment variable
* Servlet context parameteter
* Operating system environment variable

The variable in all cases is defined as ``GEOWEBCACHE_CACHE_DIR``.

1. To set as a JVM system environment variable, add the parameter ``-DGEOWEBCACHE_CACHE_DIR=<path>`` to your servlet startup script.

   In Tomcat, this can be added to the Java Options (``JAVA_OPTS``) variable in the startup script, or by creating :file:`setenv.sh` / :file:`setenv.bat`:

2. To set as a servlet context parameter, edit the GeoWebCache :file:`web.xml` file and add the following code:

   .. code-block:: xml
   
      <context-param>
        <param-name>GEOWEBCACHE_CACHE_DIR</param-name>
        <param-value>PATH</param-value>
      </context-param>

    where ``PATH`` is the location of the cache directory.

3. To set as an operating system environment variable, run one of the the following commands:

   Windows::
   
     > set GEOWEBCACHE_CACHE_DIR=<path>
   
   Linux/OS X::
   
     $ export GEOWEBCACHE_CACHE_DIR=<path>

4. Not recommended: It is possible to set this location directly in the :file:`geowebcache-core-context.xml` file.
   However this file will be replaced each update:

   .. code-block:: xml
   
      <!-- bean id="gwcBlobStore" class="org.geowebcache.storage.blobstore.file.FileBlobStore" destroy-method="destroy">
        <constructor-arg value="/tmp/gwc_blobstore" />
      </bean -->

   making sure to edit the path.  As usual, any changes to the servlet configuration files will require :ref:`configuration.reload`.

.. note:: if ``GEOWEBCACHE_CACHE_DIR`` is not provided by any of the above mentioned methods, the directory will default
    to the temporary storage folder specified by the web application container. (For Tomcat, this is the :file:`temp` directory inside the root.)
    The directory created will be called :file:`geowebcache`.  If this directory is not available, GeoWebCache will attempt to create a new 
    :file:`geowebcache` directory in the location specified by the ``TEMP`` system environment variable. It is **highly** recommended
    to explicitly define the location of the configuration/cache directory.

.. _configuration.storage.blobstore:

BlobStore configuration
-----------------------

A basic installation does not require to configure a blobstore. One will be created automatically following the same cache location lookup
mechanism as for versions prior to 1.8.0, meaning that a file blobstore will be used at the directory defined by the ``GEOWEBCACHE_CACHE_DIR``
application argument.

Starting with 1.8.0, it is possible to configure multiple blobstores, which provides several advantages:

* Allow to decouple the location of the configuration and the storage;
* Allow for multiple cache base directories;
* Allow for alternate storage mechanisms than the current ``FileBlobStore``;
* Allow for different storage mechanisms to coexist;
* Allow to chose which "blob store" to save tiles to on a per "tile layer" basis;
* Allow serving pre-seeded caches directly from S3.

The :file:`geowebcache.xml` file must be edited to configure blob stores. 

The following is the excerpt of the schema definition that allows to configure
blob stores: :download:`BlobStores XML schema <storage_blobstore_schema.txt>`

Between the ``formatModifiers`` and ``gridSets`` elements of the root ``gwcConfiguration`` element, a list of blob stores can be configured as
children of the ``blobStores`` element. For example:

.. code-block:: xml

    <gwcConfiguration>
      ...
      <formatModifiers>...</formatModifiers>
      
      <blobStores>
       <FileBlobStore default="true"><id>default_cache</id><enabled>true</enabled>...</FileBlobStore>
       <S3BlobStore><id>default_cache</id><enabled>true</enabled>...</S3BlobStore>
       <FileBlobStore><id>default_cache</id><enabled>false</enabled>...</FileBlobStore>
      </blobStores>
      
      <gridSets>...</gridSets>
      ...
    </gwcConfiguration>

Common properties
+++++++++++++++++

All blob stores have the *default*, *id*, and *enabled* properties.

* **default** is an optional attribute which defines if the blob store is the *default* one. Only one blob store can have this attribute set to *true*.
  Having more than one blob store with ``default="true"`` will raise an exception at startup time. Yet, if no blob store has ``default="true"``, a
  default ``FileBlobStore`` will be automatically created at the directory specified by the ``GEOWEBCACHE_CACHE_DIR`` application argument for
  backwards compatibility.
* **id** is a **mandatory** string property defining a unique identifier for the blobstore for the geowebcache instance. Not defining a unique id
  for a blobstore, or configuring more than one with the same id, will raise an exception at application startup time. This identifier can then
  be referred to by the ``blobStoreId`` element of a ``wmsLayer`` in the same configuration file, in order to explicitly state which blob store
  to use for a given layer.
* **enabled** is an **optional** attribute that **defaults to true**. If a blobstore is not enabled (i.e. ``<enabled>false</enabled>``), then it cannot
  be used and any attempt to store or retrieve a tile from it will result in a runtime exception making the operation fail. Note that **it is invalid** to
  have the ``default="true"`` and ``<enabled>false</enabled>`` properties at the same time, resulting in a startup failure.

Besides these common properties, each kind of blob store defines its own, as follows:

File Blob Store
+++++++++++++++

The file blob store saves tiles on disk following the traditional geowebcache cache directory layout.

Example:

.. code-block:: xml

    <FileBlobStore default="false">
      <id>defaultCache</id>
      <enabled>false</enabled>
      <baseDirectory>/opt/defaultCache</baseDirectory>
      <fileSystemBlockSize>4096</fileSystemBlockSize>
    </FileBlobStore>

Properties:


* **baseDirectory**: Mandatory. The absolute path for the cache's root directory.
* **fileSystemBlockSize**: Optional, defaults to 4096. A positive integer representing the file system block size (usually 4096, 8292, or 16384, depending on 
  the `file system <http://en.wikipedia.org/wiki/File_system>`_ where the base directory resides.
  This value is used to pad the size of tile files to the actual size of the file on disk before notifying the internal blob store listeners when tiles
  are stored, deleted, or updated. This is useful, for example, for the "disk-quota" subsystem to correctly compute the cache's disk usage.

Amazon Simple Storage Service (S3) Blob Store
+++++++++++++++++++++++++++++++++++++++++++++

The following documentation assumes you're familiar with the `Amazon Simple Storage Service <http://aws.amazon.com/s3/>`_.

This blob store allows to configure a cache for layers on an S3 bucket with the following `TMS <http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification>`_-like
key structure:

    [prefix]/<layer id>/<gridset id>/<format id>/<parameters hash | "default">/<z>/<x>/<y>.<extension>
    
* prefix: if provided in the configuration, it will be used as the "root path" for tile keys. Otherwise the keys will be built starting at the bucket's root.
* layer id: the unique identifier for the layer. Note it equals to the layer name for standalone configured layers, but to the geoserver catalog's object id for GeoServer tile layers.
* gridset id: the name of the gridset of the tile
* format id: the gwc internal name for the tile format. E.g.: ``png``, ``png8``, ``jpeg``, etc.
* parameters hash: if the request that originated that tiles included parameter filters, a unique hash code of the set of parameter filters, otherwise the constant ``default``.
* z: the z ordinate of the tile in the gridset space.
* x: the x ordinate of the tile in the gridset space.
* y: the y ordinate of the tile in the gridset space.
* extension: the file extension associated to the tile format. E.g. ``png``, ``jpeg``, etc. (Note the extension is the same for the ``png`` and ``png8`` formats, for example).

Support for S3-compatible servers other than Amazon is also present.

Configuration example:

.. code-block:: xml

    <S3BlobStore default="false">
      <id>myS3Cache</id>
      <enabled>false</enabled>
      <bucket>put-your-actual-bucket-name-here</bucket>
      <prefix>test-cache</prefix>
      <awsAccessKey>putYourActualAccessKeyHere</awsAccessKey>
      <awsSecretKey>putYourActualSecretKeyHere</awsSecretKey>
      <access>private</access>
      <maxConnections>50</maxConnections>
      <useHTTPS>true</useHTTPS>
      <endpoint>http://putYourServerEndpointHereOrLeaveOutIfUsingAmazon:9000</endpoint>
      <proxyDomain></proxyDomain>
      <proxyWorkstation></proxyWorkstation>
      <proxyHost></proxyHost>
      <proxyPort></proxyPort>
      <proxyUsername></proxyUsername>
      <proxyPassword></proxyPassword>
      <useGzip>true</useGzip>
    </S3BlobStore>


Properties:

* **bucket**: Mandatory. The name of the AWS S3 bucket where to store tiles.
* **prefix**: Optional. A prefix path to use as the "root folder" to store tiles at. For example, if the bucket is ``bucket.gwc.example`` and 
  prefix is "mycache", all tiles will be stored under ``bucket.gwc.example/mycache/{layer name}`` instead of ``bucket.gwc.example/{layer name}``.
* **awsAccessKey**: Mandatory. The public access key the client uses to connect to S3.
* **awsSecretKey**: Mandatory. The secret key the client uses to connect to S3.
* **access**: Optional.  Whether direct access in S3 will be readable by the public or only to the owner of the bucket.  Defaults to public, set to private to disable public access.  
* **maxConnections**: Optional, default: ``50``. Maximum number of concurrent HTTP connections the S3 client may use.
* **useHTTPS**: Optional, default: ``true``. Whether to use HTTPS when connecting to S3 or not.
* **endpoint**: Optional. Endpoint of the server, if using an alternative S3-compatible server instead of Amazon.
* **proxyDomain**: Optional. The Windows domain name for configuring an NTLM proxy. If you are not using a Windows NTLM proxy, you don't need to set this property.
* **proxyWorkstation**: Optional. The Windows domain name for configuring an NTLM proxy. If you are not using a Windows NTLM proxy, you don't need to set this property.
* **proxyHost**: Optional. The proxy host the client will connect through.
* **proxyPort**: Optional. The proxy port the client will connect through.
* **proxyUsername**: Optional. The proxy user name to use if connecting through a proxy.
* **proxyPassword**: Optional. The proxy password to use when connecting through a proxy.
* **useGzip**: Optional, default: ``true``. Whether gzip compression should be used when transferring tiles to/from S3.

**Note**: It is possible to set above properties from environment variable as long as they are of string type. In the example below, The awsAccessKey is set from environment variable named AWS_ACCESS_KEY

.. code-block:: xml

      <awsAccessKey>${AWS_ACCESS_KEY}</awsAccessKey>

Additional Information:
```````````````````````

The S3 objects for tiles are created with public visibility to allow for "standalone" pre-seeded caches to be used directly from S3 without geowebcache
as middleware. In the future this behavior could be disabled through a configuration option.

**Beware of amazon services costs**. Especially in terms of bandwidth usage when serving tiles out of the Amazon cloud, and S3 storage prices. **We haven't conducted
a thorough assessment of costs associated to seeding and serving caches**. Yet we can provide some general purpose advise:

* Do not seed at high zoom levels (except if you know what you're doing). The number of tiles grow exponentially as the zoom level increases.
* Use the tile format that produces the smalles possible tiles. For instance, png8 is a great compromise for quality/size. Keep in mind that the smaller the tiles
  the bigger the size difference between two identical caches on S3 vs a regular file system. The S3 cache takes less space because the actual space used for each
  tile is not padded to a file system block size. For example, the ``topp:states`` layer seeded up to zoom level 10 for EPSG:4326 with png8 format takes roughly
  240MB on an Ext4 file system, and about 21MB on S3.
* Use in-memory caching. When serving S3 tiles from GeoWebcache, you can greately reduce the number of GET requests to S3 by configuring an in-memory cache as
  described in the "In-Memory caching" section bellow. This will allow for frequently requested tiles to be kept in memory instead of retrieved from S3 on each
  call.

The following is an example OpenLayers 3 HTML/JavaScript to set up a map that fetches tiles from a pre-seeded geowebcache layer directly from S3. We're using the typical
GeoServer ``topp:states`` sample layer on a fictitious ``my-geowebcache-bucket`` bucket, using ``test-cache`` as the cache prefix, png8 tile format, and EPSG:4326 CRS.

.. code-block:: html

    <div class="row-fluid">
      <div class="span12">
        <div id="map" class="map"></div>
      </div>
    </div>

.. code-block:: javascript

    var map = new ol.Map({
      target: 'map',
      controls: ol.control.defaults(),
      layers: [
        new ol.layer.Tile({
          source: new ol.source.XYZ({
            projection: "EPSG:4326",
            url: 'http://my-geowebcache-bucket.s3.amazonaws.com/test-cache/topp%3Astates/EPSG%3A4326/png8/default/{z}/{x}/{-y}.png'
          })
        })
      ],
      view: new ol.View({
        projection: "EPSG:4326",
        center: [-104, 39],
        zoom: 2
      })
    });


Google Cloud Storage (GCS) Blob Store
+++++++++++++++++++++++++++++++++++++

This blob store allows to configure a cache for layers on a Google Cloud Storage bucket with the same TMS-like key structure as S3:

    [prefix]/<layer id>/<gridset id>/<format id>/<parameters hash | "default">/<z>/<x>/<y>.<extension>

Configuration example:

.. code-block:: xml

    <GoogleCloudStorageBlobStore default="false">
      <id>myGcsCache</id>
      <enabled>true</enabled>
      <bucket>my-gwc-bucket</bucket>
      <prefix>test-cache</prefix>
      <projectId>my-gcp-project</projectId>
      <useDefaultCredentialsChain>true</useDefaultCredentialsChain>
    </GoogleCloudStorageBlobStore>

Properties:

* **bucket**: Mandatory. The name of the GCS bucket where to store tiles.
* **prefix**: Optional. A prefix path to use as the "root folder" to store tiles at.
* **projectId**: Optional. The GCP project ID. Can be omitted if using service account credentials that already specify the project.
* **quotaProjectId**: Optional. Project to bill for quota when using requester-pays buckets.
* **endpointUrl**: Optional. Custom endpoint URL for use with GCS emulators or compatible services.
* **useDefaultCredentialsChain**: Optional. Set to ``true`` to use Application Default Credentials. This will look for credentials in the following order: environment variable GOOGLE_APPLICATION_CREDENTIALS pointing to a service account key file, GCE/GKE metadata service, or gcloud CLI credentials.
* **apiKey**: Optional. API key for authentication. If both apiKey and useDefaultCredentialsChain are provided, apiKey takes precedence.

**Note**: Like S3, all configuration properties support environment variable expansion using the ``${VARIABLE_NAME}`` syntax:

.. code-block:: xml

      <bucket>${GCS_BUCKET}</bucket>
      <projectId>${GCS_PROJECT_ID}</projectId>

Authentication options:

* **Application Default Credentials** (recommended): Set ``useDefaultCredentialsChain`` to ``true``. This works automatically on GCE/GKE and when GOOGLE_APPLICATION_CREDENTIALS points to a service account key.
* **API Key**: Set the ``apiKey`` property. Less secure, mainly for testing.
* **No auth**: For use with emulators only. Leave both auth options unset.

Implementation notes:

Delete operations run asynchronously in a background thread pool. When deleting tile ranges or layers, tiles are removed in batches using the GCS batch API for efficiency. The thread pool is sized based on available processors and shuts down gracefully on blob store destruction.

Microsoft Azure Blob Store
+++++++++++++++++++++++++++++++++++++++++++++

The following documentation assumes you're familiar with the `Azure BLOB storage <https://azure.microsoft.com/services/storage/blobs/>`_.

This blob store allows to configure a cache for layers on an Azure container with the following `TMS <http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification>`_-like
key structure:

    [prefix]/<layer id>/<gridset id>/<format id>/<parameters hash | "default">/<z>/<x>/<y>.<extension>
    
* prefix: if provided in the configuration, it will be used as the "root path" for tile keys. Otherwise the keys will be built starting at the bucket's root.
* layer id: the unique identifier for the layer. Note it equals to the layer name for standalone configured layers, but to the geoserver catalog's object id for GeoServer tile layers.
* gridset id: the name of the gridset of the tile
* format id: the gwc internal name for the tile format. E.g.: ``png``, ``png8``, ``jpeg``, etc.
* parameters hash: if the request that originated that tiles included parameter filters, a unique hash code of the set of parameter filters, otherwise the constant ``default``.
* z: the z ordinate of the tile in the gridset space.
* x: the x ordinate of the tile in the gridset space.
* y: the y ordinate of the tile in the gridset space.
* extension: the file extension associated to the tile format. E.g. ``png``, ``jpeg``, etc. (Note the extension is the same for the ``png`` and ``png8`` formats, for example).

Configuration example:

.. code-block:: xml

    <AzureBlobStore default="false">
      <id>myAzureCache</id>
      <enabled>false</enabled>
      <container>put-your-actual-container-name-here</container>
      <prefix>test-cache</prefix>
      <accountName>putYourActualAccountNameHere</accountName>
      <accountKey>putYourActualAccountKeyHere</accountKey>
      <maxConnections>100</maxConnections>
      <useHTTPS>true</useHTTPS>
      <serviceURL>http://putYourServerEndpointHereOrLeaveOutIfUsing.blob.core.windows.net</serviceURL>
      <proxyHost></proxyHost>
      <proxyPort></proxyPort>
      <proxyUsername></proxyUsername>
      <proxyPassword></proxyPassword>
    </AzureBlobStore>


Properties:

* **container**: Mandatory. The name of the Azure container where to store tiles. The code will try to create it if missing.
* **prefix**: Optional. A prefix path to use as the "root folder" to store tiles at. For example, if the container is ``gwc.example`` and 
  prefix is "mycache", all tiles will be stored under ``gwc.example/mycache/{layer name}`` instead of ``gwc.example/{layer name}``.
* **accountName**: Mandatory. The account name used to connect to Azure storage (found in the archiving account, choose the account, and then access keys).
* **accountKey**: Mandatory. The secret key the client uses to connect to S3.
* **maxConnections**: Optional, default: ``100``. Maximum number of concurrent HTTP connections the client may use. The more the merrier, as Azure REST API does not have support for bulk deletes, so each tile needs to be deleted in a separate request on cleanup.
* **useHTTPS**: Optional, default: ``true``. Whether to use HTTPS when connecting to Azure or not.
* **serviceURL**: Optional. The full service URL, in case the default one is not suitable. The default is build using the account name, e.g. ``https://accountName.blob.core.windows.net`` 
* **proxyHost**: Optional. The proxy host the client will connect through.
* **proxyPort**: Optional. The proxy port the client will connect through.
* **proxyUsername**: Optional. The proxy user name to use if connecting through a proxy.
* **proxyPassword**: Optional. The proxy password to use when connecting through a proxy.

Unlike S3, access level in Azure can be set at the container level only, so if you desired to pre-seed
a publicly available cache, please create a container that has "public" or "BLOB" access level.
The access level can be modified also after the container creation.

Additional Information:
```````````````````````

The Azure objects for tiles are created with public visibility to allow for "standalone" pre-seeded caches to be used directly from Azure without GeoWebCache
as middleware. If 

**Beware of Azure services costs**. Especially in terms of bandwidth usage when serving tiles out of the Azure cloud, and Azure storage prices. **We haven't conducted
a thorough assessment of costs associated to seeding and serving caches**. Yet we can provide some general purpose advise:

* Do not seed at high zoom levels (except if you know what you're doing). The number of tiles grow exponentially as the zoom level increases.
* Use the tile format that produces the smalles possible tiles. For instance, png8 is a great compromise for quality/size. Keep in mind that the smaller the tiles
  the bigger the size difference between two identical caches on Azure vs a regular file system. The Azure cache takes less space because the actual space used for each
  tile is not padded to a file system block size.
* Use in-memory caching. When serving Azure Blob tiles from GeoWebcache, you can greatly reduce the number of GET requests to Azure by configuring an in-memory cache as
  described in the "In-Memory caching" section below. This will allow for frequently requested tiles to be kept in memory instead of retrieved from Azure on each
  call.

The following is an example OpenLayers 3 HTML/JavaScript to set up a map that fetches tiles from a pre-seeded geowebcache layer directly from Azure, assuming that
the container access level has been set to "public" or "blob", so that direct access to the blobs is possible. We're using the typical
GeoServer ``topp:states`` sample layer on a fictitious ``my-geowebcache-container`` bucket, using ``test-cache`` as the cache prefix, png8 tile format, and EPSG:4326 CRS.

.. code-block:: html

    <div class="row-fluid">
      <div class="span12">
        <div id="map" class="map"></div>
      </div>
    </div>

.. code-block:: javascript

    var map = new ol.Map({
      target: 'map',
      controls: ol.control.defaults(),
      layers: [
        new ol.layer.Tile({
          source: new ol.source.XYZ({
            projection: "EPSG:4326",
            url: 'https://<accountNameHere>.blob.core.windows.net/<containerNameHere>/<prefixIfAny>/<layerId>/EPSG:4326/png8/default/{z}/{x}/{-y}.png'
          })
        })
      ],
      view: new ol.View({
        projection: "EPSG:4326",
        center: [-104, 39],
        zoom: 2
      })
    });
    
The ``prefix`` needs to be filled only if used otherwise that part of the path should be empty.
The ``layerId`` is the layer identifier. In GWC it has been hand-assigned during configuration,
if using GWC inside GeoServer it will be the internal layer identifier, e.g., 
something like ``LayerInfoImpl--5f036b28:16bbda57c0e:-7ffc`` which can be retrieved by checking the 
GeoServer configuration files for the layer in question.

MBTiles Blob Store
++++++++++++++++++

This blob store allow us to store tiles using the `MBTiles <https://github.com/mapbox/mbtiles-spec/blob/master/1.1/spec.md>`_ specification (version 1.1) which defines a schema for storing tiles in an `SQLite <https://www.sqlite.org/>`_ database with some restrictions regarding tiles formats and projections.

MBTiles specification only supports JPEG and PNG formats and projection EPSG:3857 is assumed. The implemented blob store will read and write MBTiles files compliant with the specification but will also be able to write and read MBTiles files that use others formats and projections.

Using the MBTiles blob store will bring several benefits at the cost of some performance loss. The MBTiles storage uses a significantly smaller number of files, which results in easier data handling (e.g., backups, moving tiles between environments). In some cases the stored data will be more compact reducing the size of the data on disk.

When compared to the file blob store this store has two limitations:

* This store does not integrate with disk quota, this is a consequence of using database files.
* **This store cannot be shared among several GeoWebCache instances.**

.. note:: If disk quota is activated the stored stats will not make much sense and will not reflect the actual disk usage, the size of the database files cannot be really controlled.

Database files cannot be managed as simple files. When connections to a database are open the associated file should not be deleted, moved or switched or the database file may become corrupted. Databases files can also become fragmented after deleting an huge amount of data or after frequent inserts, updates or delete operations.

File Path Templates
````````````````````

An MBTiles file will correspond to an SQLite database file. In order to limit the amount of contention on each single database file users will be allowed to decide the granularity of the databases files. When GeoWebCache needs to map a tile to a database file it will only look at the databases files paths, it will not take in account the MBTiles metadata (this is why this store is able to handle others formats and projections).

To configure the databases files granularity the user needs to provide a file path template. The default file path template for the MBTiles blob store is this one:

.. code-block:: none

  {layer}/{grid}{format}{params}/{z}-{x}-{y}.sqlite

This file template will stores all the tiles belonging to a certain layer in a single folder that will contain sub folders for each given format, projection and set of parameters and will group tiles with the same zoom level, column range and row range in a SQLite file. The column and row range values are passed by configuration, by default those values are equal to 250. The provided files paths templates will always be considered relative to the root directory provided as a configuration option.

Follows an example of what the blob store root directory structure may look like when using the default path template:

.. code-block:: none

  .
  |-- nurc_Pk50095
  |   `-- EPSG_4326image_pngnull
  |       |-- 11_2000_1500.sqlite
  |       `-- 12_4250_3000.sqlite
  `-- topp_states
      |-- EPSG_900913image_jpeg7510004a12f49fdd49a2ba366e9c4594be7e4358
      |   |-- 6_250_500.sqlite
      |   `-- 7_0_0.sqlite
      `-- EPSG_900913image_jpegnull
          |-- 3_500_0.sqlite
          |-- 4_0_250.sqlite
          `-- 8_750_500.sqlite

If no parameters were provided *null* string will be used. Is the responsibility of the user to define a file path template that will avoid collisions.

The terms that can be used in the file path template are:

* **grid**: the grid set id
* **layer**: the name of the layer
* **format**: the image format of the tiles
* **params**: parameters unique hash
* **x**: column range, computed based on the column range count configuration property
* **y**: row range, computed based on the row range count configuration property
* **z**: the zoom level

It is also possible to use parameters values, like *style* for example. If the parameter is not present *null* will be used.

.. note:: Characters ``\`` and ``/`` can be used as path separator, they will be translated to the operating system specific one (``\`` for Linux and ``/`` for Windows). Any special char like ``\``, ``/``, ``:`` or empty space used in a term value will be substituted with an underscore.

MBTiles Metadata
`````````````````

A valid MBTiles file will need some metadata, the image format and layer name will be automatically added when an MBTiles file is created. The user can provide the remaining metadata using a properties file whose name must follow this pattern:

.. code-block:: none

  <layerName>.metadata

As an example, to add metadata ``description`` and ``attribution`` entries to layer ``tiger_roads`` a file named ``tiger_roads.properties`` with the following content should be present in the metadata directory:

.. code-block:: none

  description=ny_roads
  attribution=geoserver

The directory that contains this metadata files is defined by a configuration property.

Vector Tile Compression
```````````````````````

Some non-standard MBTiles files contain vector tiles, and these are sometimes compressed using gzip.  A ``gzipVector`` entry to the the store configuration with a value of ``true`` will enable this behaviour.  Raster tiles will not be affected.

Expiration Rules
`````````````````

The MBTiles specification don't give information about when a tile was created. To allow expire rules, an auxiliary table is used to store tile creation time. In the presence of an MBTiles file generated by a third party tool it is assumed that the creation time of a tile was the first time it was accessed. This feature can be activated or deactivated by configuration. Note that this will not break the MBTiles specification compliance.

Eager Truncate
```````````````

When performing a truncate of the cache the store will try to remove the whole database file avoiding to create fragmented space. This is not suitable for all the situations and is highly dependent on the database files granularity. The configuration property ``eagerDelete`` allows the user to disable or deactivate this feature which is disabled by default. 

When a truncate request by tile range is received all the the databases files that contains tiles that belong to the tile range are identified. If eager delete is set to true those databases files are deleted otherwise a single delete query for each file is performed.

Configuration Example
``````````````````````

Follows as an example the default configuration of the MBTiles store:

.. code-block:: xml

  <MbtilesBlobStore default="true">
    <id>mbtiles-store</id>
    <enabled>true</enabled>
    <rootDirectory>/tmp/gwc-mbtiles</rootDirectory>
    <templatePath>{grid}/{layer}/{format}/{params}/{z}/tiles_{x}_{y}.sqlite</templatePath>
    <rowRangeCount>250</rowRangeCount>
    <columnRangeCount>250</columnRangeCount>
    <poolSize>1000</poolSize>
    <poolReaperIntervalMs>500</poolReaperIntervalMs>
    <eagerDelete>false</eagerDelete>
    <useCreateTime>true</useCreateTime>
    <executorConcurrency>5</executorConcurrency>
    <mbtilesMetadataDirectory>/tmp/gwc-mbtiles/layersMetadata</mbtilesMetadataDirectory>
  </MbtilesBlobStore>

The *rootDirectory* property defines the location where all the files produced by this store will be created. The *templatePath* property is used to control the granularity of the database files (see section above). Properties *rowRangeCount* and *columnRangeCount* will be used by the path template to compute tile ranges.

The *poolSize* property allows to control the max number of open database files, when defining this property the user should take in account the number open files allowed by the operating system. The *poolReaperIntervalMs* property controls how often the pool size will be checked to see if some database files connections need to be closed.

Property *eagerDelete* controls how the truncate operation is performed (see section above). The property *useCreateTime* can be used to activate or deactivate the insertion of the tile creation time (see section above). Property *executorConcurrency* controls the parallelism used to perform certain operations, like the truncate operation for example. Property *mbtilesMetadataDirectory* defines the directory where the store will look for user provided MBTiles metadata.

.. note:: Since the connection pool eviction happens at a certain interval, it means that the number of files open concurrently can go above the threshold limit for a certain amount of time.

Replace Operation
``````````````````

As said before, if the cache is running SQLite files cannot be simply switched, first all connections need to be closed. The replace operation was created for this propose. The replace operation will first copy the new file side by side the old one, then block the requests to the old file, close the connections tot he old file, delete the old one, rename the new file to current one, reopen the new db file and start serving requests again. Should be almost instant.

A REST entry point for this operation is available, it will be possible to submit a ZIP file or a single file along with the request. The replace operation can also use an already present file or directory. When using a directory the directory structure will be used to find the destination of each file, all the paths will be assumed to be relative to the store root directory. This means that is possible to replace a store content with another store content (a seeded one for example) by zipping the second store root directory and send it as a replacement.

.. note:: When using a local directory or submitting a zip file all the file present in the directory will be considered.

There is four ways to invoke this operation. Follows an example of all those variants invocations using CURL.

Replace a single file uploading the replacement file:

.. code-block:: none

  curl -u geowebcache:secured -H 'Content-Type: multipart/form-data'
    -F "file=@tiles_0_0.sqlite"
    -F "destination=EPSG_4326/sf_restricted/image_png/null/10/tiles_0_0.sqlite"
    -F "layer=sf:restricted"
    -XPOST 'http://localhost:8080/geowebcache/rest/sqlite/replace'

Replace a single file using a file already present on the system:

.. code-block:: none

  curl -u geowebcache:secured -H 'Content-Type: multipart/form-data'
    -F "source=/tmp/tiles_0_0.sqlite"
    -F "destination=EPSG_4326/sf_restricted/image_png/null/10/tiles_0_0.sqlite"
    -F "layer=sf:restricted"
    -XPOST 'http://localhost:8080/geowebcache/rest/sqlite/replace'

Replace multiple files uploading a ZIP file:

.. code-block:: none

  curl -u geowebcache:secured -H 'Content-Type: multipart/form-data'
    -F "file=@tiles.zip"
    -F "layer=sf:restricted"
    -XPOST 'http://localhost:8080/geowebcache/rest/sqlite/replace'

Replace multiple files using a directory already present on the system:

.. code-block:: none

  curl -u geowebcache:secured -H 'Content-Type: multipart/form-data'
    -F "source=/tmp/tiles"
    -F "layer=sf:restricted"
    -XPOST 'http://localhost:8080/geowebcache/rest/sqlite/replace'

The *layer* parameter identifies the layer whose associated blob store content should be replaced. The *file* parameter is used to upload a single file or a ZIP file. The *source* parameter is used to reference an already present file or directory. The *destination* parameter is used to define the file that should be replaced with the provided file.

This are the only valid combinations of this parameters other combinations will ignore some of the provided parameters or will throw an exception.

In-Memory caching
-----------------

Default **blobstore** can be changed with a new one called **MemoryBlobStore**, which allows in memory tile caching. The **MemoryBlobStore** is a wrapper of a **blobstore** 
implementation, which can be the default one(*FileBlobStore*) or another one. For using the new **blobstore** implementation, the user have to 
modify the **blobstore** bean associated to the **gwcStorageBroker** bean (inside the Application Context file *geowebcache-core-context.xml*) by setting *gwcMemoryBlobStore* 
instead of *gwcBlobStore*.

The configuration of a MemoryBlobStore requires a *blobstore* to wrap and a **CacheProvider** object. This one provides the caching mechanism for saving input data in memory. 
User can define different caching objects but can only inject one of them inside the **MemoryBlobStore**.  More information about the **CacheProvider** can be found in the next section.

An example of MemoryBlobStore configuration can be found beow:

.. code-block:: xml

  <bean id="gwcMemoryBlobStore" class="org.geowebcache.storage.blobstore.memory.MemoryBlobStore" destroy-method="destroy">
    <property name="store" ref="gwcBlobStore" />
	<!-- "cacheProviderName" is optional. It is the name of the bean associated to the cacheProvider object used by this MemoryBlobStore-->
    <property name="cacheBeanName" value="cacheProviderName" /> 
	<!-- "cacheProvider" is optional. It is the Reference to a CacheProvider bean in the application context. -->
	<property name="cacheProvider" ref="ExampleCacheProvider" /> 
  </bean>  

.. note:: Note that *cacheProviderName*/*cacheProvider* cannote be used together, if a *cacheProvider* is defined, the *cacheProviderName* is not considered. If *cacheProviderName*/*cacheProvider* are not defined, the **MemoryBlobStore** will internally search for a suitable **CacheProvider**.

CacheProvider configuration
+++++++++++++++++++++++++++

A **CacheProvider** object should be configured with an input object called **CacheConfiguration**. **CacheConfiguration** parameters are:

	* *hardMemoryLimit* : which is the cache size in Mb
	* *policy* : which can be LRU, LFU, EXPIRE_AFTER_WRITE, EXPIRE_AFTER_ACCESS, NULL 
	* *evitionTime* : which is the cache eviction time in seconds
	* *concurrencyLevel* : which is the cache concurrency level
	
These parameters must be defined as properties in the **cacheConfiguration** bean in the Spring Application Context (like *geowebcache-core-context.xml*).

At the time of writing there are two implementations of the **CacheProvider** interface:

	* **GuavaCacheProvider**
	* **HazelcastCacheProvider**
	
GuavaCacheProvider
``````````````````````
**GuavaCacheProvider** provides local in-memory caching by using a `Guava <https://code.google.com/p/guava-libraries/wiki/CachesExplained>`_ *Cache* for storing the various GWC Tiles locally on the machine. For configuring a **GuavaCacheProvider**
the user must create a new bean in the Application Context file (like *geowebcache-core-context.xml*) and then add a reference to a **CacheConfiguration** instance.

Here is an example of configuration:

.. code-block:: xml

  <bean id="cacheConfiguration" class="org.geowebcache.storage.blobstore.memory.CacheConfiguration">
    <property name="hardMemoryLimit" value="64"/> <!-- 64 Mb -->
	<property name="policy" value="EXPIRE_AFTER_ACCESS"/> <!-- Cache Eviction Policy is EXPIRE_AFTER_ACCESS. Other values are EXPIRE_AFTER_WRITE, NULL(LRU eviction based on cache size) -->
	<property name="evitionTime" value="240"/> <!-- Eviction time is 240 seconds -->
	<property name="concurrencyLevel" value="4"/> <!-- Concurrency Level of the cache is 4 -->
  </bean>
  
  <bean id="guavaCacheProvider" class="org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider">
    <property name="configuration" ref="cacheConfiguration"/> <!-- Setting of the configuration -->
  </bean>


HazelcastCacheProvider
``````````````````````
**HazelcastCacheProvider** is useful for implementing distributed in memory caching for clustering. It internally uses `Hazelcast <http://docs.hazelcast.org/docs/3.3/manual/html/>`_ for handling distributed caching.
The **HazelcastCacheProvider** configuration requires another object called **HazelcastLoader**. This object accepts an Hazelcast instance or loads a file called *hazelcast.xml* from a proper directory defined 
by the property "hazelcast.config.dir". If none of them is present, the CacheProvider object cannot be used.

The user must follow these rules for configuring the Hazelcast instance:

	#. The Hazelcast configuration requires a Map object with name *CacheProviderMap*
	#. Map eviction policy must be *LRU* or *LFU*
	#. Map configuration must have a fixed size defined in Mb
	#. Map configuration must have **USED_HEAP_SIZE** as *MaxSizePolicy* 
	
Here the user can find both examples:

	* From *hazelcast.xml*:
		
		.. code-block:: xml
			
			<hazelcast xsi:schemaLocation="http://www.hazelcast.com/schema/config hazelcast-config-2.3.xsd"
					   xmlns="http://www.hazelcast.com/schema/config"
					   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
			  <group>
				<name>cacheCluster</name>
				<password>geoserverCache</password>
			  </group>

			  <network>
				<!--
					Typical usage: multicast enabled with port auto-increment enabled
					or tcp-ip enabled with port auto-increment disabled. Note that you 
					must choose between multicast and tcp-ip. Another option could be
					aws, but will not be described here.
				
				-->
				<port auto-increment="false">5701</port>
					<join>
						 <multicast enabled="false">
							<multicast-group>224.2.2.3</multicast-group>
							<multicast-port>54327</multicast-port>
						</multicast>
						<tcp-ip enabled="true">
							<interface>192.168.1.32</interface>     
							<interface>192.168.1.110</interface> 
						</tcp-ip>
					</join>
			  </network>
			  <map name="CacheProviderMap">
					<eviction-policy>LRU</eviction-policy>
					<max-size policy="USED_HEAP_SIZE">16</max-size>
			  </map>

			</hazelcast>
			
		And the related application context will be:
		
		.. code-block:: xml
		
			<bean id="HazelCastLoader1"
				class="org.geowebcache.storage.blobstore.memory.distributed.HazelcastLoader">
			</bean>				
			
			<bean id="HazelCastCacheProvider1"
				class="org.geowebcache.storage.blobstore.memory.distributed.HazelcastCacheProvider">
				<constructor-arg ref="HazelCastLoader1" />
			</bean>		

		.. note:: Remember that in this case the user must define the *hazelcast.config.dir* property when starting the application.
	
	* From application context (See Hazelcast documentation for more info):
	
		.. code-block:: xml
		
				<hz:hazelcast id="instance1">
					<hz:config>
						<hz:group name="dev" password="password" />
						<hz:network port="5701" port-auto-increment="true">
							<hz:join>
								<hz:multicast enabled="true" multicast-group="224.2.2.3"
									multicast-port="54327" />
							<hz:tcp-ip enabled="false">
							  <hz:members>10.10.1.2, 10.10.1.3</hz:members>
							</hz:tcp-ip>									
							</hz:join>
						</hz:network>
						<hz:map name="CacheProviderMap" max-size="16" eviction-policy="LRU"
							max-size-policy="USED_HEAP_SIZE" />
					</hz:config>
				</hz:hazelcast>
				
				<bean id="HazelCastLoader1"
					class="org.geowebcache.storage.blobstore.memory.distributed.HazelcastLoader">
					<property name="instance" ref="instance1" />
				</bean>				
				
				<bean id="HazelCastCacheProvider1"
					class="org.geowebcache.storage.blobstore.memory.distributed.HazelcastCacheProvider">
					<constructor-arg ref="HazelCastLoader1" />
				</bean>

Optional configuration parameters
``````````````````````````````````	
In this section are described other available configuration parameters to configure:

* Cache expiration time:

  .. code-block:: xml
   
     <map name="CacheProviderMap">
     ...
   
         <time-to-live-seconds>0</time-to-live-seconds>
         <max-idle-seconds>0</max-idle-seconds>
   
     </map>
     
  Where *time-to-live-seconds* indicates how many seconds an entry can stay in cache and *max-idle-seconds* indicates how many seconds an entry may be not accessed before being evicted.

* Near Cache.

  .. code-block:: xml

     <map name="CacheProviderMap">
     ...
     <near-cache>
       <!--
         Same configuration parameters of the Hazelcast Map. Note that size indicates the maximum number of 
         entries in the near cache. A value of Integer.MAX_VALUE indicates no limit on the maximum 
         size.
       -->
       <max-size>5000</max-size>
       <time-to-live-seconds>0</time-to-live-seconds>
       <max-idle-seconds>60</max-idle-seconds>
       <eviction-policy>LRU</eviction-policy>

       <!--
         Indicates if a cached entry can be evicted if the same value is modified in the Hazelcast Map. Default is true.
       -->
       <invalidate-on-change>true</invalidate-on-change>

       <!--
         Indicates if local entries must be cached. Default is false.
       -->
       <cache-local-entries>false</cache-local-entries>
     </near-cache>
     
     </map>	

  Near Cache is a local cache for each cluster instance which is used for caching entries in the other cluster instances. This behaviour avoids to request those entries each time by executing a remote call. This feature could be helpful in order to improve Hazelcast Cache performances.

  .. note:: A value of *max-size* bigger or equal to Integer.MAX_VALUE cannot be used in order to avoid an uncontrollable growth of the cache size.

OpenStack Swift (Swift) Blob Store
+++++++++++++++++++++++++++++++++++++++++++++

The following documentation assumes you're familiar with the `Openstack Swift Documentation <https://docs.openstack.org/swift/latest/>`_.

This blob store allows to configure a cache for layers using a Swift container with the following `TMS <http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification>`_-like
key structure:

    [prefix]/<layer id>/<gridset id>/<format id>/<parameters hash | "default">/<z>/<x>/<y>.<extension>
    
* prefix: if provided in the configuration, it will be used as the "root path" for tile keys. Otherwise the keys will be built starting at the bucket's root.
* layer id: the unique identifier for the layer. Note it equals to the layer name for standalone configured layers, but to the geoserver catalog's object id for GeoServer tile layers.
* gridset id: the name of the gridset of the tile
* format id: the gwc internal name for the tile format. E.g.: ``png``, ``png8``, ``jpeg``, etc.
* parameters hash: if the request that originated that tiles included parameter filters, a unique hash code of the set of parameter filters, otherwise the constant ``default``.
* z: the z ordinate of the tile in the gridset space.
* x: the x ordinate of the tile in the gridset space.
* y: the y ordinate of the tile in the gridset space.
* extension: the file extension associated to the tile format. E.g. ``png``, ``jpeg``, etc. (Note the extension is the same for the ``png`` and ``png8`` formats, for example).

Configuration example:

.. code-block:: xml

    <SwiftBlobStore default="true">
        <id>ObjectStorageCache</id>
        <enabled>true</enabled>
        <container>put-your-actual-container-name-here</container>
        <prefix>test-cache</prefix>
        <endpoint>endpoint</endpoint>
        <provider>openstack-swift</provider>
        <region>put-region-here</region>
        <keystoneVersion>3</keystoneVersion>
        <keystoneScope>project</keystoneScope>
        <keystoneDomainName>Default</keystoneDomainName>
        <identity>put-tenant-name-here:put-username-here</identity>
        <password>put-password-here</password>
    </SwiftBlobStore>

Properties:

* **container**: Mandatory. The name of the Swift container where to store tiles.
* **prefix**: Optional. A prefix path to use as the "root folder" to store tiles at. For example, if the bucket is ``bucket.gwc.example`` and 
  prefix is "mycache", all tiles will be stored under ``bucket.gwc.example/mycache/{layer name}`` instead of ``bucket.gwc.example/{layer name}``.
* **endpoint**: Manditory. Endpoint of the server
* **provider**: Mandatory. Jclouds provider (shouldn't need modifying)
* **region**: Mandatory. Swift region for container.
* **keystoneVersion**: Mandatory. Keystone version
* **keystoneScope**: Optional. For scoped keystone authorization (project or domain scoped)
* **keystoneDomainName**: Optional. Keystone domain name (if different than the user domain)
* **identity**: Mandatory. Identity used to authenticate with the swift API (format - tenantName:username)
* **password**: Mandatory. Password used to authenticate with the swift API.

Additional Information:
```````````````````````
**Some links that might be useful:**

* The package makes use of the open source multi-cloud toolkit `jclouds <https://jclouds.apache.org/>`_ 
* Jclouds documentation for `getting started with Openstack <https://jclouds.apache.org/guides/openstack/>`_
* Jclouds documentation for `OpenStack Keystone V3 Support <https://jclouds.apache.org/blog/2018/01/16/keystone-v3/>`_ used in config 
