.. _troubleshooting:

Troubleshooting
===============

Configuration Problems
----------------------

Configuring OpenLayers can be complicated because the grid alignment can be determined from a large number of sources. Note that the "base layer" has a special meaning in the se applications, certain options pertain to all layers added to the client. The best advice is therefore to look closely at the source code of the automatically generated demos.

If you receive a "broken tile", you should either use a tool like Firebug to examine the response or attempt to view it as a separate page. Often these tiles are really plain text and return an error message. For instance, you may be told that the resolution does not match one that is available, or that the grid is misaligned.

Errors caused by incorrectly configured clients are usually not logged on the server, because a misconfigured client could easily fill up the logs.

Incorrect or Broken Tiles
-------------------------

GeoWebCache does not track configuration changes internally. So if you reconfigure GeoWebCache, but old tiles overlap with the new layer, you will usually get the old tiles. One of the most common issues is caused by changing the grid set extent or adding a new, intermediary resolution or scale. This means that the X,Y,Z indexes used to store tiles no longer refer to the same thing as before.

In this case you should stop the servlet container and wipe the appropriate cache directories. See the storage documentation with regards to their location, the top level hierarchy should be fairly simple to understand.

Note that you can safely modify gridSubsets.


Logging
-------

Logging can be controlled through ``WEB-INF/classes/log4j.properties``. By default, log messages will end up in the standard log of the container, which is ``logs/catalina.out`` in the case of Apache Tomcat.

Getting Help
------------

Help is readily available on the geowebcache-users mailinglist. Please subscribe before posting, and include any configuration files you may have modified.
