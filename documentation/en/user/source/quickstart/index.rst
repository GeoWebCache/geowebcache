.. _quickstart:

Quickstart
==========

This section describes how to get quickly started with GeoWebCache without knowing much about the :ref:`configuration`.

All servers conforming to the `OGC Web Map Service specification <http://www.opengeospatial.org/standards/wms>`_ publish what is known as a **capabilities document** (sometimes also known as a GetCapabilities document after the request used to fetch it). This is an XML file that describes the layers exposed by the WMS service and available to the user. GeoWebCache can use this information to configure itself automatically.

.. _quickstart.xml:

View preconfigured layers
-------------------------

GeoWebCache comes preconfigured with three layers from a default GeoServer install which may be installed alongside GeoNetwork.  To view them, navigate to your GeoWebCache demo page at ``http://localhost:8080/geowebcache/demo``.  Click on any of the links next to the :guilabel:`OpenLayers` column.

These layers are all served by the WMS available at ``http://localhost:8080/geowebcache/``.

.. list-table::
   :header-rows: 1
   :widths: 50 50 

   * - Layer Name (Title)
     - WMS layer(s)
   * - **img states**
     - nurc:Img_Sample,topp:states
   * - **raster test layer**
     - nurc:Img_Sample
   * - **topp:states**
     - topp:states

.. note:: This information is set in the :file:`geowebcache.xml` file, which is typically available at :file:`webapps/geowebcache/WEB-INF/classes`. See the section on :ref:`configuration.layers` for more information on customizing this file.

.. _quickstart.wms:

View layers from a WMS
----------------------

The file :file:`geowebcache-core-context.xml` is a configuration file controling how the application is loaded. It is located inside the WEB-INF folder, typically :file:`webapps/geowebcache/WEB-INF/` along with several other configuration files.

#. Open :file:`geowebcache-core-context.xml` in a text editor.

   .. note:: If using Windows, it is recommended to use an enhanced text editor such as `Notepad++ <http://notepad-plus-plus.org/>`_ or `jEdit <http://www.jedit.org>`_ instead of the standard Windows Notepad. 

#. Find the block beginning with:

   .. code-block:: xml

      <bean id="gwcWMSConfig" class="org.geowebcache.config.wms.GetCapabilitiesConfiguration">

   On the second line you will see a value that contains a URL: 

   .. code-block:: xml

      <constructor-arg value="http://localhost:8080/geoserver/wms?request=getcapabilities&amp;version=1.1.0&amp;service=wms" />

#. Replace the value with a URL pointing to a valid WMS capabilities document, such as:

   .. code-block:: xml

      <constructor-arg value="http://demo.opengeo.org/geoserver/ows?service=WMS&amp;request=GetCapabilities&amp;version=1.1.0" />

   .. warning::  The ampersand sign, ``&`` has to be written out as ``&amp;`` in XML files. Also, make sure to omit the line breaks.

#. Save the file and reload the servlet using Tomcat's Manager, or by restarting the servlet container.

#. Navigate to or reload your GeoWebCache demo page.  You should see the list of layers as advertised in the WMS capabilities document.

Special cases
-------------

Below are some extra parameters that may be needed or that you may want to add to get your WMS layers loading properly in GeoWebCache.

Map vendor parameters
~~~~~~~~~~~~~~~~~~~~~

If your WMS server requires additional vendor parameters to be passed with every request, such as MapServer's ``map`` argument, set this in the fifth ``constructor-arg`` value.

Replace:

   .. code-block:: xml

      <constructor-arg value=""/>

   with:

   .. code-block:: xml

      <constructor-arg value="map=name&amp;otherkey=othervalue"/>

Cached vendor parameters
~~~~~~~~~~~~~~~~~~~~~~~~

By default vendor parameters included in requests are ignored. To pass on the value of a vendor parameter to your wms server, and cache the result seperately depending on the given value, set the sixth ``constructor-arg`` value.

For each cached vender parameter add a line between the <map> tags of the form:

   .. code-block:: xml

        <entry key="parameterName" value="defaultvalue" />

Other image formats
~~~~~~~~~~~~~~~~~~~

To get 24 bit PNGs from MapServer or ArcIMS (``image/png; mode=24bit`` and ``image/png24`` respectively), or any other image format, you will need to specify those as output formats.  This is set in the fourth ``constructor-arg`` value.  Replace:

   .. code-block:: xml

      <constructor-arg value="image/png,image/jpeg"/>

with

   .. code-block:: xml

      <constructor-arg value="image/png; mode=24bit,image/png24,image/jpeg"/>

Other MIME types can be specified here as well.

Metatile factor
~~~~~~~~~~~~~~~
 
The metatiling factor can be modified by editing the third ``constructor-arg`` value.  This will affect all layers derived from this document.  See the :ref:`concepts.metatiles` section for more information.


Multiple capabilities documents
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

GeoWebCache can be configured from multiple capabilities documents.  To do this, you can duplicate the ``gwcWMSConfig`` bean with a new id: ``gwcWMSConfig2`` for instance.


Additional information
----------------------

All layers known to GeoWebCache should be available on the demo page, along with automatically configured OpenLayers clients. The KML demos use the same sets of tiles as the OpenLayers EPSG:4326 demo.

According to the WMS standard, the capabilities document is only guaranteed to contain the WGS84 (lat/lon) bounds for a layer, hence EPSG:4326, though in rare cases the WMS server will not be able to provide responses for this projection. If the capabilities document contains bounding boxes for additional projections, often the native reference system for the data, then these will be included as well. Finally, GeoWebCache will convert the EPSG:4326 bounding box to spherical mercator (EPSG:900913, now officially known as EPSG:3857).  That said, the WMS server isn't guaranteed to be able to provide a successful response to these requests.

If you need to support other projections, you can do so by defining grid sets manually and adding them to these layers. The same is true for supporting specific resolutions, output formats or tile sizes.  See the :ref:`configuration` section for more information.
