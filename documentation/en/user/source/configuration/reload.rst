.. _configuration.reload:

Reloading Configuration
=======================

After making a change to the GeoWebCache configuration, it is necessary to reload it for the changes to take effect.  There are three ways to do this.

Reload the servlet
------------------

The simplest way to reload the GeoWebCache configuration is to just reload the servlet from your servlet container.  In Tomcat's application manager, you can click the "Reload" link next to your GeoWebCache instance.

Reload via the web interface
----------------------------

GeoWebCache has a :ref:`webinterface` which contains a demo page.  On the bottom of the page that contains the list of layers, there is a button called "Reload Configuration".  You will be asked to authenticate (see the section on :ref:`configuration.security` for more details).

Reload via a POST request with cURL
-----------------------------------

The "Reload Configuration" button in the web interface merely makes a POST request to GeoServer's :ref:`rest` interface to reload the configuration.  Below is an example of this POST request executed using the command line utility `cURL <http://curl.haxx.se/>`_

  curl -u <admin>:<password> -d "reload" http://<GEOWEBCACHE_URL>/rest/

In this example, please make sure to change the ``<admin>`` to your administrator username, ``<password>`` for your administrator password, and <GEOWEBCACHE_URL> for the URL to your geowebcache endpoint (such as ``localhost:8080/geowebcache``).  See the section on :ref:`configuration.security` for more details about the username and password.