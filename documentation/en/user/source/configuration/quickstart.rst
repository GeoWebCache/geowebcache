.. _quickstart:

Quickstart
==========

This section describes how you can quickly get started with GeoWebCache without knowing much about the configuration.

All servers that conform to the OGC Web Map Service specification must publish what is known as a GetCapabilities document. This is an XML file that describes every layer every layer that is available to the user. GeoWebCache can use this information to configure itself automatically.

Using WMS GetCapabilities
-------------------------

geowebcache-servlet.xml is a configuration file that controls how the application is loaded. It is located inside the WEB-INF folder, ``/opt/apache-tomcat-6.0.20/webapps/geowebcache/WEB-INF/`` or ``C:\Program Files\Apache Software Foundation\Tomcat 6.0.20\webapps\geowebcache\WEB-INF`` if you followed the installation instructions.

Open the file in a text editor. If you use Windows I highly recommend Notepad++ or jEdit. Note that ``&`` has to be written as ``&amp;`` in XML files.

Find ``<bean id="gwcWMSConfig" ...``

On the second line you will see a URL pointing to localhost

``<constructor-arg value="http://localhost:8080/geoserver/wms?service=wms&amp;request=getcapabilities&amp;version=1.1.0" />``

Replace this string with a valid URL to a 1.0.0, 1.1.0 or 1.1.1 WMS GetCapabilities document, for instance

``<constructor-arg value="http://yourserver.net/wmsserver/ows?service=wms&amp;request=getcapabilities&amp;version=1.1.0" />``

If your WMS server requires additional parameters to be passed with every request, such as MapServer's ``map`` argument, set this as the value on the fifth line:

``<constructor-arg value="map=somemap&amp;otherkey=othervalue"/><!-- vendor parameters -->``

Also note that to get 24 bit PNGs (image/png; mode=24bit") from MapServer, you should add png24 as an output format:

``<constructor-arg value="image/png24,image/jpeg"/>``


Try it!
-------

Save the configuration file and reload the servlet using Tomcat's Manager, or restart the servlet container.

Then point your web browser to http://localhost:8080/geowebcache/demo , and all layers on the server should be available along with automatically configured OpenLayers clients. The KML demos, for Google Earth, use the same sets of tiles as the OpenLayers EPSG:4326 client.

Note that a GetCapabilities document is only guaranteed to contain the WGS84 (lat/lon) bounds for a layer, hence EPSG:4326. If the getcapabilities document contains bounding boxes for additional projections, these will be included as well. Finally, GeoWebCache will convert the EPSG:4326 bounding box to spherical mercator (EPSG:900913, now officially known as EPSG:3857).


If you need to support other projections you can do so by defining grid sets manually and adding them to these layers. The same is true for supporting specific resolutions, output formats or tile sizes.
