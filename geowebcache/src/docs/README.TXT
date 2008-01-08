=====================================
		GeoWebCache
		(formerly known as GeoWebCache)
		
		ak@openplans.org / chris.whitney@gmail.com
=====================================

Building
---------------------------
Using maven to generate a war file:
mvn package


Configuration
-------------------------
UPDATE ME!

In addition to the GeoWebCache.properties file, GeoWebCache also uses properties files for commons-logging and cache configuration.  Included are sample files for setting up commons-logging with log4j and configuring the JCS cache.


Running
--------------------------
Copy the war file into the webapps directory of your application server.  GeoWebCache should be available at http://server:port/GeoWebCache/GeoWebCache .  There is sample OpenLayers interface (if you have a Geoserver deployed alongside GeoWebCache and topp:states available) at http://server:port/GeoWebCache .  Also included is src/docs/JCSAdmin.jsp, a basic cache viewer-- copy it into src/main/webapp (or copy into a running web application root folder) to use. [Since it exposes cache clearing without a password, do not use it for a public-facing server].  



Debugging:
----------------------------
IOError in logs: Broken Pipe in the logfiles usually occurs when a client stops a request.  This usually occurs when openlayers pans around faster than the tiles are received.
