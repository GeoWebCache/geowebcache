.. _installing_geowebcache:

Installing GeoWebCache
======================

Once the :ref:`Java Servlet environment <prerequisites>` is in place, installing GeoWebCache is simple. 

The latest Web ARchive (WAR) file can be downloaded from `geowebcache.org <http://geowebcache.org>`_.  

Unpack the zip file and make sure to read the software :ref:`license`.

Option 1: Tomcat Administration Tool
------------------------------------

#. Navigate to `<http://localhost:8080>`_ (or wherever your Tomcat instance resides) and click the :guilabel:`Tomcat Manager` on the left hand side.

#. Find the section :guilabel:`WAR file to deploy`, and upload :file:`geowebcache.war` you unpacked from the zip file. 

#. After the upload is complete, look for :guilabel:`geowebcache` in the :guilabel:`Applications` table. 

#. GeoWebCache should be installed at ``http://localhost:8080/geowebcache``.

Option 2: Manual Installation
-----------------------------

The file :file:`geowebcache.war` is just a zip file.  The Tomcat Administration Tool unpacks this file to a folder inside the Tomcat webapps directory called ``<tomcat dir>/webapps/geowebcache``.  If you wish, you can unpack this archive manually in this location. You can also make configuration changes before copying to the webapps directory.

.. note:: Tomcat will need to be stopped before making any changes to the webapps directory.  Ensure that the Tomcat process is stopped before proceeding, as the JVM does not always release file handles immediately.

After restarting Tomcat, GeoWebCache should be installed at ``http://localhost:8080/geowebcache``.
