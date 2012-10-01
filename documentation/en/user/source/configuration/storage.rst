.. _configuration.storage:

Storage
=======

Cache
-----

.. note:: The cache is sometimes referred to as the "blobstore".

The cache is a directory structure consisting of various image files organized by layer and zoom level.  By default, the cache is stored in the temporary storage folder specified by the web application container.  (For Tomcat, this is the :file:`temp` directory inside the root.)   The driectory created will be called :file:`geowebcache`.  If this directory is not available, GeoWebCache will attempt to create a new :file:`geowebcache` directory in the location specified by the ``TEMP`` system environment variable.

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
