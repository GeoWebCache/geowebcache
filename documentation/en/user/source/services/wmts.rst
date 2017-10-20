.. _wmts:

WMTS - Web Map Tiling Service
=============================

The Web Map Tiling Service, or WMTS for short, is an OGC standard currently undergoing ratification.

GeoWebCache fully implements WMTS using KVP, and is seeking funding for the RESTful and SOAP based approaches.

On the front page of your GeoWebCache instance you will find a link to the WMTS GetCapabilities document.

WMTS - RESTful
--------------
WMTS RESTful API supports HTTP GET operation allowing clients to retrieve the following resources:

* Capabilities document
* Tile
* Feature info

Clients need to parse the capabilities document to discover how to invoke the RESTful API. The specification doesn’t define any resource path or query parameters, each implementation is free to use the paths and query parameters they want. For tiles resources and features infos resources the paths need to be defined in the capabilities document using a template language with some mandatory terms.

From now on when referring to a resource URL <baseUrl> will be used to represent the base path of the URL. In the case of the standalone GWC the base shall be:

.. code-block:: c

   http://[:port]/geowebcache/rest/wmts
   
Capabilities Document
`````````````````````
The following RESTful end-point can be used to retrieve the capabilities document:

.. code-block:: c

   <baseUrl>/WMTSCapabilities.xml

The capabilities document contains the top level elements (direct child of the root element) <ServiceMetadataURL> that tells clients the WMTS capabilities document resource URI (both services and RESTful based URL are returned):

.. code-block:: c

   <ServiceMetadataURL xlink:href="<baseUrl>/WMTSCapabilities.xml"/>

Each WMTS published layer has several <ResourceURL> elements that define the resources associated to that layer that can be retrieved through the RESTful API. The available resources can be divided in two categories: tiles (GetTile) and feature info (GetFeatureInfo).
   
Tile Resources
``````````````
The tile resources <ResourceURL> is repeated for each supported image format of the layer:

.. code-block:: c

   <ResourceURL format="<imageformat>" resourceType="tile" template="<baseUrl>/<full layer name>/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=<imageFormat>&<firstDimensionName>={firstDimensionValue} ... &<lastDimensionName>={lastDimensionValue}">

Since is not possible to predict the dimensions (time, elevation, etc ...), the resource template make available all the layers dimensions as query parameters and is up to the client to set the values for the dimensions it wants to use.

Consider a layer named temperature that has two dimensions time and elevation and supports PNG and JPEG image formats, it also has several styles and tile matrix sets. The <Layer> element corresponding to the temperature layer in the capabilities document contain this two <ResourceURL> childs:

.. code-block:: c

   <ResourceURL format="image/png" resourceType="tile" template="<baseUrl>/temperature/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/png&time={time}&elevation={elevation}">
   
.. code-block:: c

   <ResourceURL format="image/jpeg" resourceType="tile" template="<baseUrl>/temperature/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/jpeg&time={time}&elevation={elevation}">
   
The request sent by clients look like this:

.. code-block:: c

   <baseUrl>/temperature/default/WholeWorld_CRS_84/30m/4/5?format=image/png&time=2016-02-23T03:00:00.000Z&elevation=500
   
Note that only the format query parameter is mandatory, the client may choose to not use the dimensions query parameters. If an empty value is send it will be ignored.

Feature Info Resources
``````````````````````
The feature info resources <ResourceURL> is repeated for each supported feature info format of the layer:

.. code-block:: c

   <ResourceURL format="<featureInfoFormat>" resourceType="FeatureInfo" template="<baseUrl>/<full layer name>/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=<featureInfoFormat>&<firstDimensionName>={firstDimensionValue} ... &<lastDimensionName>={lastDimensionValue}">

Feature info <ResourceURL> elements are very similar to the tile resources ones. Layer dimensions are handled in the same way dimensions are handled for tile resources.

Consider a layer named temperature that has two dimensions time and elevation and supports HTML and XML feature info formats, it also has several styles and tile matrix sets. The <Layer> element corresponding to the temperature layer in the capabilities document contains these two <ResourceURL> children:

.. code-block:: c

   <ResourceURL format="text/html" resourceType="FeatureInfo" template="<baseUrl>/temperature/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/html&time={time}&elevation={elevation}">

.. code-block:: c

   <ResourceURL format="text/xml" resourceType="FeatureInfo" template="<baseUrl>/temperature/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/xml&time={time}&elevation={elevation}">

The request sent by clients look like this:

.. code-block:: c

   <baseUrl>/temperature/default/WholeWorld_CRS_84/30m/4/5/23/35?format=text/html&time=2016-02-23T03:00:00.000Z&elevation=500

Note that only the format query parameter is mandatory, the client may choose to not use the dimensions query parameters. If an empty value is send it will be ignored.

Exceptions Reports
``````````````````
In the case of an exception is returned an exception report encoded in XML . The produced XML report shall look like this:

.. code-block:: c

   <?xml version="1.0" encoding="UTF-8"?>
   <ExceptionReport version="1.1.0"
      xmlns="http://www.opengis.net/ows/1.1"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.opengis.net/ows/1.1 
      http://geowebcache.org/schema/ows/1.1.0/owsExceptionReport.xsd">
      <Exception exceptionCode="InvalidParameterValue" locator="INFOFORMAT">
         <ExceptionText>
            Unable to determine requested INFOFORMAT, text/invalid
         </ExceptionText>
      </Exception>
   </ExceptionReport>

Looking at the exception above we can understand that an invalid format (text/invalid) was requested.

Examples
````````
In this section are showed some examples of RESTful API using the demos layers shipped with GWC The following example will request the capabilities document

.. code-block:: c
   
   curl -u geowebcache:secured "http://localhost:8080/geowebcache/rest/wmts/WMTSCapabilities.xml"

The response will contain the ResourceURL and ServiceMetadataURL sections:

.. code-block:: c
   
   ...
   <Layer>
   <ows:Title>topp:states</ows:Title>
   ...
   <ResourceURL format="image/gif" resourceType="tile" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/gif"/>
   <ResourceURL format="image/jpeg" resourceType="tile" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/jpeg"/>
   <ResourceURL format="image/png" resourceType="tile" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/png"/>
   <ResourceURL format="image/png8" resourceType="tile" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}?format=image/png8"/>
   <ResourceURL format="text/plain" resourceType="FeatureInfo" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/plain"/>
   <ResourceURL format="text/html" resourceType="FeatureInfo" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=text/html"/>
   <ResourceURL format="application/vnd.ogc.gml" resourceType="FeatureInfo" template="http://localhost:8080/geowebcache/rest/wmts/topp:states/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}/{J}/{I}?format=application/vnd.ogc.gml"/>
   </Layer>
   ...

.. code-block:: c
   
   ...
   <ServiceMetadataURL xlink:href="http://localhost:8080/geowebcache/service/wmts?REQUEST=getcapabilities&amp;VERSION=1.0.0"/>
   <ServiceMetadataURL xlink:href="http://localhost:8080/geowebcache/rest/wmts/WMTSCapabilities.xml"/>
   </Capabilities>

Them is possible obtain Tile Resources with:

.. code-block:: c

   curl -u geowebcache:secured "http://localhost:8080/geowebcache/rest/wmts/topp:states/EPSG:2163/EPSG:2163:0/0/0?format=image/gif"

and Feature Info Resources with:

.. code-block:: c
   
   curl -u geowebcache:secured "http://localhost:8080/geowebcache/rest/wmts/topp:states/EPSG:2163/EPSG:2163:0/0/0/0/0?format=text/html"

