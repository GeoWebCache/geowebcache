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
* Azure blob store, Swift blob store: additional storage backends described below.

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

In-memory tile cache
--------------------

The in-memory tile cache keeps the most requested tiles in memory, in front of the blob stores. It is meant for layers whose tiles
expire quickly and are requested by many clients at once, where reading each tile from disk or from a cloud store costs more than
rendering time saved. In particular, it allows to share the tiles among all the nodes of a cluster, so that a tile rendered by one
node is served by every other node until it expires.

The cache is configured once in :file:`geowebcache.xml`, with the ``memoryCache`` element after ``blobStores``, or through the
:ref:`REST API <rest.memorycache>`, and used by the layers that set ``memoryCached`` to ``true``. For example:

.. code-block:: xml

    <memoryCache>
      <enabled>true</enabled>
      <timeToLive>300</timeToLive>
      <passthrough>true</passthrough>
      <statsInterval>300</statsInterval>
      <backend class="heapKeyValueStore">
        <maxSizeMB>64</maxSizeMB>
      </backend>
    </memoryCache>

* **enabled**: whether the cache is used, defaults to ``false``.
* **timeToLive**: seconds a tile stays in the cache when its layer sets no ``expireCache``, defaults to 300. A layer ``expireCache``
  applies instead, whether shorter or longer. A tile read from the blob store with passthrough on is kept for what is left of
  the layer ``expireCache``, and at most ``timeToLive``, so that a read racing a truncate cannot bring back a removed tile for
  longer than that.
* **passthrough**: whether tiles are also written to, and read from, the blob store. Defaults to ``true``. When ``false``, tiles live in
  the cache only, and an expired or evicted tile is rendered again. Truncating a tile, an area or a gridset then leaves the blob store
  alone, so tiles it still holds from before are kept: truncate the whole layer before turning ``passthrough`` back on.
* **statsInterval**: seconds between two statistics lines per layer in the log, defaults to 300. ``0`` turns them off.
* **backend**: where the tiles are kept, named by the ``class`` attribute. Defaults to ``heapKeyValueStore``. When the module of the backend
  is removed, GeoWebCache still starts: the cache is unusable, with the reason in the log, truncates run on the blob store only, and the
  backend settings are kept as they are, so that installing the module again restores them.

Each layer opts in with ``memoryCached``, off by default, placed before the layer ``name``:

.. code-block:: xml

    <wmsLayer>
      <memoryCached>true</memoryCached>
      <name>topp:states</name>
      ...
    </wmsLayer>

**Nodes sharing one** :file:`geowebcache.xml` **read the** ``memoryCache`` **settings when they start or reload**: after changing them,
reload or restart every other node. It is to be noted that until then, another node saving its configuration (a blob store or a
gridset change) writes its old ``memoryCache`` settings back over the change.

Layer metadata and parameter mappings always stay in the blob store. The cache only holds tiles.

When the backend cannot be reached, tiles are served without the cache, and the log reports when the cache goes down and when
it comes back. A tile request never fails because of the cache. While the cache is down, tile requests do not wait for it: each node
tries it again from time to time, from every second at the start of the outage up to every 30 seconds for long ones. A cache that keeps
going down and up again is reported once with its error, then with a short "unreachable again" line each time.

A truncate of a layer using the cache removes its tiles from both the cache and the blob store. **While the cache is down, or when it
fails to remove the tiles, the truncate is not run at all**, blob store included, and reports an error: retry it once the cache is back,
since the tiles cached before the outage are served again when it recovers. Deletes of single tiles or bounding boxes, as disk quota
runs, are the exception with passthrough on: they still delete from the blob store, so that disk quota frees space during a long outage,
and the copies cached before it are served until they expire. The log reports it once per outage. If the cache fails only after the blob store was truncated, an error is reported and cached tiles may stay stale up to
their time to live. Removing a layer always deletes its tiles from the blob store, even with the cache down; its cached tiles then expire
with their time to live. Layers not using the cache are truncated in the blob store as usual, whatever the state of the cache.

Heap backend
++++++++++++

The ``heapKeyValueStore`` backend keeps tiles in the memory of each node, **not shared** with other nodes. ``maxSizeMB`` (defaults to 64)
bounds the memory used, the least used tiles are dropped first.

Hazelcast backends
++++++++++++++++++

