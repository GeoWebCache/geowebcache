.. _configuration.layers.projections:

Custom projections
==================

.. note:: In GeoWebCache, a projection is also known as a :ref:`gridset <concepts.gridsets>`.

By default, GeoWebCache only advertises support for EPSG:4326 (longitude/latitude) and EPSG:900913 (spherical web mercator).  Any other projection needs to be defined and configured manually.

Adding a custom projection to GeoWebCache requires modifying (or creating, if it doesn't already exist) the :file:`geowebcache.xml` configuration file to include a custom projection definition and then associating the layer with that projection.  (Read more about this file in the section :ref:`configuration.layers.howto`.)

Defining a projection
---------------------

.. note:: GeoWebCache does not know anything about projections; it only knows about its own tile grid.  This information is used when passing information to the originating WMS.  It is assumed that the WMS can understand requests in the custom projection.

In order to add a custom projection to GeoWebCache, the following information is required:

.. list-table::
   :widths: 30 20 50
   :header-rows: 1

   * - Field
     - Tag
     - Description
   * - Name
     - ``<name>``
     - A human readable name for the projection.  Can typically be the name of the projection (e.g. EPSG:2263).
   * - SRS number
     - ``<srs><number>``
     - The numerical identifier for the SRS (e.g. 2263).
   * - SRS extent
     - ``<extent><coords>``
     - The full extent for the projection in its native units.
   * - List of valid resolutions
     - ``<resolutions>``
     - While a list of resolutions typically decrease by a factor of two with each entry, this is not required.
   * - Meters per unit
     - ``<metersPerUnit>``
     - A constant value of the amount of meters in the native units for the projection.
   * - Tile height and width
     - ``<tileHeight>``, ``<tileWidth>``
     - Typically 256 pixels each, although other values are valid.

.. note:: For full information, please see the GeoWebCache schema.  (Read more about the schema in the section on :ref:`configuration.layers.howto`.)

.. warning:: When defining a new SRS/gridset, it is important to **use the full extent of the projection**, even if your data only covers a portion of that extent.  The extent of the layer itself is defined in the ``<gridSubset>`` of the layer definition below.

Determining SRS extent
~~~~~~~~~~~~~~~~~~~~~~

If you are unsure of the full extent of the SRS, you can use an online resource such as `<http://spatialreference.org>`_ and use the :guilabel:`Projected Bounds`.  For example, on the `reference page for EPSG:2263 <http://spatialreference.org/ref/epsg/2263/>`_ the Projected Bounds is listed as::

  909126.0155, 110626.2880, 1610215.3590, 424498.0529

Determining meters per unit
~~~~~~~~~~~~~~~~~~~~~~~~~~~

While many SRS definitions are in meters, there are many other units possible.  Setting this will prevent grid shifts and unexpected output.

A short list of correct values  of ``<metersPerUnit>`` for different units is as follows:

.. list-table::
   :widths: 50 50
   :header-rows: 1

   * - Unit
     - Meters per unit
   * - Degrees
     - 111319.49079327358
   * - Feet
     - 0.3048
   * - Inches
     - 0.0254
   * - Meters
     - 1

While it is not required to put a ``<metersPerUnit>`` value when the projection is defined in meters, it is still good practice (and will prevent warnings from being displayed in the logs).

Example
~~~~~~~

The following is an example of a custom projection definition, in this case `EPSG:2263 <http://spatialreference.org/ref/epsg/2263/>`_, or **New York Long Island (ft)**.

.. code-block:: xml

   <gridSets>
     ...
     <gridSet>
       <name>EPSG:2263</name>
       <srs><number>2263</number></srs>
       <extent>
         <coords>
           <double>909126.0155</double>
           <double>110626.2880</double>
           <double>1610215.3590</double>
           <double>424498.0529</double>
         </coords>
       </extent>
       <resolutions>
         <double>466.2771277605631</double>
         <double>233.13856388028155</double>
         <double>116.569281940140775</double>
         <double>58.2846409700703875</double>
         <double>29.14232048503519375</double>
         <double>14.571160242517596875</double>
         <double>7.2855801212587984375</double>
       </resolutions>
       <metersPerUnit>0.3048</metersPerUnit>
       <tileHeight>256</tileHeight>
       <tileWidth>256</tileWidth>
     </gridSet>
     ...
   </gridSets>

.. note:: specific layers will need to be associated with this projection before they can be viewed as such.

Associate a layer with the projection
-------------------------------------

Once the projection is loaded in GeoWebCache, the next step is to associate the projection to a given layer.  This is done in the same :file:`geowebcache.xml` file.

The necessary information is:

.. list-table::
   :widths: 30 20 50
   :header-rows: 1

   * - Field
     - Tag
     - Description
   * - Layer name
     - ``<name>``
     - The layer name as published by GeoWebCache. It can be the same name as known to the WMS (for example, in GeoServer, this would be ``namespace:layername``) or not.
   * - Name of projection
     - ``<gridSetName>``
     - The projection name as referenced in the layer definition (in this example, ``EPSG:2236``)
   * - Layer extent
     - ``<extent><coords>``
     - The extent of the layer.  This is where the restricted extent is set.
   * - WMS URL
     - ``<wmsUrl>``
     - The path to the WMS endpoint.

Example
~~~~~~~

Below an example of a single layer added using the previously-defined (EPSG:2263) projection.

.. code-block:: xml

   <layers>
     ...
     <wmsLayer>
       <name>my:layer</name>
       <gridSubsets>
         <gridSubset>
           <gridSetName>EPSG:2263</gridSetName>
           <extent>
             <coords>
               <double>937558.37821372</double>
               <double>89539.946131199</double>
               <double>1090288.6738155</double>
               <double>327948.21243638</double>
             </coords>
           </extent>
         </gridSubset>
       </gridSubsets>
       <wmsUrl><string>http://myserver/geoserver/wms</string></wmsUrl>
     </wmsLayer>
     ...
   </layers>

Any change made to :file:`geowebcache.xml`, it is required to :ref:`reload the configuration <configuration.reload>`.
