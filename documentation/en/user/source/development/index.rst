.. _development:

Development
===========

You are encouraged to help contribute code to GeoWebCache.  To do so, you will first need to set up the proper development environment.

This is the current prerequisites:

 * Sun/Oracle Java Developer Kit SE, version 1.5
 * `Maven 2.x <http://maven.apache.org/>`_
 * `Git <http://git-scm.com>`_

Please make sure you use **Java 1.5** to compile to ensure that we do not introduce dependencies only available in 1.6.

You are encouraged to join the `GeoWebCache Developers mailing list <https://lists.sourceforge.net/lists/listinfo/geowebcache-devel>`_ to discuss your work.  It is always a good idea to ask whether anyone else has already solved the same problem.


Setting up Maven
----------------

#. Get the installation file from http://maven.apache.org/download.html, unpack and include the :file:`bin` directory in your PATH variable.

#. Set JAVA_HOME to point to the root directory of your JDK, for example:

   Linux/OS X::

     $ export JAVA_HOME=/opt/jdk1.5.0_21

   Windows::

     > set JAVA_HOME=C:\Program Files\Java\jdk1.5.0_21

#. Test that Maven is installed correctly::

     mvn -version

#. Check that you are using the right version of the ``javac`` compiler, as this is determined by PATH, not JAVA_HOME::

     javac -version

#. Check out the code::

     git clone https://github.com/GeoWebCache/geowebcache.git

To build the code, enter the :file:`geowebcache` directory and run::

   mvn clean install

To build with an embedded Jetty server to test changes::

   mvn clean install jetty:run

To build a WAR file:  Currently GeoWebCache has all classes in a single maven project. To build a WAR file, edit :file:`pom.xml` and replace ``<!--WAR`` and ``WAR-->`` with ``<`` and ``>`` respectively. Run ``mvn clean install`` and you will find ``geowebcache.war`` inside the target directory.

Setting up Eclipse
------------------

#. Inside the source code directory, run::

     mvn clean install eclipse:eclipse

Create a new workspace in Eclipse
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Set up Maven repository  (Window -> Preferences -> Java -> Build Path -> Class Path Variables)

#. Add a new variable M2_REPO, and set the path to <home directory>/.m2/repository

#. Next, go to Java -> Code Style -> Formatter.  Click on Import, choose geowebcache/tools/formatter.xml

#. Now we need to import the actual project (File -> Import -> Existing Projects into Workspace)

To run GeoWebCache, go to Run -> Debug Configurations, double-click on Java Configurations

  * Set Name: GWC
  * The Project: geowebcache
  * For main class, set "Start"

Then press "Close", or "Debug" if you want to try it right away.

Contributing patches
--------------------

The prefered way of providing patches is to create an issue in GitHub a patch, which you create by running::

  git diff > patch.txt

In addition to creating the issue, you are highly encouraged to jump on the `GeoWebCache Developers mailing list <https://lists.sourceforge.net/lists/listinfo/geowebcache-devel>`_ to introduce the patch.