The Hazelcast backends keep tiles in a `Hazelcast <https://hazelcast.com/>`_ map, shared by all the nodes. There are two of them:

* ``hazelcastClientKeyValueStore`` connects to a separate Hazelcast cluster, which holds the tiles. Nodes stay small and stateless, and the
  cluster is sized and restarted on its own.
* ``hazelcastEmbeddedKeyValueStore`` makes each node a member of the cluster, holding a share of the tiles in its own memory. There is no
  separate service to run, at the cost of a larger memory footprint per node.

**Hazelcast open source has no authentication nor encryption**. The cluster must only be reachable from the GeoWebCache nodes, for example
on a private network or with network policies, as anyone reaching it can read and change the tiles.

Client backend
~~~~~~~~~~~~~~

.. code-block:: xml

    <backend class="hazelcastClientKeyValueStore">
      <clusterName>dev</clusterName>
      <members>hazelcast-1:5701, hazelcast-2:5701</members>
      <mapName>gwc-tiles</mapName>
    </backend>

* **clusterName**: the name of the cluster, defaults to ``gwc``.
* **members**: comma separated ``host:port`` list of cluster members to connect to, defaults to ``localhost:5701``. One reachable member
  is enough, the client then finds the others.
* **mapName**: the map holding the tiles, defaults to ``gwc-tiles``. It must be declared on the cluster, see below.

**clusterName and members apply on restart only**: the client connects once, the first time the cache is used, and keeps that
connection until GeoWebCache stops. Saving new values, or reloading the configuration, does not change it. ``mapName`` applies on save.

GeoWebCache starts even when the cluster is down. Tiles are then served without the cache, and the client keeps trying to connect in the
background, waiting up to 30 seconds between attempts. Reading or writing a tile gives up after 2 seconds, and the cache is then treated
as down; each cluster call of a truncate gives up after 10 seconds, and the truncate then reports an error. The limits can be changed with
the ``GWC_MEMORYCACHE_CALL_TIMEOUT`` and ``GWC_MEMORYCACHE_BULK_TIMEOUT`` system properties, servlet context parameters or environment
variables, in milliseconds. Copying a tile read from the blob store into the cache, with passthrough on, follows the tile limit too, and
is skipped while too many copies are still waiting on a member that stopped answering. The cluster-wide locks have no such limit: they
wait until the connection is declared dead, 60 seconds by default.

For settings not covered above, such as discovery or timeouts, a :file:`hazelcast-client.xml` file can be placed in the same directory as
:file:`geowebcache.xml`. When present, it **replaces** ``clusterName``, ``members`` and the connection defaults, following the
`Hazelcast client configuration <https://docs.hazelcast.com/hazelcast/5.7/clients/java#configuring-java-client>`_. It is to be noted that
the client always starts in the background and retries forever, whatever the file says, so that an unreachable cluster never stops
GeoWebCache: a finite ``cluster-connect-timeout-millis`` is ignored, with a warning in the log. ``mapName`` is still taken from
:file:`geowebcache.xml`. The file also keeps the 5 seconds request timeout and the retry ``multiplier`` (2) and ``jitter`` (0.2) of
the cache, unless it sets ``hazelcast.client.invocation.timeout.seconds``, ``multiplier`` or ``jitter`` itself. Changes to the file
apply on restart only.

Embedded backend
~~~~~~~~~~~~~~~~

.. code-block:: xml

    <backend class="hazelcastEmbeddedKeyValueStore">
      <mapName>gwc-tiles</mapName>
    </backend>

* **mapName**: the map holding the tiles, defaults to ``gwc-tiles``.

The member is configured by a :file:`hazelcast.xml` file, **mandatory**, in the same directory as :file:`geowebcache.xml`, following the
`Hazelcast member configuration <https://docs.hazelcast.com/hazelcast/5.7/configuration/configuring-declaratively>`_. Changes to the file
apply on restart.

It is to be noted that the member joins the cluster in the background when the cache is first used. Tile requests do not wait for it, they
are served without the cache until the member has joined, and the log reports the cache unreachable then reachable again. A missing
file, or a file not declaring the map, leaves the cache unusable, with the reason in the log, while tiles keep being served without it.
A member that fails to start, or stops later, for example after running out of memory or through a cluster shutdown, is started again
when the cache is next tried, at most every 30 seconds.

