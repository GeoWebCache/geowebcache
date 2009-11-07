.. _access_control:

Access Control
--------------

GeoWebCache requires authentication to perform certain operations, such as reconfiguration or seeding. By default ``WEB-INF\users.properties`` looks like this:

.. code-block:: c

   geowebcache=secured,ROLE_ADMINISTRATOR


Change ``geowebcache`` to your desired username and ``secured`` to your password of choice. It is recommended that you use only ASCII characters.

Fine-grained control can be implemented using an external proxy or servlet filter, or within GeoWebCache by using a request filter. Advanced users who understand ACEGI may want to edit ``acegi-config.xml`` as well.
