.. _prerequisites.linux:

Linux
=====

Due to licensing issues, many Linux distributions come with a variety of Java environments. Additionally, to minimize the chance of a security breach with default settings, most versions of Tomcat are configured to not allow access to the file system.  Therefore, it is highly recommended to follow these instructions, even if you already have a servlet container set up.

Java Runtime Environment
------------------------

In your browser, navigate to `<http://www.java.com/en/download/manual.jsp>`_, and download the latest JRE (SE is fine; you do not need FX or EE). Make sure to select **Linux_x64** if you are running on an x86_64 kernel.  Take note of where you saved the file. (This document will assume the file is saved in ``/home/user/Download/`` and is named ``jre16.bin`` although the file name will likely be different.)

Next, open a shell and switch to superuser. Depending on your distribution, type ``sudo su`` or just ``su``. Then, ``cd /opt`` and run the command ``sh /home/user/Download/jre1.6.bin>``.  This should install Java into /opt. If you receive the message ``ELF not found``, it probably means you need to get the ``Linux`` (i586) version instead of ``Linux_64``.

Apache Tomcat
-------------

Back in your browser, navigate to `<http://tomcat.apache.org>`_, find the **Tomcat 6.x** link in the **Downloads** section, and save the ``tar.gz`` file listed under **Binary Distributions / Core**.  Back in your superuser shell, unpack this file as well by running ``tar xzvf /home/user/Download/apache-tomcat-a.b.c.tar.gz>`` (Make sure to use the correct file name.) 

Set the owner of the extracted directory to be your user account: ``chown <user> apache-tomcat-a.b.c``.

Using your favorite text editor, open ``/opt/apache-tomcat-a.b.c/bin/catalina.sh``.  Because we don't want to worry about system-wide settings, we will make our changes in the top of this file. Find the line that starts with ``# OS specific support`` (around line 81), and insert the following right before, making sure to input the correct path to your JRE::

  export PATH="/opt/jre1.6/bin:$PATH"
  JAVA_OPTS="-server -Xmx256M"

The first line sets the the JRE just installed is the one that Tomcat uses.  The second line tells Tomcat to run with server settings and to use 256MB for heap memory. (It may be possible to run with less heap memory, but this is no recommended.)  On big installations you will want to use 1024MB or more. Note that this resource is shared among all servlets running in the container, so if you add more servlets later you may have to adjust this number.

Access Control
--------------

If you wish to use Tomcat's web administration tool, you will need to create an account for the administrator.

Open ``/opt/apache-tomcat-a.b.c/conf/tomcat-users.xml`` in a text editor. Immediately after the line containing ``<tomcat-users>``, insert::

  <role rolename="manager"/>
  <user username="tomcat" password="s3cret" roles="manager"/>

Replace ``s3cret`` with your actual password. After making this change you will have to restart Tomcat.

Controlling Tomcat
------------------

Running as your own user, you should be able to start and stop Tomcat by using the scripts
``/opt/apache-tomcat-a.b.c/bin/startup.sh`` and ``/opt/apache-tomcat-a.b.c/bin/shutdown.sh``

Verify Tomcat is running by navigating to to http://localhost:8080 (the default location of the Tomcat web interface). If Tomcat is running correctly, you should see a page congratulating you on a successful installation.

.. note:: Tomcat shutdown is asynchronous, meaning that Tomcat may still be running even though the script returns. Check by using ``ps -ef | grep tomcat``, and if all else fails, use ``kill -9 <process id>`` to terminate the Tomcat process.

Continue to :ref:`installing_geowebcache`
