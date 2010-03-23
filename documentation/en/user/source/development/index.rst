.. _development:

Development Environment
=======================

If you wish to contribute code to GeoWebCache, the first thing you need to set up if a development environment.

You need the following:
* Sun Java Developer Kit SE, version 1.5
* Maven 2.x
* Subversion 1.4.x or greater

Please make sure you use Java 1.5 to compile to ensure that we do not introduce dependencies or idioms that are only available in 1.6.

You are encouraged to join the developer mailinglist when you start hacking, it is always a good idea to ask whether anyone else has already solved the same problem.


Setting up Maven
----------------

Get the installation file from http://maven.apache.org/download.html , unpack and include the bin directory in your PATH variable.

Set JAVA_HOME to point to the root directory of your JDK, for example 
``export JAVA_HOME=/opt/jdk1.5.0_21``

Test that Maven is ready:
``mvn -version``

Check that you are using the right version of the javac compiler, because this is determined by PATH, not JAVA_HOME.
``javac -version``

Check out the code:
``svn co http://geowebcache.org/svn/trunk gwc-trunk``

Building the code:
Enter the gwc-trunk directory and run
``mvn clean install``

Running an embedded Jetty server to test changes:
``mvn clean install jetty:run``

Building a WAR file:
Currently GWC has all classes in a single maven project. To build a WAR file, edit pom.xml and replace <!--WAR and WAR--> with < and > respectively. Run ``mvn clean install`` and you will find geowebcache.war inside the target directory.

Setting up Eclipse
------------------
Inside the source code directory, run
``mvn clean install eclipse:eclipse``

Create a new workspace in Eclipse

Set up Maven repository
Window -> Preferences -> Java -> Build Path -> Class Path Variables
Add a new variable M2_REPO , and set the path to <home directory>/.m2/repository

Next, go to 
Java -> Code Style -> Formatter 
Click on Import, choose gwc-trunk/tools/formatter.xml

Now we need to import the actual project
File -> Import -> Existing Projects into Workspace

To run GeoWebCache
Go to Run -> Debug Configurations , double-click on Java Configurations 
Set Name: GWC
The Project: geowebcache
For main class, set "Start"
and press "Close", or "Debug" if you want to try it right away.

Contributing Patches
--------------------

The prefered way of providing patches is to create a ticket in Trac and attaching a diff, which you create by running
svn diff > patch.txt

Use a text editor to check patch.txt, to make sure you are not sending your configuration file and other changes that are not part of the patch. In addition to filing the ticket, you are highly encouraged to jump on the developer mailinglist to introduce the patch.
