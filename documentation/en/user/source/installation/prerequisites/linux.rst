.. _prerequisites.linux:

Linux
=====

Many linux distirbutions provide their own distirbution of OpenJDK, or you may install your own from OpenJDK or Adoptium (as recommended below).

Caution is required before considering your Linux distirbution of Apache Tomcat:

* Package manager may recommend or udpate to Apache Tomcat 10 or newer (which is not supported by GeoWebCache).
* Tomcat may be configured, in the interests of security, to restrict access to the local file system.

We recommend providing your own environment as outlined below.

Java Runtime Environment
------------------------

Make sure you have a Java Runtime Environment (JRE) installed on your system. GeoWebCache requires a Java 17 or Java 21 environment, available from `OpenJDK <https://openjdk.java.net/>`__, `Adoptium <https://adoptium.net/>`_, or provided by your OS distribution.

See :doc:`/installation/upgrading` for compatibility table.
  
Apache Tomcat
-------------

Back in your browser, navigate to `<https://tomcat.apache.org>`_, find the **Tomcat 9.x** link in the **Downloads** section, and save the ``tar.gz`` file listed under **Binary Distributions / Core**.  Back in your superuser shell, unpack this file as well by running ``tar xzvf /home/user/Download/apache-tomcat-9.0.106.tar.gz>`` (Make sure to use match the version numbers you downloaded name.) 

Set the owner of the extracted directory to be your user account: ``chown <user> apache-tomcat-a.b.c``.

Using your favorite text editor, open ``/opt/apache-tomcat-9.0.106/bin/catalina.sh``.  Because we don't want to worry about system-wide settings, we will make our changes in the top of this file. Find the line that starts with ``# OS specific support`` (around line 81), and insert the following right before, making sure to input the correct path to your JRE:

.. code-block:: bash

  export PATH="/opt/jdk-17.0.15_6/bin:$PATH"
  JAVA_OPTS="-server -Xmx256M"

The first line sets the the JRE just installed is the one that Tomcat uses.  The second line tells Tomcat to run with server settings and to use 256MB for heap memory. (It may be possible to run with less heap memory, but this is no recommended.)  On big installations you will want to use 1024MB or more. Note that this resource is shared among all servlets running in the container, so if you add more servlets later you may have to adjust this number.

Access Control
^^^^^^^^^^^^^^

If you wish to use Tomcat's web administration tool, you will need to create an account for the administrator.

Open ``/opt/apache-tomcat-9.0.106/conf/tomcat-users.xml`` in a text editor. Immediately after the line containing ``<tomcat-users>``, insert::

  <role rolename="manager"/>
  <user username="tomcat" password="s3cret" roles="manager"/>

Replace ``s3cret`` with your actual password. After making this change you will have to restart Tomcat.

Controlling Tomcat
^^^^^^^^^^^^^^^^^^

Running as your own user, you should be able to start and stop Tomcat by using the scripts
``/opt/apache-tomcat-9.0.106/bin/startup.sh`` and ``/opt/apache-tomcat-a.b.c/bin/shutdown.sh``

Verify Tomcat is running by navigating to to http://localhost:8080 (the default location of the Tomcat web interface). If Tomcat is running correctly, you should see a page congratulating you on a successful installation.

.. note:: Tomcat shutdown is asynchronous, meaning that Tomcat may still be running even though the script returns. Check by using ``ps -ef | grep tomcat``, and if all else fails, use ``kill -9 <process id>`` to terminate the Tomcat process.

Continue to :ref:`installing_geowebcache`
