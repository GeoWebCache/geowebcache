.. _installing_geowebcache:

Installing GeoWebCache
======================

Once the :ref:`Java Servlet environment <prerequisites>` is in place:

1. The :file:`geowebcache-war.zip` Web ARchive (WAR) file can be downloaded from `GeoWebCache.osgeo.org <https://geowebcache.osgeo.org>`_.  

2. Unpack the zip file and make sure to read the software :ref:`license`, and locate the :file:`geowebcache.war` for deployment.

Option 1: Tomcat Administration Tool
------------------------------------

#. Navigate to `<http://localhost:8080>`_ (or wherever your Tomcat instance resides) and click the :guilabel:`Tomcat Manager` on the left hand side.

#. Find the section :guilabel:`WAR file to deploy`, and upload :file:`geowebcache.war` you unpacked from the zip file. 

#. After the upload is complete, look for :guilabel:`geowebcache` in the :guilabel:`Applications` table. 

#. Once the application is started, GeoWebCache is available ``http://localhost:8080/geowebcache``.

Option 2: Manual Installation
-----------------------------

1. Tomcat will need to be stopped before making any changes to the webapps directory.
   
   Ensure that the Tomcat process is stopped before proceeding, as the JVM does not always release file handles immediately.

2. To manually deploy:

   * Copy the file :file:`geowebcache.war` to :file:`<tomcat dir>/webapps`.
   
     On startup Tomcat will deploy the application to the folder :file:`<tomcat dir>/webapps/geowebcache`
     
   * If you wish, you can unpack this archive manually in this location. The :file:`geowebcache.war` may be treated as a zip file and be unpacked to :file:`<tomcat dir>/webapps/geowebcache`.
  
    This approach allows you to make configuration changes before restartig Tomcat.

3. After restarting Tomcat, GeoWebCache is available ``http://localhost:8080/geowebcache``.
