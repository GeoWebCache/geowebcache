.. _prerequisites.windows:

Windows
=======

Java Runtime Environment
------------------------

In your browser, navigate to `<http://www.java.com/en/download/manual.jsp>`_, and download the latest JRE (SE is fine; you do not need FX or EE).  You can use either the Online or Offline installer.

Apache Tomcat
-------------

Back in your browser, navigate to `<http://tomcat.apache.org>`_, find the **Tomcat 6.x** link in the **Downloads** section, and save the ``Windows Service Installer`` file listed under **Binary Distributions / Core**.  Run this application to install Tomcat as a Windows service.  After installing, you should have a small system tray icon. You can right click on it to ensure that it is running with the latest version of Java, and assign at least 256MB of heap memory. Note that this resource is shared among all servlets running in the container, so if you add more servlets later you may have to adjust this number.

Access Control
--------------

If you wish to use Tomcat's web administration tool, you will need to create an account for the administrator.

Do this by opening the conf\tomcat-users.xml`` file in from your Tomcat Program Files directory (by default ``C:\Program Files\Apache Software Foundation\Tomcat a.b`` in a text editor.  Immediately after ``<tomcat-users>`` insert::

  <role rolename="manager"/>
  <user username="tomcat" password="s3cret" roles="manager"/>

Replace ``s3cret`` with your actual password. After making this change you will have to restart Tomcat.

Controlling Tomcat
------------------

By default, Tomcat will now start automatically with your computer. You can modify this by going through the :menuselection:`Control Panel -> Administrative Tools -> Services`, and editing the settings for the **Apache Tomcat** service.

Verify Tomcat is running by navigating to to http://localhost:8080 (the default location of the Tomcat web interface). If Tomcat is running correctly, you should see a page congratulating you on a successful installation.

Continue to :ref:`installing_geowebcache`
