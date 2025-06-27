.. _macosx:

MacOS X
=======

MacOS has a number of command line package managers for open source components. We recommend using `SDKMAN! <https://sdkman.io/>`_ to manage Java and Tomcat environment.

Java
----

Make sure you have a Java Runtime Environment (JRE) installed on your system. GeoWebCache requires a Java 17 or Java 21 environment.

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

GeoWebCache requires Apache Tomcat 9 required for JavaEE environment.

1. Navigate to `Tomcat 9 <https://tomcat.apache.org/download-90.cgi>`_ **Downloads** section, and save the ``zip`` file listed under **Binary Distributions / Core**.

   * Tomcat 9 Required: GeoWebCache uses the JavaEE environment last supported in Tomcat 9.
   
   * Tomcat 10 Unsupported: GeoWebCache is not yet compatibile with the JakartaEE environment used by Tomcat 10 and newer.

* SDKMan!

  .. code-block:: bash
  
     # list to determine latest Apache Tomcat 9
     sdk list tomcat 9 
     
     # Installing latest Tomcat 9 shown above
     sdk install tomcat 9.0.102 
     
     # Select tomcat for use
     sdk use tomcat 9.0.102 

GeoWebCache is not compatible with Apache Tomcat 10 JakarataEE environment.
