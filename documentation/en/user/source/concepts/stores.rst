.. _concepts.stores:

Blobstores and Metastores
=========================

GeoWebCache's storage subsystem has two components. The first is a storage mechanism for tiles, and is known as the **blobstore**. The second is an (optional) storage mechanism for meta-information about tiles, such as when each tile was created and size, and is known as the **metastore**.

By default, the storage location for both of these stores is the temporary storage directory specified by the servlet container.  (A directory called :file:`geowebcache` will be created there.)  If this directory is not available, GeoWebCache will attempt to create a new directory in the location specified by the ``TEMP`` environment variable.  Inside this will be one directory for the metastore (called :file:`meta_jdbc_h2` by default), and blobstore directories named for each cached layer (such as :file:`topp_states` for the layer ``topp:states``.


