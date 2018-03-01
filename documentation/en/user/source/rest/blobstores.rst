.. _rest.blobstores:

Managing BlobStores through the REST API
========================================

The REST API for BlobStore management provides a RESTful interface through which clients can 
programatically add, modify, or remove BlobStore configurations.

BlobStores list
---------------

``/rest/blobstores.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the list of available blobstores
     - 200
     - XML, JSON
   * - POST
     - 
     - 405
     - 
   * - PUT
     - 
     - 405
     - 
   * - DELETE
     - 
     - 400
     -

Sample request:

.. code-block:: xml

 curl -u geowebcache:secured  "http://localhost:8080/geowebcache/rest/blobstores"

Sample response:
 
.. code-block:: xml

    <blobStores>
      <blobStore>
        <name>defaultCache</name>
        <atom:link xmlns:atom="http://www.w3.org/2005/Atom" rel="alternate" href="http://localhost:8080/geowebcache/rest/blobstores/defaultCache.xml" type="text/xml"/>
      </blobStore>
    </blobStores>

BlobStore Operations
--------------------

``/rest/blobstores/blobstore.xml``

.. list-table::
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return the XML representation of the BlobStore
     - 200
     - XML, JSON
   * - POST
     - 
     - 405
     - 
   * - PUT
     - Add a new BlobStore or modify the definition/configuration of a BlobStore.
     - 200
     - XML, JSON
   * - DELETE
     - Delete a BlobStore
     - 200
     -

*Representations*:

- :download:`XML <representations/blobstore_xml.txt>`
- :download:`JSON <representations/blobstore_json.txt>`

REST API for BlobStores, cURL Examples
--------------------------------------

The examples in this section use the `cURL <http://curl.haxx.se/>`_
utility, which is a handy command line tool for executing HTTP requests and 
transferring files. Though cURL is used the examples apply to any HTTP-capable
tool or library.

Add BlobStore
+++++++++++++

Given a `blobstore.xml` file as the following:

.. code-block:: xml

    <FileBlobStore default="false">
      <id>blobStore1</id>
      <enabled>false</enabled>
      <baseDirectory>/tmp/blobStore1</baseDirectory>
      <fileSystemBlockSize>4096</fileSystemBlockSize>
    </FileBlobStore>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: application/xml" -d @blobstore.xml  "http://localhost:8080/geowebcache/rest/blobstores/blobStore1.xml"

Or if using the GeoServer integrated version of GeoWebCache:

.. code-block:: xml 

 curl -v -u user:password -XPUT -H "Content-type: application/xml" -d @blobstore.xml  "http://localhost:8080/geoserver/gwc/rest/blobstores/blobStore1.xml"

.. note:: To add other types of blobstores, refer to the  blobstore XML listed for that type under :ref:`configuration.storage.blobstore`.

Modify BlobStore
++++++++++++++++

Now, make some modifications to the blobstore definition on the `blobstore.xml` file:

.. code-block:: xml

    <FileBlobStore default="false">
      <id>blobStore1</id>
      <enabled>true</enabled>
      <baseDirectory>/var/opt/gwc/storage</baseDirectory>
      <fileSystemBlockSize>2048</fileSystemBlockSize>
    </FileBlobStore>

.. code-block:: xml 

 curl -v -u geowebcache:secured -XPUT -H "Content-type: application/xml" -d @blobstore.xml  "http://localhost:8080/geowebcache/rest/blobstores/blobStore1.xml"

Delete BlobStore
++++++++++++++++

Finally, to delete a blobstore, use the HTTP DELETE method against the blobstore configuration:

.. code-block:: xml 

 curl -v -u geowebcache:secured -XDELETE "http://localhost:8080/geoserver/gwc/rest/blobstores/blobStore1.xml"
