.. _quickstart:

Quickstart
==========

This section describes how you can quickly get started with GeoWebCache without knowing much about the configuration.

All servers that conform to the OGC Web Map Service specification must publish what is known as a GetCapabilities document. This is an XML file that describes every layer every layer that is available to the user. GeoWebCache can use this information to configure itself automatically.

Using WMS GetCapabilities
-------------------------

``geowebcache-servlet.xml`` is a configuration file that controls how the application is loaded. It is located inside the WEB-INF folder, typically ``/opt/apache-tomcat-6.0.20/webapps/geowebcache/WEB-INF/`` or ``C:\Program Files\Apache Software Foundation\Tomcat 6.0.20\webapps\geowebcache\WEB-INF``. If you use Windows I highly recommend Notepad++ or jEdit over regular Notepad.

1. Open geowebcache-servlet.xml in a text editor. Find ``<bean id="gwcWMSConfig" ...`` , on the second line you will see a URL pointing to localhost 

   .. code-block:: xml

      <constructor-arg value="http://localhost:8080/geoserver/wms?service=wms&amp;request=getcapabilities&amp;version=1.1.0" />

 
2. Replace this string with a valid URL to a 1.0.x or 1.1.x WMS GetCapabilities document, for instance

   .. code-block:: xml

      <constructor-arg value="http://yourserver.net/wmsserver/ows?service=wms&amp;request=getcapabilities&amp;version=1.1.0" />


   *Note:* ``&`` has to be written as ``&amp;`` in XML files. Omit the line breaks.
 

3. Save the configuration file and reload the servlet using Tomcat's Manager, or restart the servlet container.


4. Try it! Go to http://localhost:8080/geowebcache/demo and try out the OpenLayers applications.

Special Cases
-------------

5. If your WMS server requires additional parameters to be passed with every request, such as MapServer's ``map`` argument, set this as the value on the fifth line:

   .. code-block:: xml

      <constructor-arg value="map=somemap&amp;otherkey=othervalue"/><!-- vendor parameters -->

6. Also note that to get 24 bit PNGs (image/png; mode=24bit") from MapServer (or image/png24 from ArcIMS), you should specify those as output formats:

   .. code-block:: xml

      <constructor-arg value="image/png; mode=24bit,image/jpeg,image/png24"/>

7. It is possible to change the metatiling factors by changing the fourth element. This will affect all layers derived from this document.


8. GeoWebCache can be configured from multiple getcapabilities documents. Simply duplicate the bean, change the id and add it to the list of the ``gwcTLDispatcher``.


Additional Information
----------------------

All layers on the server should be available on the demo page, along with automatically configured OpenLayers clients. The KML demos, for Google Earth, use the same sets of tiles as the OpenLayers EPSG:4326 client.

According to the standard, the GetCapabilities document is only guaranteed to contain the WGS84 (lat/lon) bounds for a layer, hence EPSG:4326, though in rare cases the WMS server will not be able to provide responses for this projection. If the getcapabilities document contains bounding boxes for additional projections, often the native reference system for the data, then these will be included as well. Finally, GeoWebCache will convert the EPSG:4326 bounding box to spherical mercator (EPSG:900913, now officially known as EPSG:3857). Again, this does not guarantee that the WMS server can actuall provide such tiles.

If you need to support other projections you can do so by defining grid sets manually and adding them to these layers. The same is true for supporting specific resolutions, output formats or tile sizes.
