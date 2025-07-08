.. _production:

Production
==========

While many optimizations are configured for GeoWebCache by default, here are some additional considerations when operating in a production enviornment..

Operating Environment
---------------------

Java Settings
+++++++++++++

GeoWebCache speed depends in part on the chosen Java Runtime Environment (JRE). GeoWebCache is compiled for Java 17, and is tested with both OpenJDK 17 and OpenJDK 11. JREs other than these may work correctly, but are not tested nor supported.

GeoWebCache does not need a lot of heap memory assigned to the JVM. Usually 512M is just fine if its the only web application running on the servlet container.

Java Advanced Imaging / ImageIO
+++++++++++++++++++++++++++++++

GeoWebCache uses the Java Advanced Imaging library (for image processing) and ImageIO for image encoding/decoding.

Servlet container settings
++++++++++++++++++++++++++
Depending on the power of your hardware setup and your expected user load, consider increasing the number of concurrent connections the servlet container is allowed to handle. For a high end set up you can even set it to 2000. In Tomcat, that's performed by modifying the maxThreads attribute for the tomcatThreadPool Executor in server.xml.

Hardware considerations
-----------------------
Having substantial (spare) RAM is of great help. Not for the JVM Heap, but for the Operating System's disk block cache.

Regular SATA drives or RAID arrays work fine, if you use NAS or SAN then things become a bit more tricky. Often these devices have a ton of overhead due to capabilities like deduplication, shared locks and so forth, and sometimes they kill the disk block cache altogether. 

Keeping an eye on the hard-drive access LED indicators might be a good idea (if you are not using SSDs), but also the IO Wait percentange in utilities like top might help diagnose I/O bottlenecks.


Operating system settings
-------------------------

Open Files
++++++++++
GeoWebCache will probably open lots of files from the file system the cache is stored on, depending on the request load, though through short periods of time, as it opens the tile files to deliver their contents to the client. Therefore it is recommended that you set your Operating System settings to allow for at least 4 times as much open files the number of maximum concurrent connections configured in your servlet container.

File System Performance
+++++++++++++++++++++++
Storing tiles on a NFS mount point is known to be slow. Ext3, Ext4 and XFS are known to perform well. btrfs should be ok too, as well as ZFS, though we didn't actually test on them. Depending on the average tile size, a too small block size may lead to too much fragmentation, and a too big block size to too much wasted storage space.

If possible, turn off File System's last access time (atime) as it avoids writing to the File System for every tile read (check `Criticism of atime <http://en.wikipedia.org/wiki/Atime_(Unix)#Criticism_of_atime>`_ ).

File System Block Size
++++++++++++++++++++++
Since the Tile Cache stores thousands or even millions of files, the File System block size is of great importance for optimizing the actually used storage space for the cache. If that could be an issue, consider tuning your File System block size. Modern file systems usually have a default block size of 16K, which should be fine for most situations. If your average tile is a small 8-bit png you may want to use a 8K block size. If its a large JPEG you may want to increase the block size to 64K.

If the block size is too large, it's more likely that more disk space is gonna be wasted (since a tile file will use an integral number of blocks on disk, as for any file in your file system).
If the block size is too small, performance will probably degrade as more I/O operations will be needed to read a single file.


Seeding and Truncating the Cache
--------------------------------

From the page that lists the demos there is a link for seeding each layer. This page presents a form with the seed/truncate options for the layer, as well as a summary of all the running tasks, if any.
The following options can be set to submit a seed task:

* Number of threads to use
* Type of operation (seed|re-seed|truncate)
* Grid Set
* Image format
* Start and stop zoom levels
* Modifiable parameters (for any configured parameter filter)
* Bounding box

Seeding and truncating with parameter filters is supported since version 1.2.5. If a layer is configured with parameter filters a dynamically generated input will be presented for each parameter filter, with the parameter filter's default value.
Every combination of different parameter filter values will generage a different tile set.


Seed Failure Tolerance
++++++++++++++++++++++

