.. _configuration.layers.expiretiles:

Tile expiration
===============

GeoWebCache allows controlling the expiration of tiles both at server and client level. Modifications must be made in the general configuration file, ``geowebcache.xml`` as explained below:

Server tile expiration
----------------------
The tag ``<expireCacheList>`` controls how old a tile may be before it is fetched again from the server. The default value is ``0``, which means never, otherwise it must be specified in seconds.

Attributes ``minZoom`` and ``expiration`` of the element ``<expirationRule>`` can be edited to define a list of expiration rules, so that cache expiration can be controlled per layer and per zoom level.

.. code-block:: xml

    <expireCacheList>
      <expirationRule minZoom="0"  expiration="14400" />
      <expirationRule minZoom="10" expiration="7200" />
    </expireCacheList>

``minZoom``
 First zoom level. The list should always start with ``0`` and be monotonically increasing. 

``expiration``
 Defines the number of seconds a tile remains valid on the server. Subsequent requests will result in a new tile being fetched. The default is to cache forever. Special expiration values are ``-1`` to disable caching and ``-2`` to never expire.


Client tile expiration
----------------------

The tag ``<expireClientsList>`` controls the number of seconds that a client should cache a tile it has received from GeoWebCache. The default is to use the same expiration time as provided by the WMS server. If this value is not provided, 3600 seconds (one hour) is used.

Attributes ``minZoom`` and ``expiration`` of the element ``<expirationRule>`` can be edited to define a list of expiration rules.

.. code-block:: xml

    <expireClientsList>
      <expirationRule minZoom="0" expiration="7200" />
      <expirationRule minZoom="10" expiration="600" />
    </expireClientsList>

``minZoom``
 This list must start with ``minZoom="0"`` and be monotonically increasing.

``expiration``
 Can either be a value in number of seconds or ``0`` to disable the header. A special value of ``-1`` may be used to set "no-cache" headers. By default the expiration header from the WMS backend is used. If it is not set or not available (for example, when no request has been forwarded to backend since startup) then the value is set to ``3600`` seconds. 

