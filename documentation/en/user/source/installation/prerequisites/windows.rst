.. _prerequisites.windows:

Windows
=======

Java Runtime Environment
------------------------

Make sure you have a Java Runtime Environment (JRE) installed on your system. GeoWebCache requires a Java 17 or Java 21 environment.

1. Download an OpenJDK release for your platform:

   * https://adoptium.net/temurin/releases/?version=17 Temurin 17 (LTS) (Recommended)

2. Choose the options to:

   * Updating the JAVA_HOME environment variable
   * Add the installation to the PATH environment variable

See :doc:`/installation/upgrading` for compatibility table.

Apache Tomcat
-------------

1. Navigate to `<https://tomcat.apache.org>`_, find the **Tomcat 9.x** link in the **Downloads** section, and save the ``Windows Service Installer`` file listed under **Binary Distributions / Core**.

   * Tomcat 9 Required: GeoWebCache uses the JavaEE environment last supported in Tomcat 9.
   
   * Tomcat 10 Unsupported: GeoWebCache is not yet compatibile with the JakartaEE environment used by Tomcat 10 and newer.
2. Run this application to install Tomcat as a Windows service.
   
    After installing, use the small system tray icon. You can right click on it to ensure that it is running with the latest version of Java, and assign at least 256MB of heap memory.
    
    Note that this resource is shared among all servlets running in the container, so if you add more servlets later you may have to adjust this number.

Access Control
^^^^^^^^^^^^^^

If you wish to use Tomcat's web administration tool, you will need to create an account for the administrator.

Do this by opening the :file:`conf\tomcat-users.xml` file in from your Tomcat Program Files directory (by default ``C:\Program Files\Apache Software Foundation\Tomcat 9.0`` in a text editor.  Immediately after ``<tomcat-users>`` insert:

.. code-block:: xml

   <role rolename="manager"/>
   <user username="tomcat" password="s3cret" roles="manager"/>

Replace ``s3cret`` with your actual password. After making this change you will have to restart Tomcat.

Controlling Tomcat
^^^^^^^^^^^^^^^^^^

By default, Tomcat will now start automatically with your computer. You can modify this by going through the :menuselection:`Control Panel -> Administrative Tools -> Services`, and editing the settings for the **Apache Tomcat** service.

Verify Tomcat is running by navigating to to http://localhost:8080 (the default location of the Tomcat web interface). If Tomcat is running correctly, you should see a page congratulating you on a successful installation.

Continue to :ref:`installing_geowebcache`
