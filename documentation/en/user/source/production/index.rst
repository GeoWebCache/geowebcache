.. _production:

Production
==========

This section is work in progress, and will eventually be extended to cover topics such as

* Service architecture
* Apache Portable Runtime
* Java Advanced Imaging / ImageIO
* External tools for optimizing tiles
* Profiling
* Links for tips on improving WMS performance

Operating Environment
---------------------
Java Settings
+++++++++++++

GeoWebCache speed depends in part on the chosen Java Runtime Environment (JRE). For best performance use a Java 6 JRE. If not possible, use a Java 5 JRE. JREs other than those released by Sun may work correctly, but are not tested nor supported.

GeoWebCache does not need a lot of heap memory assigned to the JVM. Usually 512M is just fine if its the only web application running on the servlet container.

Servlet container settings
++++++++++++++++++++++++++
Depending on the power of your hardware set up and your expected user load, consider increasing the number of concurrent connections the servlet container is allowed to handle. For a high end set up you can even set it to 2000. In tomcat, that's performed by modifying the maxThreads attribute for the tomcatThreadPool Executor in server.xml.

Hardware considerations
-----------------------
Having substantial (spare) RAM is of great help. Not for the JVM Heap, but for the Operating System's disk block cache.

Regular SATA drives or RAID arrays work fine, if you use NAS or SAN then things become a bit more tricky. Often these devices have a ton of overhead due to capabilities like deduplication, shared locks and so forth, and sometimes they kill the disk block cache altogether. 

Keeping an eye on the harddrive access LED indicators might be a good idea (if you are not using SSDs), but also the IO Wait percentange in utilities like top might help diagnose I/O bottlenecks.


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


Resource Allocation
-------------------

Also see http://geowebcache.org/trac/wiki/resources for tools that can be used to estimate how much storage you need and how long seeding will take

