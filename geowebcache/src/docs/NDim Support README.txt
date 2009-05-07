                                  NDim Support
                                  ------------

Introduction
------------
The Open Geospatial Consortium, Inc (OGC) is an international industry consortium.
OGC issues recommendations how a Web Map Server should expose itself on the 
Internet. For further reading on this topic please see:
http://www.opengeospatial.org/standards/wms

In many regards GeoWebCache (GWC) can be considered a WMS (in many others it is 
not). An effort has been made to make GWC a bit more compliant with OGC:s WMS 
specification. Support for N-dimensional data has been a part of the WMS spec.
at least since version 1.1.1 (Jan 2002). Not many WMS:s provide N-dimensional 
data but it is starting to be more and more frequent.


Features
--------
GWC has been equipped with the feature of being able to receive, understand and
process dimensional request parameters. These parameters are TIME, ELEVATION and
any dimension with the prefix DIM_.
http://server.org/wms?REQUEST=GetMap&...&TIME=2009-01-01T00:00:00&ELEVATION=10.0&DIM_CUSTOM=1,2,3

In order to be able to understand and process these parameters it is essential 
that the data producer, usually a WMS, is able to expose this dimension for the 
requested layer. GWC has thus been equipped with the feature to read and 
understand the <Dimension> and <Extent> element of a WMS Capabilities document.
Further more the WMS must specify the name of the dimension in the name attribute
of the <Dimension> element, same goes for units. Optionally the WMS may specify
unitSymbol, defaultValue, multipleValues, nearestValue. An extent must always be
specified. The format for this varies a bit between the WMS spec. versions (please
see respective WMS specification for full details):

1.1.1
	<Layer>
	...
		<Dimension name="time" units="ISO8601"/>
		<Extent name="time" default="2000-10-17">1996-01-01/2000-10-17/P1D</Extent>
	</Layer>   

1.3.0
	<Layer>
	...
		<Dimension name="time" units="ISO8601" default="2000-10-17">1996-01-01/2000-10-17/P1D</Dimension>   
	</Layer>
In ver. 1.3.0 the <Extent> element has been removed and all attributes are set on
the <Dimension> element.


Technical overview
------------------
As mentioned in the previous section GWC has been equipped with the feature of 
being able to gather information about dimensions and extents of a layer. This 
is the starting point of N dimensional support. When GWC initializes (or is being
reinitialized) all configurations are read and all layers are stored as TileLayers
in the TileLayerDispatcher. Layers that comes from XMLConfiguration.getTileLayers()
and GetCapabilitiesConfiguration.getTileLayers() are created as WMSLayers (extends TileLayer).
WMSLayer has got a new property: dimensions. In this new property all 
dimensions of that layer is stored in a java.util.Map<String, Dimension> where 
the name of the dimension is used a key.

By doing this all dimension information is stored for each layer in the 
TileLayerDispatcher and can later be used for validating the incoming request.

WMSParameters has like WMSLayer got a new property dimension. But in this case
it is a Map<String, String> where the key is the name of the dimension and the
value the requested extent.

When asking the service for a tile (WMSService.getTile()) the service will ask 
the tileLayerDispatcher for a tileLayer (which almost always is a WMSLayer) and 
create a WMSParameters object based on the incoming Request object. The dimension 
maps in the WMSLayer and WMSParameters are then compared. The comparison is done
in several steps:
 - loop dimensions in parameters:
 	- check that dimensions exists in layer
 	- check that a dimension with the same name exists in layer
 	- do a simple format check of the parameter value
 - loop dimensions in layer:
    - for each dimension ask the dimension for a value

The last step in the list above is where the interesting things is happening.
When setting up the spring context you are able to configure an extentHandlerMap.
This map uses the unit of a dimension to set a handler on the dimension.
