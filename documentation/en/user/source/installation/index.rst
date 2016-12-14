.. _installation:

Installation
============

This section describes how to install GeoWebCache on the most common platforms.  GeoWebCache will work on any operating system that supports Java 1.5, including FreeBSD and Solaris.

The following software will need to be set up prior to installing GeoWebCache:

 * **Java Runtime Environment (JRE)** (version 1.5 or greater, preferably from Oracle)
 * **Java Servlet Container** (such as Apache Tomcat)

In essence, GeoWebCache is a set of Java classes (program files) and a number of configuration files. The combination is known as a Java Servlet, and these are commonly distributed in a zip file known as a Web ARchive, or WAR file for short. 

To use its content, we need a Java Virtual Machine and a Servlet Container. The latter is a network service that accepts requests from clients, such as web browsers, and delegates them to the appropriate servlet.


.. toctree::
   :maxdepth: 2

   prerequisites/index
   geowebcache.rst
   upgrading.rst
