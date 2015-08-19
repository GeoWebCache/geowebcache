.. _troubleshooting:

Troubleshooting
===============

This section describes some common problems encountered when running GeoWebCache.

Configuration problems
----------------------

Configuring OpenLayers with GeoWebCache can be complicated because the grid alignment can be determined from a large number of sources. Note that the "base layer" has a special meaning in these applications; certain options pertain to all layers added to the client. The best advice is therefore to look closely at the source code of the automatically generated demos (available at ``http://<GEOWEBCACHE_URL>/demo/``), and customize them to your needs.

If you receive a "broken tile", you can use a tool like `Firebug <http://getfirebug.com>`_ to examine the response or attempt to view it as a separate page. Often these tiles are really plain text and contain an error message. For instance, you may be told that the resolution does not match one that is available, or that the grid is misaligned.

Errors caused by incorrectly configured clients are usually not logged on the server, because a incorrectly configured client could easily fill up the logs.

If you have a problem with scales, it can be due to the fact that OpenLayers by default assumes 72 DPI, whereas OGC standards such as WMS 1.3.0 assume 0.28mm per pixel (90.72 DPI). To workaround this, you can change the value ``<pixelSize>``.  The value is simply expressed in meters per pixel, i.e.  0.0254 / DPI.

Incorrect or broken tiles
-------------------------

GeoWebCache does not track configuration changes internally. So if you reconfigure GeoWebCache, but old tiles overlap with the new layer, you will usually get the old tiles. One of the most common issues is caused by changing the gridset extent or adding a new, intermediary resolution or scale. This means that the X,Y,Z indexes used to store tiles no longer refer to the same thing as before.

In this case you should stop the servlet container and wipe the appropriate cache directories. See the :ref:`configuration.storage` for more details.

Note that you can safely modify gridsubsets, but the gridset should always be the full extent for the projection.


Logging
-------

Logging can be controlled through :file:`WEB-INF/classes/log4j.properties`. By default, log messages will end up in the standard log of the container, which is for Apache Tomcat is inside the :file:`logs` directory.

XStream
-------

GeoWebCache uses XStream to read and write XML for configuration and for its REST API.  In order to do this securely, it needs a list of Java classes that are safe to convert between objects and XML.  If a class not on that list is given to XStream, it will generate the error ``com.thoughtworks.xstream.security.ForbiddenClassException``.  The specific class that was a problem should aslo be included.  This may be a result of GeoWebCache's list of allowed classes missing a class, which should be reported as a bug, or it may be caused by an extension/plugin not adding its classes to the list.  

This can be worked arround by setting the system property ``GEOWEBCACHE_XSTREAM_WHITELIST`` to a semicolon separated list of qualified class names.  The class names may include wildcards ``?`` for a single character, ``*`` for any number of characters not including the separater ``.``, and ``**`` for any number of characters including separators.  For instance, ``org.example.blah.SomeClass; com.demonstration.*; ca.test.**`` will allow, the specific class ``org.example.blah.SomeClass``, any class immediately within the package ``com.demonstration``, and any class within the package ``ca.test`` or any of its descendant packages.

``GEOWEBCACHE_XSTREAM_WHITELIST`` should only be used as a workarround until GWC or the extension causing the problem has been updated.

Getting help
------------

Help is readily available on the `GeoWebCache Users mailing list <https://lists.sourceforge.net/lists/listinfo/geowebcache-users>`_.  Please subscribe before posting, and include any configuration files you may have modified.

Learn more about the GeoWebCache :ref:`community`.
