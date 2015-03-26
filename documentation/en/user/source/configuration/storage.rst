.. _configuration.storage:

Storage
=======

Cache
-----

.. note:: The cache is sometimes referred to as the "blobstore".

The cache is a directory structure consisting of various image files organized by layer and zoom level.  By default, the cache is stored in the temporary storage folder specified by the web application container.  (For Tomcat, this is the :file:`temp` directory inside the root.)   The directory created will be called :file:`geowebcache`.  If this directory is not available, GeoWebCache will attempt to create a new :file:`geowebcache` directory in the location specified by the ``TEMP`` system environment variable.

There are a few ways to change the location of the cache:

* JVM system environment variable
* Servlet context parameteter
* Operating system environment variable

The variable in all cases is defined as ``GEOWEBCACHE_CACHE_DIR``.

To set as a JVM system environment variable, add the parameter ``-DGEOWEBCACHE_CACHE_DIR=<path>`` to your servlet startup script.  In Tomcat, this can be added to the Java Options (JAVA_OPTS) variable in the startup script.

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

Configuration File
------------------

The configuration file, :file:`geowebcache.xml`, will be looked for or created in the cache directory by default.  A separate location can be set with the ``GEOWEBCACHE_CACHE_DIR`` variable or property in the same way as decribed above for ``GEOWEBCACHE_CACHE_DIR``.

BlobStore configuration
-----------------------

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

