.. _configuration.storage:

Storage
=======

The storage subsystem of GeoWebCache is made up of two components.  The first is a storage mechanism for tiles, called the **cache**. The second is an (optional) storage mechanism for information about those tiles, such as when each tile was created and what its size is, called the **metastore**.


Cache
-----

.. note:: The cache is sometimes referred to as the "blobstore".

The cache is a directory structure consisting of various image files organized by layer and zoom level.  By default, the cache is stored in the temporary storage folder specified by the web application conatiner.  (For Tomcat, this is the :file:`temp` directory inside the root.)   The driectory created will be called :file:`geowebcache`.  If this directory is not available, GeoWebCache will attempt to create a new :file:`geowebcache` directory in the location specified by the ``TEMP`` system environment variable.

There are a few ways to change the location of the cache:

* JVM system environment variable
* Servlet context parameteter
* Operating system environment variable

The variable in all cases is known as ``GEOWEBCACHE_CACHE_DIR``.

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


Metastore
---------

The metastore is a database that contains information about the tiles in the cache.  It is stored in the same directory as the cache, and consists of a small H2 database in a directory called :file:`meta_jdbc_h2`.

The metastore is recommended since it allows for cache expiration, disk quotas, parameter filters, and more, but it is optional.  To turn off the metastore, you need to set the ``GWC_METASTORE_DISABLED`` variable to be "TRUE".  This can be done in the same way as described above for setting the ``GEOWEBCACHE_CACHE_DIR`` variable:

* JVM system environment variable
* As a servlet context parameteter
* As an operating system environment variable


Jobstore
---------

The jobstore is a database that contains information about the jobs that have been executed or are scheduled in the system. The job store also keeps track of information about jobs as they are running, and saves progress on a regular bases so that if something goes wrong GeoWebCache can log information about the job and try to recover.

By default the JobStore does not save to disk and is kept in memory only. This means that if for some reason GeoWebCache is stopped and started all job information will be lost. To change this and any other JobStore settings, find the :file:`geowebcache-core-context.xml` file and adjust the following block of code in that file:

.. code-block:: xml

  <bean id="gwcJobStore" class="org.geowebcache.storage.jdbc.jobstore.JDBCJobBackend" destroy-method="destroy">

    ...

    <constructor-arg>
      <description>Set the job store to run in memory only</description>
      <value>TRUE</value>
    </constructor-arg>
  </bean>