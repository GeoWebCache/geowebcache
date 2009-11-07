.. _storage:

Storage
=======

Please refer to Quickstart on how to find and edit geowebcache-servlet.xml. Each element in this file is refered to as a "bean", they are individually refered to by their "id" attributes.

Components
----------

GeoWebCache's storage subsystem has two components. The first is a storage mechanism for blobs, such as tiles. The second is an optional storage mechanism for metainformation about tiles, such as when each tile was created and what the size is. These two components are refered to as the blobstore and the metastore, and this is what they look like inside the configuration file:

``<bean id="gwcMetaStore" class="org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend" destroy-method="destroy">
  <constructor-arg ref="gwcDefaultStorageFinder" />
</bean>``

``<bean id="gwcBlobStore" class="org.geowebcache.storage.blobstore.file.FileBlobStore" destroy-method="destroy">
  <constructor-arg ref="gwcDefaultStorageFinder" />
</bean>``

By default, the storage location is the temporary storage folder specified by the servlet container, commonly <Tomcat directory>\\temp\\geowebcache. If this directory is not available, GeoWebCache will attempt to create a new ``geowebcache`` directory in the location specified by the ``TEMP`` environment variable.

Inside the ``geowebcache`` directory, the program will create a single directory for the metastore, the default is ``meta_jdbc_h2``. Additionally, the blobstore will create one directory per layer, where the name of the directory is a filtered version of the layer name.


Configuring the Location
------------------------

If you wish to configure the location for one or both of these storage mechanisms manually, you can do so by commenting out the bean above. Replace ``<bean`` with ``<!-- bean`` and ``</bean`` with ``</bean -->``. There are two templates inside geowebcache-servlet.xml

Metastore:

``<bean id="gwcMetaStore" class="org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend" destroy-method="destroy">
  <constructor-arg value="org.h2.Driver" />
  <constructor-arg value="jdbc:h2:file:c:/meta_dir/h2_metastore;TRACE_LEVEL_FILE=0" />
  <constructor-arg value="username" />
  <constructor-arg value="password" />
</bean>``

The only supported database is currently H2, please make a feature request if you need to run on a different backend. The database does not have to exist, the only requirement is that the directory after ``file:`` exists and is writable by the servlet process. The default is to run in embedded mode, but H2 can also run as a standalone TCP server. Please refer to the H2 documentation for how to do this and connection parameters.

If you are moving an existing database to a new location, the default username for H2 is ``sa`` and with an empty password. Java developers can also replace JDBCMetaBackend with another class.

Blobstore:

``<bean id="gwcBlobStore" class="org.geowebcache.storage.blobstore.file.FileBlobStore" destroy-method="destroy">
  <constructor-arg value="c:/blob_dir" />
</bean>``

The directory must exist and be writable by the servlet process.


Disabling the MetaStore
-----------------------

The metastore is currently only required if you use cache expiration (default is not to), or you use parameter filters. In the future it will be used more, for example to do least-recently-used expiration, to limit the size of the cache and to provide statistics.

The performance overhead of the metastore is neglible, and speeds up certain operations, but under some conditions it can be easier to run GeoWebCache without a metastore. To do this:
1) Find the bean definition of gwcStorageBroker
2) Replace ``<constructor-arg ref="gwcMetaStore" />`` with ``<constructor-arg><null /></constructor-arg>``
3) Comment out the bean for gwcMetaStore as well
