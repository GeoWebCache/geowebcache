.. _macosx:

MacOS X
=======

MacOS has a number of command line package managers for open source components. We recommend using `SDKMAN! <https://sdkman.io/>`_ to manage Java and Tomcat environment.

Java
----

Make sure you have a Java Runtime Environment (JRE) installed on your system. GeoWebCache requires a Java 17 environment, and is also tested with Java 21.

* Required: Java Development Kit 17 (JDK 17)

  Open JDK:

  https://adoptium.net/temurin/releases/?version=17 Temurin 17 (LTS) - Recommended

* SDKMan!
  
  .. code-block:: bash
  
     # list to determine latest Temurin JDK 17
     sdk list java 17
     
     # Installing latest Temurin JDK 17 shown above
     sdk install java 17.0.15-tem
     
     # Select Java for use 
     sdk use java 17<tab>

See :doc:`/installation/upgrading` for compatibility table.

Apache Tomcat
-------------

GeoWebCache requires a container supporting the Jakarta EE Servlet 6.1 specification. Apache Tomcat 11 and Jetty 12 are tested; other compliant containers should work but are untested.

1. Navigate to `Tomcat 11 <https://tomcat.apache.org/download-11.cgi>`_ **Downloads** section, and save the ``zip`` file listed under **Binary Distributions / Core**.

* SDKMan!

  .. code-block:: bash

     # list to determine latest Apache Tomcat 11
     sdk list tomcat 11

     # Installing latest Tomcat 11 shown above
     sdk install tomcat 11.0.6

     # Select tomcat for use
     sdk use tomcat 11.0.6
