.. _linux:

Linux
=====

How to Install GeoWebCache under Linux
--------------------------------------

Due to licensing issues, many Linux distributiosn come with a variety of Java environments. Additionally, to minimize the chance of a security breach with default settings, most versions of Tomcat are configured to not allow access to the filesystem.

Download and Install Java
-------------------------

Go to http://java.sun.com , download the latest Java SE (JRE 6 Update 17, you do not need FX or EE). Get Linux_x64 if you are running on a x86_64 kernel. Note that you can continue without registering. Get the .bin version by clicking on `` jre-6u17-linux-x64.bin``, even if you have an RPM-based system. Take note of where you saved the file, lets assume it is in ``/home/user/Download``

Next, open a shell and switch to superuser. Depending on your distribution, type ``sudo su`` or just ``su``. Then, ``cd /opt`` and run the command ``sh /home/user/Download/jre-6u17-linux-x64.bin``. This should put Java into /opt. If you receive the message``ELF not found``, it probably means you need to get the ``Linux`` (i586) version instead of ``Linux_64``.


Install Tomcat
--------------

Go back to your browser and point it to http://tomcat.apache.org , find the Downloads section for Tomcat 6.x and save the ``tar.gz`` file listed under Core. Now back in your superuser shell, unpack this file as well by running ``tar xzvf /home/user/Download/apache-tomcat-6.0.20.tar.gz``. 

Next we need to fix the permissions, we'll assume you are going to run Tomcat under your own user: ``chown user apache-tomcat-6.0.20``

Finally we need to make some changes to Tomcat's scripts. Using your favorite texteditor, open /opt/apache-tomcat-6.20/bin/catalina.sh

Because we do not want to worry about systemwide settings, we will make our changes in the top of this file. Find the line that starts with ``# OS specific support`` (around line 81), and insert the following right before.
``export PATH="/opt/jre1.6.0_17/bin:$PATH"
JAVA_OPTS="-server -Xmx256M"``

The first line ensures that we use the Java version we just installed, the second tells Tomcat to use 256Mbyte for heap memory and run with server settings. It may be possible to run with less heap memory. On big installations you will want to use 1024M or more. Note that this resource is shared among all servlets running in the container, so if you add more servlets later you may have to adjust this number.

Access Control
--------------

If you wish to use Tomcat's web administration tool, you need to create an account for the administrator.

Do this by opening ``/opt/apache-tomcat-6.0.20/conf/tomcat-users.xml`` in a text editor. Immediately after ``<tomcat-users>`` insert
``<role rolename="manager"/>
<user username="tomcat" password="s3cret" roles="manager"/>``

Replace s3cret with your actual password. After making this change you have to restart Tomcat.


Controlling Tomcat
------------------

Running as your own user, you should be able to start and stop tomcat by using the scripts
``/opt/apache-tomcat-6.0.20/bin/startup.sh`` and ``/opt/apache-tomcat-6.0.20/bin/shutdown.sh``

Check by pointing a browser to http://localhost:8080, you should see a page congratulating you on a succesful installation.

Note that shutdown is asynchronous, meaning that Tomcat may still be running even though the script returns. Check by using ``ps -ef |grep tomcat``, and use ``kill -9 <process id>`` if all else fails.
