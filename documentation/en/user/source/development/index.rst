.. _development:

Development
===========

You are encouraged to help contribute code to GeoWebCache.  To do so, you will first need to set up the proper development environment.

This is the current prerequisites:

 * Java 7 (`Oracle JDK <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`__ or `OpenJDK <http://openjdk.java.net>`__ )
 * `Maven <http://maven.apache.org/>`_
 * `Git <http://git-scm.com>`_

Please make sure you use **Java 7** to compile to ensure that we don't accidentally use new features only available in Java 8.

You are encouraged to join the `GeoWebCache Developers mailing list <https://lists.sourceforge.net/lists/listinfo/geowebcache-devel>`_ to discuss your work.  It is always a good idea to ask whether anyone else has already solved the same problem.


Setting Up
----------

#. The Maven build system respects the current setting of JAVA_HOME.

   To define JAVA_HOME be sure to point to the root directory of your JDK.

   Windows:

   .. code-block:: bash

      set JAVA_HOME=C:\Program Files\Java\jdk1.7.0_79

   Linux/OS X:

   .. code-block:: bash

      export JAVA_HOME=/opt/jdk1.7.0_79

#. You can download maven from http://maven.apache.org/download.html, unpack and include the :file:`bin` directory in your PATH variable.

   Windows:

   .. code-block:: bash

      set M2_HOME = C:\java\apache-maven-3.0.5
      set PATH=%PATH%;%M2_HOME%\bin;%JAVA_HOME%\bin

   Linux:

   .. code-block:: bash

      export M2_HOME = ~/java/apache-maven-3.0.5
      export PATH=$PATH:$M2_HOME/bin:$JAVA_HOME/bin

   For more detail instructions on maven see the `download page <http://maven.apache.org/download.cgi>`_.

#. Test that Maven is installed correctly:

   .. code-block:: bash

      mvn -version

#. Check that you are using the right version of the ``javac`` compiler (as this is determined by ``PATH``, not ``JAVA_HOME``):

   .. code-block:: bash

      javac -version

Build
~~~~~

#. Check out the code:

   .. code-block:: bash

      mkdir gwc
      cd gwc
      git clone https://github.com/GeoWebCache/geowebcache.git

#. To build the code, enter the :file:`geowebcache` directory and run:

   .. code-block:: bash

      cd geowebcache
      mvn clean install

#. To quickly run a local GeoWebCache for testing:

   .. code-block:: bash

      cd web
      mvn jetty:run

#. A WAR is built as the last step in ``mvn clean install`` above.

   It is located in :file:`geowebcache/web/target/geowebcache.war`


Setting up Eclipse
------------------

#. Inside the source code directory, run:

   .. code-block:: bash

      cd geowebcache
      mvn eclipse:eclipse

   This generates the :file:`.project` and :file:`.classpath` files used to define an Eclipse
   project.

#. Create a new workspace in Eclipse

#. Configure the Maven repository

   * Navigate to :menuselection:`Window --> Preferences --> Java --> Build Path --> Class Path Variables`
   * Add a new variable M2_REPO, and set the path to :file:`.m2/repository` in your home directory
     as shown below:

     ==================== =========================================
     System               PATH
     ==================== =========================================
     Windows              :file:`C:\\\\Users\\You\\.m2\\repository`
     Linux or Mac         :file:`~/.m2/repository`
     ==================== =========================================

#. Next we will configure Eclipse for working on GeoWebCache files.

   * Navigate to to :menuselection:`Java --> Code Style --> Formatter`.
   * Click on Import, choose :file:`geowebcache/tools/formatter.xml`

#. There is also a :file:`geowebcache/tools/codetemplates.xml` to assist
   with creating new files.

#. Now we need to import the actual project:

   * Open the appropriate wizard menuselection:`File --> Import --> Existing Projects into Workspace`
   * Choose your :file:`geowebcache` folder

   This step depends on the :file:`.project` and :file:`.classpath` files generated
   by ``mvn eclipse:eclipse`` above.

#. To run GeoWebCache use the main menu :menuselection:`Run --> Debug Configurations` and double-click on Java Configurations

   * **Set Name:** :kbd:`GWC`
   * **The Project:** :kbd:`geowebcache`
   * For main class, set **Start**

   Then press :guilabel:`Close`, or :guilabel:`Debug` if you want to try it right away.

Contributing patches
--------------------

The preferred way of providing patches is to create an issue in GitHub, develop the patch, and then make a GitHub Pull Request referencing the ticket.  If appropriate please backport fixes to the Stable and Maintenance branches.  New features may be backported if they have been on Master for a month without issue and if they are backward compatible for users and down stream developers.

In addition to creating the issue ticket, you are highly encouraged to bring it up on the `GeoWebCache Developers mailing list <https://lists.sourceforge.net/lists/listinfo/geowebcache-devel>`_ first.  Other developers or users may have considered the problem before or have other useful input.

Please include unit tests for any patches that change behaviour: For a bug fix, include tests to confirm the bug is fixed, for a new feature include tests to check that the feature works. Please also include the copyright header for the LGPL 3.0 in any new source files.

Please squash your working commits before creating a pull request.  The commits in a pull request should represent clear semantic parts of the patch, not the work history.  :kbd:`Added extension point` -> :kbd:`New module implementing extension point` -> :kbd:`Added documentation for new module`  is a good break down while  :kbd:`Did some work` -> :kbd:`Work from tuesday` -> :kbd:`Stuff I forgot` is not.  

Avoid non-semantic whitespace and formatting changes as this makes your intent less clear and makes it harder to understand the change history.  If you do clean things up, please do so via a separate commit.  In particular, please avoid using automatic code formatters to reformat an entire existing file.

Use javadoc comments to document APIs and additional comments to clarify obtuse code.  Do not use comments to identify yourself as that's what the Git history is for.  Do not leave commented out code blocks. Commented out examples in human readable config files however are OK.
