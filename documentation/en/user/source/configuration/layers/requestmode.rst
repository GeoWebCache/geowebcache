.. _configuration.layers.request.mode:

WMS request modes
=================

The WMS standard officially supports only HTTP GET requests. This poses a limit on the number and size of parameters
in the request, as GET request lenght is commonly limited to 2048 chars (the standard poses no limits, the actual
value depends a lot on server configurations, routers and other network machinery in the middle).

GeoWebCache can receive requests also as POST, form URL encoded, removing such limit, but the remote WMS
server may or may not support this as it's not part of the standard (e.g., GeoServer does support it).

In case the server is known and this approach is supported, a layer can be configured to cascade requests
as POST adding the following configuration bit:

.. code-block:: xml

   <gwcConfiguration>
     <!-- ... -->
     <layers>
       <!-- ... -->
       <wmsLayer>
         <name>topp:states</name>
         <!-- ... -->
         <httpRequestMode>FormPost</httpRequestMode>
       </wmsLayer>
       <!-- ... -->
     </layers>
   </gwcConfiguration>
