.. _production:

Production
==========

This section discuesses running GeoWebCache in a production environment.

This section is a work in progress, and will eventually be extended to cover topics such as

* Service architecture
* Apache Portable Runtime
* Java Advanced Imaging / ImageIO
* External tools for optimizing tiles
* Profiling
* Links for tips on improving WMS performance

Environment
-----------

Java settings
~~~~~~~~~~~~~

The speed of GeoWebCache depends in part on the chosen Java Runtime Environment (JRE). For best performance use a Java 6 JRE. If not possible, use a Java 5 JRE. JREs other than those released by Oracle may work correctly, but are neither tested nor supported.

GeoWebCache does not need a lot of heap memory assigned to the JVM. Usually 512M is just fine if its the only web application running on the servlet container.

Servlet container settings
~~~~~~~~~~~~~~~~~~~~~~~~~~

Depending on the power of your hardware set up and your expected user load, consider increasing the number of concurrent connections the servlet container is allowed to handle. For a high-end set up you can even set it to 2000. In Tomcat, you can modify the ``maxThreads`` attribute for the ``tomcatThreadPool Executor`` in :file:`server.xml`.

Hardware considerations
-----------------------

Having substantial (spare) RAM is of great help. This is for the Operating System disk block cache, not the JVM heap.

Regular SATA drives or RAID arrays work fine.  If you use NAS or SAN then things become a bit more complicated, since these devices often have a lot of overhead due to capabilities like deduplication, shared locks and so forth.  Sometimes they kill the disk block cache altogether. 

Keeping an eye on the drive access LED indicators might be a good idea (if you are not using SSDs), but also the IO Wait percentange in utilities like ``top`` might help diagnose I/O bottlenecks.

Operating system settings
-------------------------

Open files
~~~~~~~~~~

GeoWebCache will probably open lots of files (for short periods of time) from the file system as it opens the tile files for delivery to the client.  Therefore it is recommended that you set your operating system settings to allow for at least 4 times as many open files as the number of maximum concurrent connections configured in your servlet container.

File System Performance
~~~~~~~~~~~~~~~~~~~~~~~

Storing tiles on a NFS mount point is known to be slow.  Ext3, Ext4 and XFS are known to perform well.  btrfs should be ok too, as well as ZFS, though this hasn't been tested.  

If possible, turn off the file system's "last access time" (atime) as it avoids writing to the file system for every tile read (see also `Criticism of atime <http://en.wikipedia.org/wiki/Atime_(Unix)#Criticism_of_atime>`_).

File system block size
~~~~~~~~~~~~~~~~~~~~~~

Since the cache stores thousands or even millions of files, the file system block size is of great importance for optimizing the storage space that's actually used in the cache. If that might be an issue, consider tuning your file system block size.  Modern file systems usually have a default block size of 16K, which should be fine for most situations. If your avergate tile is a small 8-bit PNG, you may want to use a 8K block size. If its a large JPEG you may want to increase the block size to 64K.

Depending on the average tile size, setting a block size that's too small may lead to too much fragmentation, and a block size that's too large will lead to wasted storage space.


Seeding and truncating the cache
--------------------------------

On the demo page on the :ref:`webinterface` there is a link for seeding each layer. Seeding and truncating layers with :ref:`configuration.layers.parameterfilters` is currently not supported, except for the default values.

You can also manage the cache via the :ref:`rest`.


