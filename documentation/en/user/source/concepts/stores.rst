.. _concepts.stores:

Storage components
==================

GeoWebCache historically used to have three storage components, responsible for both tile and tile metadata handling: the blob store, the metastore and the disk quota subsystem.

The **blobstore** is a storage mechanism for tiles, whose default implementation is file system based.

The **metastore** was an optional H2 based storage mechanism for meta-information about tiles, such as tile creation time, size and usage of request parameters.

The **disk quota** mechanism uses a nosql embedded database to track the tiles disk usage and expire tiles based on user set policies.

Since GeoWebCache 1.4.0 the metastore was replaced with a full filesystem based solution, making the blobstore responsible for the information previously tracked by the metastore.

By default, the storage location for both of these stores is the temporary storage directory specified by the servlet container (a directory called :file:`geowebcache` will be created there.). If this directory is not available, GeoWebCache will attempt to create a new directory in the location specified by the ``TEMP`` environment variable.  Inside there will be one directory for the disk quota (called :file:`diskquota_page_store` by default), and blobstore directories named after each cached layer (such as :file:`topp_states` for the layer ``topp:states``).


