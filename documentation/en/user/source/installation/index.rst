.. _installation:

Prerequisites and Installation
==============================

This section describes how to install GeoWebCache on various platforms.

The essential components that we need are:
* Java Runtime Environment (JRE) version 1.5 or greater, preferablyfrom Sun
* A Java Servlet Container
* The GeoWebCache WAR file

In essence, GeoWebCache is a set of Java classes (program files) and some configuration files. The combination is known as a Java Servlet, and these are commonly distributed in a zip file known as a Web ARchive, or WAR file for short. To make use of its content, we need a Java Virtual Machine and a Servlet Container. The latter is a network service that accepts requests from clients, such as web browsers, and delegates them to the appropriate servlet.

GeoWebCache will work on any operating system that supports Java 1.5, including FreeBSD and Solaris.


.. toctree::
   :maxdepth: 2

   instructions/index
   geowebcache.rst

