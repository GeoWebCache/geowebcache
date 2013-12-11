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


PNGCrush
--------

If `PNGCrush<http://pmt.sourceforge.net/pngcrush/>`_ is installed, GeoWebCach can apply it to tiles as they are cached.  This increases overhead while caching, but the optimized PNGs will take be smaller and reduce bandwidth usage in serving them.

To enable PNGCrush, set ``PNGCRUSH_PATH`` to the path of the PNGCrush executable.  It can be set the same way as ``GEOWEBCACHE_CACHE_DIR`` above.  If it is empty, PNGCrush will not be used.

The command line parameters can be set with ``PNGCRUSH_OPTIONS`` which defaults to ``-q``.  The directory used to write out to files before optimizing them can be set with ``OPTIMIZATION_STAGING_DIR`` and by default a directory will be created inside ``GEOWEBCACHE_CACHE_DIR``.  A memory based filesystem like ``tmpfs`` or ``/dev/shm`` will reduce the overhead of disk IO.
