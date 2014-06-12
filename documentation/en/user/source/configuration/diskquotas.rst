.. _configuration.diskquotas:

Disk Quotas
===========

Since disk usage increases geometrically by zoom level, one single seeding task could fill up an entire storage device.  Because of this, GeoWebCache employs a disk quota system where one can specify the maximum amount of disk space to use for a particular layer or for the entire set of layers (the "Global Quota"), as well as logic on how to proceed when that quota is reached.  There are two different policies for managing the disk quotas:  Least Frequently Used (LFU) and Least Recently Used (LRU).

Disk quotas are managed by the `gwc-diskquota-<version>.jar` library, which uses an embedded ``Berkeley DB Java Edition`` database in a directory called `diskquota_page_store`. This directory is created under the cache directory, next to the meta-store database directory, and is used to store tile usage statistics as well as to record cache disk usage. This database is internally referred to as the `page store`, because it stores usage statistics in `pages` of tiles of an automatically calculated dimension for each tile set zoom level.
Whenever a tile is requested to GeoWebCache, the page for that tile is updated with information about the frequency of use and last access time for that page of tiles, in order to feed the LFU and LRU expiration policies, respectively.

Whenever a tile is stored, deleted, or updated, a single database record representing the `tile set` that tile belongs to is updated to reflect its disk usage, calculating the actual disk space taken by that tile based on the configured `diskBlockSize` property, as shown in the example configuration file bellow.

The page store is independent of the meta store in order to keep usage statistics for the layer independently of the life cycle of individual times, as the whole point of the expiration policies is to act upon the usage history for each layer, independently of how often individual tiles are `truncated` and `seeded`.

Enabling disk quotas
--------------------

**Disk quotas are disabled by default.**  Before setting any disk quotas, it is necessary to first enable the disk quota subsystem.  To do this, a file called :file:`geowebcache-diskquota.xml` should be created in the GeoWebCache cache directory. If you don't create it beforehand, a default one will be created at start up time, with the `enabled` property set to `false`.

All disk quota policy settings will be contained in this file.  You can also create this file using the following template as a guide.  

.. code-block:: xml

    <?xml version="1.0" encoding="utf-8"?>
    <gwcQuotaConfiguration>
      <enabled>false</enabled>
      <diskBlockSize>4096</diskBlockSize>
      <cacheCleanUpFrequency>10</cacheCleanUpFrequency>
      <cacheCleanUpUnits>SECONDS</cacheCleanUpUnits>
      <maxConcurrentCleanUps>2</maxConcurrentCleanUps>
      <globalExpirationPolicyName>LFU</globalExpirationPolicyName>
      <globalQuota>
        <value>512</value>
        <units>GiB</units>
      </globalQuota>
      <layerQuotas> <!-- optional -->
        <LayerQuota>
          <layer>topp:states</layer>
          <expirationPolicyName>LRU</expirationPolicyName>
          <quota>
            <value>100</value>
            <units>GiB</value>
          </quota>
        </LayerQuota>
        <!-- Other layers -->
      </layerQuotas>
    </gwcQuotaConfiguration>

.. note:: The `enabled` configuration property only accounts to whether the layers should be automatically truncated when the disk quota is exceeded, and to do so the disk usage statistics kept being recorded even if `enabled` is set to false, in order to avoid the potentially very expensive task of re-traversing the full tile cache if the page store gets out of date.

Disabling disk quotas
---------------------

It may be the case that you want to completely disable the disk quota subsystem so that it doesn't even gather disk usage statistics in the background.

Completely disabling the disk quota subsystem can be achieved through an environment variable that is read when the web application starts up.

To do so set the ``GWC_DISKQUOTA_DISABLED`` environment variable to ``true`` as you do with the other GeoWebCache environment variables and start GeoWebCache. That is, either by setting an Operating System environment variable, a Java VM system property, or a servlet context parameter in GeoWebCache's ``WEB-INF/web.xml`` file.

For example, if you are using the bash shell in GNU/Linux and deploying GWC into Apache Tomcat, you can do:

    $export CATALINA_OPTS="-DGWC_DISKQUOTA_DISABLED=true` && bin/startup.sh


Expiration policies
-------------------

