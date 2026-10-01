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
