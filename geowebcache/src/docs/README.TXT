============================================================================
		GeoWebCache
		(formerly known as JTileCache)
		
		ak@openplans.org / chris.whitney@gmail.com
=============================================================================

Building
---------------------------
Using maven to generate a war file:
1) Edit pom.xml

2) mvn clean install


Configuration
-------------------------
GeoWebCache 0.8 uses the Spring framework. The application is tied together by applicationContext.xml, which can be found in src/main/resources prior to installation and in WEB-INF/classes after the WAR is installed.

For layers you can either use .proterties files for static configuration, or through GetCapabiltiesConfiguration which uses a WMS getCapabilities request to a WMS server to determine what layers are available.

GeoWebCache also uses properties files for commons-logging and cache configuration.  Included are sample files for setting up commons-logging with log4j and configuring the JCS cache.

Running
--------------------------
Copy the war file into the webapps directory of your application server.  GeoWebCache should be available at http://server:port/geowebcache/ , where it defaults to an index file that shows off three demos. Requests to these are forwarded to sigma.openplans.org

Also included is src/docs/JCSAdmin.jsp, a basic cache viewer-- copy it into src/main/webapp (or copy into a running web application root folder) to use. [Since it exposes cache clearing without a password, do not use it for a public-facing server].  

Debugging:
----------------------------
See http://geowebcache.org for tips on how to run GeoWebCache in Eclipse.
