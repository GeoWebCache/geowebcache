.. _macosx:

MacOS X
=======

MacOS has a number of command line package managers for open source components. We recommend using `SDKMAN! <https://sdkman.io/>`_ to manage Java and Tomcat environment.

Java
^^^^^

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

Apache Tomcat
^^^^^^^^^^^^^

GeoWebCache requires Apache Tomcat 9 required for JavaEE environment.

* 

* SDKMan!

  .. code-block:: bash
  
     # list to determine latest Apache Tomcat 9
     sdk list tomcat 9 
     
     # Installing latest Tomcat 9 shown above
     sdk install tomcat 9.0.102 
     
     # Select tomcat for use
     sdk use tomcat 9.0.102 

GeoWebCache is not compatible with Apache Tomcat 10 JakarataEE environment.
