.. _xml:

Configuring Layers
==================

GeoWebCache's main configuration file is ``geowebcache.xml``. It is located inside the servlet directory, ``WEB-INF/classes/geowebcache.xml``.

If you wish to use a specific location, you can open ``WEB-INF\geowebcache-servlet.xml`` and modify the gwcXmlConfig bean definition. It contains examples for setting absolute and relative paths, uncomment one of these and modify it as needed.


Useful Tools
------------
You can either use a special XML tool or a general text editor to edit geowebcache.xml. jEdit is a great editor that includes support for XML Schema Documents (XSD files). The advantage is that it can tell you right away if you are missing a tag or have placed elements out of order.

**The order of the XML elements is crucial!**

If you do not use such a tool you can still verify the document. On UNIX derivatives you can usually use ``xmllint``, or you can upload your file to an online service such as http://www.validome.org/xml/

GeoWebCache validates the XML file against the schema during startup and will report errors, so be sure to check the logs if the layers do not behave as expected. If in doubt, be sure to reference the schema documentation, which will be updated for every stable release: http://geowebcache.org/schema/docs/

.. toctree::
   :maxdepth: 1

   simple.rst
   exhaustive.rst
