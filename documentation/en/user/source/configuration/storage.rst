.. _configuration.storage:

Storage
=======

.. note:: The cache is the physical location of the layers tiles, whether it is on the local file system, an S3 bucket, a pure in-memory cache, 
    or anything else. A "blobstore" is a software component that provides the operations to store and retrieve tiles to and from a given
    storage mechanism.


Cache
-----

Starting with version 1.8.0, there are two types of persistent storage mechanisms for tiles:

* File blob store: stores tiles in a directory structure consisting of various image files organized by layer and zoom level.  
* S3 blob store: stores tiles in an `Amazon Simple Storage Service <http://aws.amazon.com/s3/>`_ bucket, as individual "objects" following a 
  `TMS <http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification>`_-like key structure.

Zero or more blobstores can be configured in the configuration file to store tiles at different locations and on different storage back-ends.
One of the configured blobstores will be the **default** one. Meaning that it will be used to store the tiles of every layer whose configuration
does not explicitly indicate which blobstore shall be used.

.. note:: **there will always be a "default" blobstore**. If a blobstore to be used by default is not explicitly configured, one will
   be created automatically following the legacy cache location lookup mechanism used in versions prior to 1.8.0.
 
Configuration File
------------------

The location of the configuration file, :file:`geowebcache.xml`, will be defined by the ``GEOWEBCACHE_CACHE_DIR`` application argument.

There are a few ways to define this argument:

* JVM system environment variable
* Servlet context parameteter
* Operating system environment variable

The variable in all cases is defined as ``GEOWEBCACHE_CACHE_DIR``.

To set as a JVM system environment variable, add the parameter ``-DGEOWEBCACHE_CACHE_DIR=<path>`` to your servlet startup script.  
In Tomcat, this can be added to the Java Options (JAVA_OPTS) variable in the startup script.

To set as a servlet context parameter, edit the GeoWebCache :file:`web.xml` file and add the following code:

.. code-block:: xml

   <context-param>
     <param-name>GEOWEBCACHE_CACHE_DIR</param-name>
     <param-value>PATH</param-value>
   </context-param>

where ``PATH`` is the location of the cache directory.

To set as an operating system environment variable, run one of the the following commands:

Windows::

  > set GEOWEBCACHE_CACHE_DIR=<path>

Linux/OS X::

  $ export GEOWEBCACHE_CACHE_DIR=<path>

Finally, although not recommended, it is possible to set this location directly in the :file:`geowebcache-core-context.xml` file.  Uncomment this code:

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

    [prefix]/<layer name>/<gridset id>/<format id>/<parameters hash | "default">/<z>/<x>/<y>.<extension>
    
* prefix: if provided in the configuration, it will be used as the "root path" for tile keys. Otherwise the keys will be built starting at the bucket's root.
* layer name: the name of the layer
* gridset id: the name of the gridset of the tile
* format id: the gwc internal name for the tile format. E.g.: ``png``, ``png8``, ``jpeg``, etc.
* parameters hash: if the request that originated that tiles included parameter filters, a unique hash code of the set of parameter filters, otherwise the constant ``default``.
* z: the z ordinate of the tile in the gridset space.
* x: the x ordinate of the tile in the gridset space.
* y: the y ordinate of the tile in the gridset space.
* extension: the file extension associated to the tile format. E.g. ``png``, ``jpeg``, etc. (Note the extension is the same for the ``png`` and ``png8`` formats, for example).

Configuration example:

.. code-block:: xml

    <S3BlobStore default="false">
      <id>myS3Cache</id>
      <enabled>false</enabled>
      <bucket>put-your-actual-bucket-name-here</bucket>
      <prefix>test-cache</prefix>
      <awsAccessKey>putYourActualAccessKeyHere</awsAccessKey>
      <awsSecretKey>putYourActualSecretKeyHere</awsSecretKey>
      <maxConnections>50</maxConnections>
      <useHTTPS>true</useHTTPS>
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
* **maxConnections**: Optional, default: ``50``. Maximum number of concurrent HTTP connections the S3 client may use.
* **useHTTPS**: Optional, default: ``true``. Whether to use HTTPS when connecting to S3 or not.
* **proxyDomain**: Optional. The Windows domain name for configuring an NTLM proxy. If you are not using a Windows NTLM proxy, you don't need to set this property.
* **proxyWorkstation**: Optional. The Windows domain name for configuring an NTLM proxy. If you are not using a Windows NTLM proxy, you don't need to set this property.
* **proxyHost**: Optional. The proxy host the client will connect through.
* **proxyPort**: Optional. The proxy port the client will connect through.
* **proxyUsername**: Optional. The proxy user name to use if connecting through a proxy.
* **proxyPassword**: Optional. The proxy password to use when connecting through a proxy.
* **useGzip**: Optional, default: ``true``. Whether gzip compression should be used when transferring tiles to/from S3.

Additional Information:
=======================


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

