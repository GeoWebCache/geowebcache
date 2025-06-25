.. _configuration.layers.howto:

How to configure layers
=======================

The main configuration file for GeoWebCache is :file:`geowebcache.xml`. By default, it is located in the same directory as the :ref:`cache <configuration.storage>`. If the configuration file does not exist, GeoWebCache will copy the default configuration file from :file:`WEB-INF/classes/geowebcache.xml` to the cache directory.

Unless specified, all configuration changes in this section are done editing the file :file:`geowebcache.xml`.

Changing the location of geowebcache.xml
----------------------------------------

The location of the configuration file is set in :file:`WEB-INF/geowebcache-core-context.xml`.  If you wish to set a different location for the configuration file, open :file:`geowebcache-core-context.xml` and look for the ``<bean>`` tag referencing ``gwcXmlConfig``:

.. code-block: xml

   <bean id="gwcXmlConfig" class="org.geowebcache.config.XMLConfiguration">
     <constructor-arg ref="gwcAppCtx" />
     <constructor-arg ref="gwcGridSetBroker"/>
     <constructor-arg ref="gwcDefaultStorageFinder" />
     <!-- constructor-arg value="/etc/geowebcache" / -->
   </bean>


Uncomment the bottom ``<constructor-arg value= ...>`` tag and specify the location where you would like to place :file:`geowebcache.xml`.

Tips for editing XML
--------------------

Editing the configuration file by hand can be tricky, since the smallest typo may cause GeoWebCache to stop functioning.  Furthermore, **The order of the XML elements is crucial!** 

It is recommended to use a special XML editing tool or at the very least a text editor with syntax highlighting.  Furthermore, using an editor with XML schema validation will tell you right away if you are missing a tag or have placed elements out of order.

Two recommended free editors, both of which have support for XML validation, are:

* `jEdit <https://www.jedit.org>`_ (cross platform)
* `Notepad++ <https://notepad-plus-plus.org/>`_ (Windows only)

It is also possible to validate an XML document outside of a text editor environment.

* The `W3C Markup Validation Service <http://validator.w3.org/>`_ allows you to upload an XML file for validation.  Another such service is the `Validome XML Validator <http://www.validome.org/xml/>`_.
* ``xmllint`` application (UNIX/Linux only)

GeoWebCache validates the XML file against the schema during startup and will report errors, so be sure to check the servlet container logs if the layers do not behave as expected.

Schema
------

All configuration options and parameters are ultimately located in the XML schema.  While the documentation you are reading now is designed to translate and interpret the schema to make the GeoWebCache configuration simpler, expert users can look at the schema to determine all details, including variable types, ordering of elements and much more.

* GeoWebCache schema - ``http://geowebcache.org/schema/<version>/geowebcache.xsd``
* GeoWebCache schema docs - ``http://geowebcache.org/schema/docs/<version>/``

Make sure to replace the <version> with the current version number.