The following example fits a Kubernetes deployment, where members find each other through a service. The pods need permission to read
endpoints and pods in their namespace:

.. code-block:: xml

    <hazelcast xmlns="http://www.hazelcast.com/schema/config">
      <cluster-name>gwc</cluster-name>
      <network>
        <join>
          <multicast enabled="false"/>
          <kubernetes enabled="true">
            <namespace>gwc</namespace>
            <service-name>gwc-hazelcast</service-name>
          </kubernetes>
        </join>
      </network>
      <map name="gwc-tiles">
        <backup-count>1</backup-count>
        <eviction eviction-policy="LRU" max-size-policy="USED_HEAP_PERCENTAGE" size="25"/>
      </map>
    </hazelcast>

Declaring the map
~~~~~~~~~~~~~~~~~

With both backends, the map must be declared in the cluster configuration with an eviction policy and a size bound. **A map without
them grows until the members run out of memory**, as Hazelcast keeps every entry by default. The embedded backend refuses to start on an
undeclared map. The client backend cannot check it, so the cluster configuration must be reviewed by hand.

The time to live does not need to be declared, as each tile is stored with its own.

``backup-count`` sets how many other members keep a copy of each tile. With ``1``, losing a member loses no tiles, at the cost of twice
the memory. With ``0``, tiles of a lost member are rendered again.

The first truncate adds a sorted index on the map keys, so that removing the tiles of a layer, gridset or parameter set looks up the
matching keys instead of reading every tile on the members. The index costs some memory per tile on the members and a little time on
every write, so size the members for it: an entry count limit does not account for it. To keep the cluster configuration under your
control, declare it with the map instead:

.. code-block:: xml

    <map name="gwc-tiles">
      <indexes>
        <index type="SORTED">
          <attributes>
            <attribute>__key</attribute>
          </attributes>
        </index>
      </indexes>
    </map>

An index added by GeoWebCache is lost when the whole cluster restarts, and added again by the next truncate. If it cannot be added, for
example when the cluster configuration refuses it, truncates still run without it, reading every tile on the members, with a warning in
the log, and the next truncate tries again.

Truncating a bounding box reads the keys of each affected zoom level, for one parameter set and format, into the memory of the node doing
it, and removes the tiles inside the box. **Above 100,000 tiles in a zoom level it removes the whole zoom level instead**, with a line in
the log, so that the node memory stays bounded: the extra tiles are rendered again when requested. The limit can be changed with the
``GWC_MEMORYCACHE_MATCHING_LIMIT`` system property, servlet context parameter or environment variable, ``0`` always removing the whole
zoom level; it applies when the cache settings are next saved or on restart.

Cluster-wide tile locks
~~~~~~~~~~~~~~~~~~~~~~~

When a tile is missing, a node renders its whole metatile, and other requests for the same metatile on that node wait for it. Separate
nodes do not: when several of them miss the same metatile at once, for example right after its tiles expired, each of them renders it.
With a Hazelcast backend, the nodes can lock on the cluster instead, so that one node renders the metatile and the others wait, then serve
its tiles from the shared cache. Name ``hazelcastLock`` as the lock provider in :file:`geowebcache.xml`, after ``backendTimeout``:

.. code-block:: xml

    <backendTimeout>120</backendTimeout>
    <lockProvider>hazelcastLock</lockProvider>

* **It needs the cache enabled with a Hazelcast backend**, as the locks are kept on the same cluster, in a map named after the tile map
  with a ``.locks`` suffix, ``gwc-tiles.locks`` by default, which needs no declaration. Otherwise each node locks on its own, with a
  warning in the log. Switching the cache to another backend takes the nodes back to their own locks as soon as the settings are saved.
* A node never fails a request because of these locks. When the cluster cannot be reached, or while the cache considers it down after a
  failed call, nodes lock on their own at once until it is back, and a metatile may then be rendered once per node. A node waits for a lock at most the number of seconds set by the ``GWC_LOCK_TIMEOUT``
  system property, 120 by default, then goes on anyway with a warning in the log. A node stopping while it holds a lock keeps it at most
  that long.
* It is to be noted that the blob stores use the same lock provider. **The S3 blob store holds a lock for the whole of a bulk delete**,
  often longer than ``GWC_LOCK_TIMEOUT``, so the lock can expire under it and another node can start the same delete meanwhile.
