.. _tms:

TMS - Tile Map Service
======================

TMS is a predecessor to WMTS, defined by OSGeo. See http://wiki.osgeo.org/wiki/Tile_Map_Service_Specification

The patch for this functionality was provided by Mikael Nyberg. The paths were originally of the form

``http://servername/contextpath/service/tms/1.0.0/layername/z/x/y.formatExtension``


for instance

``http://localhost:8080/geowebcache/service/tms/1.0.0/topp:states/0/0/0.png``

These are still supported, but in order to support multiple formats and spatial reference systems, the general path as of 1.2.2 is 

``http://servername/contextpath/service/tms/1.0.0/layername@grisetId@formatExtension/z/x/y.formatExtension``


As of version 1.2.2, the TileMapService document can be retrieved from 

``http://servername/contextpath/service/tms/1.0.0/``

Similarly, the TileMap documents are available at 

``http://servername/contextpath/service/tms/1.0.0/layername@grisetId@formatExtension``


The TMS specification has the TileGrid Map origin located at bottom left so Y coordinates grow up moving towards north.
A vendor parameter "flipY=true" can be appended to the path to support Y coordinates numbered in the opposite direction, from north southwards.

``http://servername/contextpath/service/tms/1.0.0/layername/z/x/y.formatExtension?flipY=true``


