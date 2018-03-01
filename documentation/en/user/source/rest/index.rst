.. _rest:

REST API
========

This section will discuss the GeoWebCache REST API.

.. toctree::
   :maxdepth: 1
   
   global.rst
   layers.rst
   gridsets.rst
   blobstores.rst
   seed.rst
   diskquota.rst
   masstruncate.rst
   statistics.rst



Formats and representations
---------------------------

A ``format`` specifies how a resource should be represented. A format is used:

- In an operation to specify what representation should be returned to the 
  client
- In a POST or PUT operation to specify the representation being sent to the 
  server

In a GET operation the format can be specified in a number of ways. The first is
with the ``Accepts`` header. For instance setting the header to "text/xml" would
specify the desire to have the resource returned as XML. The second method of 
specifying the format is via file extension. For example consider the resource 
"foo". To request a representation of foo as XML the request uri would end with
"foo.xml". To request as JSON the request uri would end with "foo.json". When no
format is specified the server will use its own internal format, usually html.

In a POST or PUT operation the format specifies 1) the representatin of the 
content being sent to the server, and 2) the representation of the resposne to
be sent back. The former is specified with the ``Content-type`` header. To send
a representation in XML, the content type "text/xml" or "application/xml" would
be used. The latter is specified with the ``Accepts`` header as specified in the
above paragraph describing a GET operation.

The following table defines the ``Content-type`` values for each format: 

.. list-table::
   :header-rows: 1

   * - Format
     - Content-type
   * - XML
     - text/xml
   * - JSON
     - application/json
   * - HTML
     - application/html

Authentication
--------------

POST, PUT, and DELETE requests (requests that modify resources) require the 
client to be authenticated. Currently the only supported method of 
authentication is Basic authentication.

Status codes
------------

A Http request uses a ``status code`` to relay the outcome of the request to the
client. Different status codes are used for various purposes through out this 
document. These codes are described in detail by the `http specification <http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html>`_.

