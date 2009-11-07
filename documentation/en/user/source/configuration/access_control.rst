.. _access_control:

Access Control
--------------

GeoWebCache requires authentication to perform certain operations, such as reconfiguration or seeding. By default the username and password are ``geowebcache`` and ``secured``. This is probably a good time to change this. 

Open ``WEB-INF\users.properties`` and change ``geowebcache`` to your desired username, ``secured`` to your password of choice. It is recommended that you use only ASCII characters, note that the password is stored as plain text.

Fine-grained control can be implemented using an external proxy or servlet filter, or within GeoWebCache by using a request filter. Advanced users who understand ACEGI may want to edit ``acegi-config.xml`` as well.
