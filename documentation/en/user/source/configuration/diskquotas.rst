.. _configuration.diskquotas:

Disk Quotas
===========

Since disk usage increases geometrically by zoom level, one single seeding task could fill up an entire storage device.  Because of this, GeoWebCache employs a disk quota system where one can specify the maximum amount of disk space to use for a particular layer, as well as logic on how to proceed when that quota is reached.  There are two different policies for managing the disk quotas:  Least Frequently Used (LFU) and Least Recently Used (LRU).

.. note:: It is not currently possible to set a disk quota for the entire GeoWebCache storage system.  It is also not possible to mix LFU and LRU on a single layer.

Enabling disk quotas
--------------------

**Disk quotas are disabled by default.**  Before setting any disk quotas, it is necessary to first enable the disk quota subsystem.  To do this, look in the :file:`geowebcache-servlet.xml` file, located in the :file:`WEB-INF` directory inside the GeoWebCache root directory.  Uncomment the following line:

.. code-block:: xml

   <!--import resource="geowebcache-diskquota-context.xml"/-->

Restart GeoWebCache for the change to take effect.

A file called :file:`geowebcache-diskquota.xml` should be created in the GeoWebCache cache directory.  All disk quota policy settings will be contained in this file.  You can also create this file using the following template as a guide.  

.. code-block:: xml

    <?xml version="1.0" encoding="utf-8"?>
    <gwcQuotaConfiguration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://geowebcache.org/schema/1.2.2/diskquota org/geowebcache/config/geowebcache-diskquota.xsd"
      xmlns="http://geowebcache.org/schema/1.2.2/diskquota">

      <diskBlockSize>
        <!-- ... -->
      </diskBlockSize>
      <cacheCleanUpFrequency>
        <!-- ... -->
      </cacheCleanUpFrequency>
      <cacheCleanUpUnits>
        <!-- ... -->
      </cacheCleanUpUnits>
      <maxConcurrentCleanUps>
        <!-- ... -->
      </maxConcurrentCleanUps>

      <layerQuotas>
        <LayerQuota>
          <!-- First layer -->
        </LayerQuota>
        <!-- Other layers -->
      </layerQuotas>

    </gwcQuotaConfiguration>


Disk quota policies
-------------------

When a disk quota is reached, further tiles will be saved at the expense of other tiles which will be truncated.  The **Least Frequently Used (LFU)** policy will analyze the tile metastore and delete the tiles that have been accessed the least often.  The **Least Recently Used (LRU)** policy will analyze the tile metastore and delete the tiles that haven't been accessed in the longest amount of time.

Both policies are set in exactly the same way, with only the policy name changing.  The policies operate on a per-layer basis. 

The following information is needed:

* Layer name
* Policy
* Disk quota (maximum size)

The layer name must match the name as given in :file:`geowebcache.xml` (the ``<name>`` of the ``<wmsLayer>``), the policy is one of ``LFU`` or ``LRU``, and the disk quota requires both magnitude and units.  The magnitude can be any number (although when used in conjunction with units the value will usually be fairly small).  The units can be any one of bytes (B), kibibytes (KiB), mebibytes (MiB), gibibytes (GiB), tebibytes (TiB), etc.

.. note:: The above units are not typos.  A kibibyte, valued at 1024 bytes, is different from a kilobyte, valued at 1000 bytes.  The same holds for mebibytes (1024 KiB), gibibytes (1024 MiB), and tebibytes (1024 GiB).

The syntax for a single disk quota policy is:

.. code-block:: xml

    <LayerQuota>
      <layer>LAYER_NAME</layer>
      <expirationPolicyName>POLICY</expirationPolicyName>
      <quota>
        <value>DISK_QUOTA_VALUE</value>
        <units>DISK_QUOTA_UNITS</units>
      </quota>
    </LayerQuota>

For example, setting a LFU policy on the ``topp:states`` layer, with a disk quota of 100 Mebibytes would look like:

.. code-block:: xml

  <layerQuotas>
    <LayerQuota>
      <layer>topp:states</layer>
      <expirationPolicyName>LFU</expirationPolicyName>
      <quota>
        <value>100</value>
        <units>MiB</units>
      </quota>
    </LayerQuota>


Disk block size
---------------

GeoWebCache doesn't know about the file system block size , so this will need to be set via the ``<diskBlockSize>`` tag.  Add this value to :file:`geowebcache-diskquota.xml`, just beneath the namespace information:

.. code-block:: xml

   <diskBlockSize>#</diskBlockSize>

Where ``#`` is the block size in bytes (such as 4096, 8192, 16384, etc.).

Polling time
------------

GeoWebCache will not truncate the cache as soon as the disk quota is exceeded.  Instead, it polls the store at given intervals, with this time interval set in :file:`geowebcache-diskquota.xml`.  There are two tags, ``<cacheCleanUpFrequency>`` and ``<cacheCleanUpUnits>`` that determine the time interval.  The first is a numeric identifier (such as 10) and the second gives the time units (as in ``SECONDS``, ``MINUTES``, ``HOURS``, or ``DAYS``).  To poll the store every five minutes, the code would be:

.. code-block:: xml

   <cacheCleanUpFrequency>5</cacheCleanUpFrequency>
   <cacheCleanUpUnits>MINUTES</cacheCleanUpUnits>

Other settings
--------------

It is possible to set the amount of threads to use when processing the disk quota.  This is set using the ``<maxConcurrentCleanUps>`` tag, for instance, to use three threads:

.. code-block:: xml

   <maxConcurrentCleanUps>3</maxConcurrentCleanUps>
