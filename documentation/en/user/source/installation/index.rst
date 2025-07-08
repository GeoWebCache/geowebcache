.. _installation:

Installation
============

This section describes how to install GeoWebCache, which will work on any operating system that supports Java 17.

The following software will need to be set up prior to installing GeoWebCache:

 * **Java Runtime Environment (JRE)** (version 17 or 21)
 * **Java Servlet Container** (such as Apache Tomcat or Jetty)

In essence, GeoWebCache is web application consisting of Java classes (program files) and a number of configuration files. The combination is known as a Java Servlet, and these are commonly distributed in a zip file known as a Web ARchive, or WAR file for short.

To run this web application, we need both a Java Runtime Environment (responsible for running the Java program files) and Servlet Container (that accepts HTTPS requests from clients, such as web browsers, and delegates them to the GeoWebCache).

.. toctree::
   :maxdepth: 2

   prerequisites/index
   geowebcache.rst
   upgrading.rst
