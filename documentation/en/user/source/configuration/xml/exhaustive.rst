.. _exhaustive:

Simple Configuration Examples
=============================

The example below uses all configuration directives that are currently available in GeoWebCache. Some of them a mutually exclusive, please check the XSD documentation. Additionally, certain global settings are controlled through the Spring context in geowebcache-servlet.xml.

.. code-block:: xml
   <?xml version="1.0" encoding="utf-8"?>
   <gwcConfiguration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:noNamespaceSchemaLocation="http://geowebcache.org/schema/1.2.0/geowebcache.xsd"
                     xmlns="http://geowebcache.org/schema/1.2.0">
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
     <!-- OPTIONAL Grid Sets. If you do not define any here, the only ones available will be
          worldwide EPSG:4326 and EPSG:900913, in addition to any that are automatically
          generated for the GetCapabilities configuration -->
     <gridSets>
       <!-- You can have one or more of these elements -->
       <gridSet>
         <!-- REQUIRED The name should imply something about use, extent or SRS -->
         <name>UK National</name>
         <!-- REQUIRED The SRS used for WMS requests. This is all the fallback lookup
              method for services that do not specify the grid set. Only the number
               -->
         <srs><number>4326</number</srs>
       </gridSet>
     </gridSets>
