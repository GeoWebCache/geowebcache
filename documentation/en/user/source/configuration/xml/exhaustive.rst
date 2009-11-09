.. _exhaustive:

Complete List of Configuration Elements
=======================================

The example below uses all configuration directives that are currently available in GeoWebCache. Some of them a mutually exclusive, please check the XSD documentation. Additionally, certain global settings are controlled through the Spring context in geowebcache-servlet.xml.

.. code-block:: xml

   <?xml version="1.0" encoding="utf-8"?>
   <gwcConfiguration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:noNamespaceSchemaLocation="http://geowebcache.org/schema/1.2.0/geowebcache.xsd"
                     xmlns="http://geowebcache.org/schema/1.2.0">

     <!-- ============================== GLOBAL SETTINGS ======================================== -->

     <!-- The following controls certain automatic upgrades. Update this and the namespaces above when 
          you update the configuration file manually -->
     <version>1.2.0</version>
     <!-- OPTIONAL This is the global timeout for HTTP connections to WMS backends. It is used both for the
          connection and the transfer, so the actual timeout may be much longer if the data is
          trickling back slowly. -->
     <backendTimeout>120</backendTimeout>
     <!-- OPTIONAL If the following is set to true you can append cached=false to requests and they will be
          proxied without caching. -->
     <cacheBypassAllowed>FALSE</cacheBypassAllowed>
     <!-- OPTIONAL By default GWC displays simple runtime statistics on the front page -->
     <runtimeStats>TRUE</runtimeStats>
     <!-- OPTIONAL A HTTP username to use for requests. Due to the design of the HTTP client included with
          Java this setting is global-->
     <httpUsername></httpUsername>
     <!-- OPTIONAL Password for the username above -->
     <httpPassword></httpPassword>

     <!-- ============================== GLOBAL FORMAT MODIFIERS ================================ -->

     <!-- OPTIONAL Format modifiers. These can also be defined per layer -->
     <formatModifiers>
       <!-- You can have one or more of these elements -->
       <formatModifier>
         <!-- REQUIRED This format modifier applies to JPEGs -->
         <responseFormat>image/jpeg</responseFormat>
         <!-- OPTIONAL PNG images are requested from the backend to avoid double compression -->
         <requestFormat>image/png</requestFormat>
         <!-- OPTIONAL For request formats that support it  -->
         <transparent>FALSE</transparent>
         <!-- OPTIONAL Background color -->
         <bgColor>0x0066DD</bgColor>
         <!-- OPTIONAL Palette used on WMS server -->
         <palette>somepalette</palette>
         <!-- OPTIONAL Tune the compression level, 1.0 is best quality -->
         <compressionQuality>0.9</compressionQuality>
       </formatModifier>
     </formatModifiers>

     <!-- ============================== GRID SETS ============================================= -->

     <!-- OPTIONAL Grid Sets. If you do not define any here, the only ones available will be
          worldwide EPSG:4326 and EPSG:900913, in addition to any that are automatically
          generated for the GetCapabilities configuration -->
     <gridSets>
       <!-- You can have one or more of these elements -->
       <gridSet>
         <!-- REQUIRED The name should imply something about use, extent or SRS -->
         <name>The Entire World</name>
         <!-- REQUIRED The SRS used for WMS requests. This is all the fallback lookup
              method for services that do not specify the grid set. Currently only
              EPSG codes are allowed, so you specify the number.
               -->
         <srs><number>4326</number</srs>
         <!-- REQUIRED The bounding box for the grid set. See the Grid Set documentation
              regarding how this is interpreted and adjusted. You should set this to the 
              maximum values for which the given SRS is valid, you will limit it with 
              gridSubset elements further down -->
         <extent>
           <coords>
             <double>-180.0</double>
             <double>-90.0</double>
             <double>180.0</double>
             <double>90.0</double>
           </coords>
         </extent>
         <!-- OPTIONAL Whether the grid should have a fixed top left origin. The default
              is to use the bottom left -->
         <alignTopLeft>FALSE</alignTopLeft>
         <!-- OPTIONAL (CHOICE) By default GWC tries to fit the extent into a single tile and then
              quarters this tile for every subsequent zoom level. Instead you can specify
              specific resolutions that are uniformly decreasing. Resolution is calculated
              as map degrees per pixel. So 180 degrees / 256 pixel = 0.703125 -->
         <resolutions>
              <double>0.703125</double>
              <double>0.3515625</double>
              <double>0.17578125</double>
         </resolutions>
         <!-- OPTIONAL (CHOICE) Alternatively, you can use scale denominators, calculated in the
              OGC fashion of assuming one pixel = 0.28mm. The numbers must be uniformly
              increasing. -->
         <scaleDenominators>
              <double>25000000<double>
              <double>2500000<double>
              <double>250000<double>
              <double>50000<double>
         </scaleDenominators>
         <!-- OPTIONAL (CHOICE) If quartering is good enough, but you wish to limit the number of
              zoom levels, you can do so by defining the number of levels -->
         <levels>20</levels>
         <!-- OPTIONAL The value of "1 map unit" in real world meters. This value is
          used for approximate scale calculations and is usually not very accurate.
          For lat/lon you should use (earth circumference) / 360.0 degrees = 111226.31
          For feet you use 0.3048 , and so forth
          -->
         <metersPerUnit>111226.31</metersPerUnit>
         <!-- OPTIONAL Some protocols, such as WMTS, support named scales. If desired you
              can define the names here, be sure to keep the same order as in the scale or
              resolution definition -->
         <scaleNames>
           <string>Low Resolution</string>
           <string>Medium Resolution</string>
           <string>High Resolution</string>
         </scaleNames>
         <!-- OPTIONAL Specify the tile height, in pixels -->
         <tileHeight>256</tileWidth>
         <!-- OPTIONAL Specify the tile width, in pixels -->
         <tileWidth>256</tileWidth>
       </gridSet>
     </gridSets>

     <!-- ============================== LAYERS ============================================== -->

     <layers>
       <wmsLayer>
         <!-- REQUIRED The name that clients should use to request this layer.
              Unlike WMS, it can contain commas, in case you want to combine a group -->
         <name>Some Layer</name>
         <!-- OPTIONAL Specify the formats that are supported by this layer.
              By default, image/png and image/jpeg are supported. Other options include
              image/png; mode=24bit, image/png24, image/png8, image/tiff, image/gif
         <mimeFormats><string>image/png</string><string>image/jpeg</string></mimeFormats>
         <!-- OPTIONAL See the global setting with the same name -->
         <formatModifiers>...</formatModifiers>
         <!-- OPTIONAL By default a layer will be valid for all of EPSG:4326 and EPSG:900913.
              This behavior is disabled if you specify a gridSubset, and this also allows
              you to specify that this layer is only valid for a subset of the grid -->
         <gridSubsets>
           <gridSubset>
             <!-- REQUIRED The name of the grid set for which this layer is valid 
                  "EPSG:4326" and "EPSG:900913" are valid by default, but this
                  example shows how to refer to the grid set definition we created
                  earlier.
             -->
             <gridSetName>The Entire World</gridSetName>
             <!-- OPTIONAL The bounding box for the grid subset. See the Grid Set documentation 
                  regarding how this is interpreted and adjusted. You should set this to the 
                  tightest bounds that cover your layer. If not specified, it is assumed that
                  this subset covers the entire set. -->
             <extent>
               <coords>
                 <double>-60.0</double>
                 <double>-70.0</double>
                 <double>-20.0</double>
                 <double>-80.0</double>
               </coords>
             </extent>
             <!-- OPTIONAL The first zoom level for which this layer is valid,
                  given as the zero-based index of the resolution / scaledenominator array -->
             <zoomStart>0</zoomStart>
             <!-- OPTIONAL The last zoom level for which this layer is valid,
                  given as the zero-based index of the resolution / scaledenominator array -->
             <zoomStop>25</zoomStop>
           </gridSubset>
         </gridSubsets>
         <!-- OPTIONAL (TODO, see XSD documentation) -->
         <requestFilters></requestFilters>
         <!-- REQUIRED One or more URLs to the WMS service to be used as backend -->
         <wmsUrl><string>http://yourserver/path/wms-service</string></wmsUrl>
         <!-- OPTIONAL The LAYERS= value to be sent to the backend server.
              If not specified, the name of this layer element is used. -->
         <wmsLayers>layer1,layer2</wmsLayes>
         <!-- OPTIONAL The STYLES= value to be sent to the backend server.
              If not specified, an empty string is used -->
         <wmsStyles></wmsStyles>
         <!-- OPTIONAL The metatiling factors used for this layer 
              If not specified, 3x3 metatiling is used for image formats -->
         <metaWidthHeight><int>3</int><int>3</int></metaWidthHeight>
         <!-- OPTIONAL The gutter is specified in pixels and represents extra padding 
              around the image that is sliced away when the tiles are created. 

              Certain WMS server have edge effects that can be elimited this
              way, but it can also result in labels being cut off -->
         <gutter>0</gutter>
         <!-- OPTIONAL The EXCEPTION= value to be sent to the backend server.
              You can also use vnd.ogc.se_inimage, but in that case GWC will 
              be unable to distinguish an error from a valid tile. -->
         <errorMime>application/vnd.ogc.se_xml</errorMime>
         <!-- OPTIONAL The VERSION= value to be sent to the backend server. 
              The default is 1.1.0 -->
         <wmsVersion>1.1.0</wmsVersion>
         <!-- OPTIONAL The TILED= value to be sent to the backend server.
              Should normally be omitted -->
         <tiled>FALSE</tiled>
         <!-- OPTIONAL The TRANSPARENT= value to be sent to the backend server.
              This is normally set to TRUE, but is not good for JPEG. 
              See formatModifiers. -->
         <transparent>TRUE</transparent>
         <!-- OPTIONAL The background color specified in hexadecimal
              Note that bgColor and transparent are mutually exclusive -->
         <bgColor>0xFF00AA</bgColor>
         <!-- OPTIONAL The PALETTE= value to be sent to the backend server.
              This parameter is usually omitted -->
         <palette><palette>
         <!-- OPTIONAL Any other parameters that should be sent with every
              request to the backend server. If needed, values should be
              URL escaped in the string below and separated by &amp;
              
              A typical parameter is the map= value in MapServer.

              This parameter is usually left blank -->
         <vendorParameters></vendorParameters>
         <!-- OPTIONAL The number of seconds a tile remains valid on the
              server. Subsequent requests will result in a new tile being fetched.
              The default is to cache forever. -->
         <expireCache>-1</expireCache>
         <!-- OPTIONAL The number of seconds that a client should cache
              a tile it has received from GWC. The default is to use the same
              expiration time as the WMS server provided. If this value is 
              not available, 2 hours is used. -->
         <expireClients>7200</expireClients>
         <!-- OPTIONAL See the global backendTimeout description -->
         <backendTimeout></backendTimeout>
         <!-- OPTIONAL Whether clients can append &cached=false and thereby use
              GWC as a proxy or service translator -->
         <cacheBypassAllowed></cacheBypassAllowed>
         <!-- OPTIONAL Whether this layer will represent itself as queryable 
              in the getcapabilities document, and proxy getfeatureinfo requests
              to the backend server. The default is false. -->
         <queryable>FALSE</queryable>
         <!-- OPTIONAL (TODO, see XSD documentation) -->
         <paramaterFilters></parameterFilters>
       <wmsLayer>
     <layers>
   </gwcConfiguration>
