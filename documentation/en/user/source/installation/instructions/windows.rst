.. _windows:

Windows
=======

Download and Install Java
-------------------------

Go to http://java.sun.com , download the latest Java SE (JRE 6 Update 17, you do not need FX or EE) and install it.

Install Tomcat
--------------

Go back to your browser and point it to http://tomcat.apache.org , find the Downloads section for Tomcat 6.x and save the ``Windows Service Installer``. After installing, you should have a small tray icon. You can right click on it to ensure that it is running with the latest version of Java, and assign at least 256M of heap memory. Note that this resource is shared among all servlets running in the container, so if you add more servlets later you may have to adjust this number.

Access Control
--------------

If you wish to use Tomcat's web administration tool, you need to create an account for the administrator.

Do this by opening ``C:\Program Files\Apache Software Foundation\Tomcat 6.20\conf\tomcat-users.xml`` in a text editor. Immediately after ``<tomcat-users>`` insert
``<role rolename="manager"/>
<user username="tomcat" password="s3cret" roles="manager"/>``

Replace s3cret with your actual password. After making this change you have to restart Tomcat.

Controlling Tomcat
------------------

By default, Tomcat will now start with your computer. You can change this by going through the Control Panel (or Computer Management) and changing the settings of the Tomcat service.

Point your browser to http://localhost:8080 , you should see a page congratulating you on a succesful installation.
