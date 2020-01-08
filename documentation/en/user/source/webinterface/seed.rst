.. _webinterface.seed:

Seed Pages
==========

For each layer known to GeoWebCache, there is a corresponding Seed page allowing to start or manage seed tasks.  These pages are front-ends for the :ref:`rest`.

The URL format for the Seed pages is::

  http://<GEOWEBCACHE_URL>/rest/seed/<LAYER_NAME>

Starting a seed task
--------------------

The interface can start one of the three :ref:`concepts.operations`:  **seed**, **reseed**, or **truncate**.

To create a new seed task, fill out the form with the following information:

.. list-table::
   :widths: 25 75
   :header-rows: 1

   * - Field
     - Description
   * - :guilabel:`Number of threads to use`
     - This refers to processor management of the task.  A good rule of thumb is to set this value to two times (2x) the number of cores in your system.
   * - :guilabel:`Type of operation`
     - This is one of the **seed**, **reseed**, or **truncate** operations described above.  
   * - :guilabel:`Grid set`
     - The name of the any valid `grid set <concepts.gridsets>` as defined for a given layer.
   * - :guilabel:`Format`
     - Any valid image format as defined for that layer.
   * - :guilabel:`Zoom start`
     - The starting/minimum zoom level for the seed task.  Zoom level 00 is typically the first (smallest) zoom level.
   * - :guilabel:`Zoom stop`
     - The final/maximum zoom level for the seed task.
   * - :guilabel:`Bounding box`
     - An optional subset of the layer's maximum extent, useful for seeding only certain (more important) areas.  Values are given in the units of the grid set.  If ommitted, the layer's maximum exten will be assumed.
   * - :guilabel:`Tile failure retries`
     - Number of times a tile creation should be retried, before giving up. Defaults to zero. Set it to -1 to have the seeding thread stop all its activities at the first failure.
   * - :guilabel:`Pause before retry (ms)`
     - Time, in milliseconds, GeoWebCache will wait before retrying to compute a failed tile. This allows the system to recover to temporary/contextual failures.
   * - :guilabel:`Total failures before aborting`
     - Time, in milliseconds, GeoWebCache will wait before retrying to compute a failed tile. This allows the system to recover to temporary/contextual failures.

  

When ready to start the task, click :guilabel:`Submit`.

.. warning:: Some seed tasks can take a **very** long time, and can easily fill up your disk.

Managing a seed task
--------------------

When a seed task is ongoing, returning to the layer's Seed page will display the current status of the task, including projected duration and number of tiles, with details for each thread (if more than one).  Threads can be terminated by clicking the :guilabel:`Kill Thread` button next to the thread status.