As of version 1.2.5, it is possible to control how GWC behaves in the event that a backend (WMS for example) request fails during seeding, by using the following environment variables:

* ``GWC_SEED_RETRY_COUNT`` : specifies how many times to retry a failed request for each tile being seeded. Use ``0`` for no retries, or any higher number. Defaults to ``0``, meaning no retries are performed. Defaults to -1, which also implies that defaults to the other two variables (for backwards compatibility).
* ``GWC_SEED_RETRY_WAIT`` : specifies how much to wait before each retry upon a failure to seed a tile, in milliseconds. Defaults to ``100ms``
* ``GWC_SEED_ABORT_LIMIT`` : specifies the aggregated number of failures that a group of seeding threads should reach before aborting the seeding operation as a whole. This value is shared by all the threads launched as a single thread group; so if the value is ``10`` and you launch a seed task with four threads, when ``10`` failures are reached by all or any of those four threads the four threads will abort the seeding task. The default is ``1000``.

These applicaiton properties can be established by any of the following ways, in order of precedence:

- As a Java environment variable: for example `java -DGWC_SEED_RETRY_COUNT=5 ...`
- As a Servlet context parameter in the web application's ``WEB-INF/web.xml`` configuration file. for example:
 
  .. code-block:: xml
  
    <context-param>
      <!-- milliseconds between each retry upon a backend request failure -->
      <param-name>GWC_SEED_RETRY_WAIT</param-name>
      <param-value>500</param-value>
    </context-param>
  
- As a System environment variable: `export GWC_SEED_ABORT_LIMIT=2000; <your usual command to run GWC here>` (or for Tomcat, use the Tomcat's `CATALINA_OPTS` in Tomcat's `bin/catalina.sh` as this: `CATALINA_OPTS="GWC_SEED_ABORT_LIMIT=2000 GWC_SEED_RETRY_COUNT=2`


Resource Allocation
-------------------

Also see https://github.com/GeoWebCache/geowebcache/wiki/Estimating-the-number-of-tiles-and-size-on-disk for table that can be used to estimate how much storage you need and how long seeding will take


Clustering
----------

GeoWebCache is quite an efficient piece of software, as such it normally does not need clustering for performance reasons (GeoWebCache running on an old notebook with a seeded tile can literally flood a gigabit line), but it may still make sense to cluster GeoWebCache for high availability reasons. 

Before the GeoWebCache 1.4.x clustering GeoWebCache instances required:
* turning off the disk quota subsystem
* turning off the metastore subsystem
* setting up clustering in active/passing mode

Starting with 1.4.0 the metastore subsystem has been removed and replaced with full on disk metadata, which makes it possible to keep on using tile expiration and layer parameters even with clustering active, and the disk quota subsystem allows connection to a central database. Moreover, the tile creation workflow has been modified to allow for an active/active setup, meaning several GWC instances can now share the same cache directory without risks of file corruption or incomplete tiles being served back to clients.

Each GWC internally synchs to avoid two requests end up working on the same meta tile, but by default separate GWC instances do not and will end up wasting time. In case you want to make sure two separate instances do not end up working on the same metatile you have two options:

   * make it unlikely for two instances to work on the same metatile by using sticky sessions in the load balancer (very often requests for the tiles making up a metatile originate from the same client)
   * switch to file based locking so that the GWC instances properly synch up activity

In order to activate file based locking you will have to chage the geowebcache.xml configuration and activate the "NIO locks" as follows::

      ..
      <backendTimeout>120</backendTimeout>
      <lockProvider>nioLock</lockProvider>
      <serviceInformation>
        <title>GeoWebCache</title>
      ...

A new ``lockfiles`` directory will be created in the cache directory where all GeoWebCache instances will create the lock files for the time it takes to request and write out a metatile (a separate file will be used for each metatile).

When setting up active/active clustering the disk quota subsystem will have to be configured in order to use an external JDBC database so that all nodes share the same disk quota metadata.
