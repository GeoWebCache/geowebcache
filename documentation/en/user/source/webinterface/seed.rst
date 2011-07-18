.. _webinterface.seed:

Seed Pages
==========

For each layer known to GeoWebCache, there is a corresponding Seed page that allows seed tasks o be started or managed.  These pages are effectively front-ends for the :ref:`rest`.

The URL format for the Seed pages are::

  http://<GEOWEBCACHE_URL>/rest/seed/<LAYER_NAME>

An example of the seed form can be seen here: :download:`Seed Form Example <img/seedform.png>`


Starting a seed job
-------------------

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
     - The name of any valid :ref:`grid set <configuration.layers.gridsets>` as defined for a given layer.
   * - :guilabel:`Throughput`
     - Maximum number of requests per second this task is allowed to make. Helps ensure the backend service doesn't get flooded with requests. Note that this throttles the number of requests, not tiles.
   * - :guilabel:`Schedule`
     - A seed job can be scheduled to begin at a later time or on a regular basis. A `CRON <http://en.wikipedia.org/wiki/Cron>`_ string is used to set the schedule.
   * - :guilabel:`Format`
     - Any valid image format as defined for that layer.
   * - :guilabel:`Zoom start`
     - The starting/minimum zoom level for the seed task.  Zoom level 00 is typically the first (smallest) zoom level.
   * - :guilabel:`Zoom stop`
     - The final/maximum zoom level for the seed task.
   * - :guilabel:`Bounding box`
     - An optional subset of the layer's maximum extent, useful for seeding only certain (more important) areas.  Values are given in the units of the grid set.  If omitted, the layer's maximum extent will be assumed. The update button will update the map at the bottom of the form to match entered bounds while the reset button will reset to default extents.

When ready to start the task, click :guilabel:`Submit`.

.. warning:: Some seed tasks can take a **very** long time, and can easily fill up your disk. Use the estimate to sanity check your seed job.

Seed task estimate
------------------

It's useful to know how big a seed job is while creating it. The seed form provides an estimate of the number of tiles the job will generate and how long it will take. The number of tiles to seed is determined from the bounds, grid set and zoom levels. The time estimate considers the number of tiles to seed, metatiling factors and any throughput limitations.

Selecting the bounds
--------------------

The interactive map at the bottom of the seed form makes it easier to select what region to tile. This map lets you browse around the tile set and select a region to seed. The map tools are explained in the table below.

.. image:: img/bounds_move.png
   :align: left
   :class: float_left

**Move / Resize** - Lets you move and resize the bounds.

.. image:: img/bounds_select.png
   :align: left
   :class: float_left

**Draw** - Draw new bounds onto the map.

.. image:: img/bounds_pan.png
   :align: left
   :class: float_left

**Navigate** - Pan and zoom around the map without changing the bounds.

The map also shows a base layer which can be configured in the GeoWebCache configuration file. For more details see: :ref:`configuration.basemap`.

Managing a seed job
-------------------

When a seed task is ongoing, returning to the layer's Seed page will display the current status of the task, including projected duration and number of tiles, with details for each thread (if there is more than one).  Threads can be terminated by clicking the :guilabel:`Kill Thread` button next to the thread status. Managing seed tasks is also possible using the :ref:`webinterface.jobs`

.. note:: A word on terminology. A task in GeoWebCache is a single thread performing some activity such as seeding or truncating. A job is a schedulable (or immediately executed) activity that can run multiple tasks to achieve its goal. The terms task and thread in GeoWebCache are pretty much interchangeable.
