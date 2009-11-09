.. _installing_geowebcache:

Installing GeoWebCache
======================

Installing GeoWebCache is easy once the Java Servlet environment is in place. Go to http://geowebcache.org , download the latest -WAR.zip file. Unpack the zip file, make sure you understand the software license and note where you put the .war file.

Using Tomcat's Administration Tool
----------------------------------

Then go to http://localhost:8080 and click the Tomcat Manager on the left hand side

Go to the section ``WAR file to deploy`` and upload geowebcache.war 

After the upload is complete, look for geowebcache in the ``Applications`` table. 

GeoWebCache should now be installed at http://localhost:8080/geowebcache

This concludes the installation, next we look at configuration.


Alternative: Unpacking the Archive Manually
-------------------------------------------

All that the administration tool really does is create a new directory inside ``<tomcat dir>/webapps/geowebcache`` , and unpacks the zip file inside this directory. 

If you wish, you can create this directory somewhere else, unpack manually and make any configuration changes, stop Tomcat, move the directory and then start Tomcat again. If upgrading, ensure that the Tomcat process is gone before moving the directory, the JVM does not always release file handles immediately.
