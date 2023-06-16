.. _prerequisites:

Installing Java and Tomcat
==========================

This section describes how to install a Java Runtime Enivronment (JRE) and Apache Tomcat (a Java Servlet Container) on the most common platforms. 

While this section only discusses the Tomcat servlet container, the setup is usually very similar for other servlet containers.

.. note:: You can skip this step and go to :ref:`installing_geowebcache` if you already have a servlet container running.

.. warning::  Many Linux distributions come with packages for Java and Tomcat. However, these are often an incorrect version and are frequently configured to deny filesystem access, hence it is recommended to install these packages manually.

.. toctree::
   :maxdepth: 2

   linux.rst
   windows.rst
   macosx.rst