When a disk quota is reached, further tiles will be saved at the expense of other tiles which will be truncated.  The **Least Frequently Used (LFU)** policy will analyze the disk quota page store and delete the pages of tiles that have been accessed the least often.  The **Least Recently Used (LRU)** policy will analyze the diskquota page store and delete the tiles that haven't been accessed in the longest amount of time.

Both policies are set in exactly the same way, with only the policy name changing.  The policies operate both globally and on a per-layer basis. 

.. code-block:: xml

      <globalExpirationPolicyName>LFU</globalExpirationPolicyName>

Global Quota
------------

Quotas can be assigned to individual layers and to the whole cache. The sum of quotas assigned to individual layers shall not exceed the `Global Quota`, as they are considered part of the global quota and the difference between the global quota and the sum of explicitly set layer quotas is the shared quota left to all non explicitly configured layers.

When a single layer quota is exceeded, the single layer the quota refers to is truncated as explained above.
When the global quota is exceeded, first any explicitly configured quota is enforced, and then the global quota is enforced acting upon all the remaining layers until the global quota is reached back.

.. code-block:: xml

      <globalQuota>
        <value>512</value>
        <units>GiB</units>
      </globalQuota>


Individual Layer Quotas
-----------------------

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

Disk quota storage
------------------

The disk quota subystem defaults to use an embedded Berkeley DB whose storage is located in the cache directory, there is however also the possibility of using either an embedded H2 database, against storing information in the cache directory, or a standard Oracle or PostgreSQL database.

In order to switch from the Berkeley DB to the embedded H2 storage the :file:`geowebcache-diskquota.xml` must contain the ``quotaStore`` element set to ``H2``:

.. code-block:: xml

    <?xml version="1.0" encoding="utf-8"?>
    <gwcQuotaConfiguration>
      <enabled>false</enabled>
      <quotaStore>H2</quotaStore>
      ...

    </gwcQuotaConfiguration>


In order to switch from the Berkeley DB to the freeform JDBC sources the :file:`geowebcache-diskquota.xml` must contain the ``quotaStore`` element set to ``JDBC``:

.. code-block:: xml

    <?xml version="1.0" encoding="utf-8"?>
    <gwcQuotaConfiguration>
      <enabled>false</enabled>
      <quotaStore>JDBC</quotaStore>
      ...

    </gwcQuotaConfiguration>

In this case a separate file, :file:`geowebcache-diskquota-jdbc.xml` will contain the configuration for the chosen database containing the chosen DBMS dialect, at the time of writing the possible values are ``H2``, ``Oracle``, ``PostgreSQL``.

The connection pool can be either provided locally, in such case a DBCP based connection pool will be instantiated, or provided via JNDI.
The JDNI configuration is as simple as follows:

.. code-block:: xml

    <gwcJdbcConfiguration>
      <dialect>Oracle</dialect>
      <JNDISource>java:comp/env/jdbc/oralocal</JNDISource>
    </gwcJdbcConfiguration>

The local connection pool can instead be configured by specifying the following:

.. code-block:: xml

    <gwcJdbcConfiguration>
      <dialect>PostgreSQL</dialect>
      <connectionPool>
        <driver>org.postgresql.Driver</driver>
        <url>jdbc:postgresql:gttest</url>
        <username>cite</username>
        <password>cite</password>
        <minConnections>1</minConnections>
        <maxConnections>10</maxConnections>
        <fetchSize>1000</fetchSize>
        <connectionTimeout>50</connectionTimeout>
        <validationQuery>select 1</validationQuery>
        <maxOpenPreparedStatements>50</maxOpenPreparedStatements>
      </connectionPool>
    </gwcJdbcConfiguration>

Disk quota schema
-----------------

The schema used by a JDBC Disk Quota store specifies that a layer's name can be no longer than 128 characters.  If the database was created using GWC 1.5.2 or earlier then the limit will be 64 characters instead. If you have very long layer names and get SQLException messages in your log, it may be because your layer names are longer than this maximum.

If this limit is too low, it can be changed using your database's administrative tools.  You need to increase the size of 4 fields on two tables, all by the same amount.  ``layer_name`` and ``key`` on the ``tileset`` table, and ``tileset_id`` and ``key`` on the ``tilepage`` table.  For details, see the documentation for the specific database you are using.

 
