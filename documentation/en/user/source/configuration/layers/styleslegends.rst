.. _configuration.layers.styleslegends:

Styles Legends
==============

In WMTS and WMS services capabilities documents, styles can be associated with a ``<LegendURL>`` element that allows clients to retrieve an image representing the style legend. In the following example we can see the ``<LegendURL>`` element for ``rain`` style (WMS 1.1.0): 

.. code-block:: xml

  <Style>
    <Name>rain</Name>
    <LegendURL width="128" height="123">
      <Format>image/png</Format>
      <OnlineResource xlink:type="simple" xlink:href="http://localhost:8080/geoserver/ows?service=WMS&request=GetLegendGraphic&format=image/png&width=128&height=123&layer=topp:states&style=rain"/>
    </LegendURL>
  </Style>

When using GeoWebCache integration with GeoServer, styles legends are automatically configured for GeoServer layers. Layers configured using a WMS capabilities document will also have their styles legends automatically configured.

When configuring a layer using the REST interface or through ``geowebcache.xml`` configuration file the user needs to configure the styles legends, otherwise no ``<LegendURL>`` elements will be included in WMS and WMTS services capabilities documents.

Both REST interface and ``geowebcache.xml`` configuration file use the same XML structure. A legend configuration is associated with a certain style in the context of a certain layer, follows a configuration example:

.. code-block:: xml

   <wmsLayer>
      <name>topp:states</name>
      <parameterFilters>
        <stringParameterFilter>
          <key>STYLES</key>
          <defaultValue>population</defaultValue>
          <values>
            <string>population</string>
            <string>polygon</string>
            <string>pophatch</string>
          </values>
        </stringParameterFilter>
      </parameterFilters>
      <wmsUrl>
        <string>http://localhost:8080/geoserver/topp/wms</string>
      </wmsUrl>
      <legends defaultWidth="20" defaultHeight="20" defaultFormat="image/png">
        <legend style="population"/>
        <legend style="pophatch">
          <format>image/jpeg</format>
          <url>http://localhost:8020/geowebcache/wms</url>
        </legend>
        <legend style="polygon">
          <width>50</width>
          <height>100</height>
          <format>image/gif</format>
          <completeUrl>http://localhost/polygon.gif</completeUrl>
          <minScale>5000</minScale>
          <maxScale>10000</maxScale>
        </legend>
      </legends>
    </wmsLayer>

The following properties can be used to configure a legend info:

.. list-table::
   :widths: 10 90
   :header-rows: 1

   * - Property
     - Description
   * - style
     - the style to which this legend belongs 
   * - width
     - the width of the legend image 
   * - height
     - the height of the legend image
   * - format
     - the image format of the legend image
   * - url
     - the URL that can be used to retrieve the legend image
   * - minScale
     - minimum scale denominator (inclusive) for which this legend image is valid (WMTS only)
   * - maxScale
     - maximum scale denominator (exclusive) for which this legend image is valid (WMTS only)

This properties can be provided in several ways using ``<legends>`` and ``<legend>`` elements. Default values for properties ``width``, ``height`` and ``format`` can be configured using respectively attributes ``defaultWidth``, ``defaultHeight`` and ``defaultFormat`` of ``<legends>`` element. The default values can be overridden using elements ``width``, ``height`` and ``format`` inside ``<legend>`` elements.

The ``style`` property value needs to be provided using ``style`` attribute of ``<legend>`` elements. The ``url`` property is a little more complex, if nothing is say the WMS layer base URL will be used to build a WMS ``GetLegendGraphic`` request. Element ``<url>`` can be used to provide another base URL, and this one will be used instead of the layer base URL. Element ``<completeUrl>`` can be used to provide an URL that should be used as is to retrieve the legend image.

Properties ``minScale`` and ``maxScale`` can be provided using the ``<legend>`` element, this properties will only be used by WMTS service.

Looking at the example above, the legend configured for ``population`` style will produce the following legend URL for WMTS:

.. code-block:: xml

  <LegendURL width="20" height="20" format="image/png" 
    xlink:href="http://localhost:8080/geoserver/topp/wms?service=WMS&amp;request=GetLegendGraphic&amp;format=image/png&amp;width=20&amp;height=20&amp;layer=topp%3Astates&amp;style=population"/>

Note that the layer base URL was used as the base URL for the legend URL. In the example above a different base URL is provided for the legend associated with style ``pophatch`` using the ``<url>`` element. The produced legend URL will look like this for WMTS:

.. code-block:: xml

  <LegendURL width="20" height="20" format="image/png"
    xlink:href="http://localhost:8020/geowebcache/wms?service=WMS&amp;request=GetLegendGraphic&amp;format=image/jpeg&amp;width=20&amp;height=20&amp;layer=topp%3Astates&amp;style=pophatch"/>

In some situations it may be useful to provide an already complete URL to the legend image (custom vendors parameters, a static image or different protocol). In the example above the legend URL for style ``polygon`` will use an already complete URL and will look like this for WMTS:

.. code-block:: xml

  <LegendURL width="50" height="100" minScaleDenominator="5000" maxScaleDenominator="10000" 
    format="image/gif" xlink:href="http://localhost/polygon.gif"/>

In WMS the legend URL element for ``polygon`` style will look like this:

.. code-block:: xml

  <LegendURL width="50" height="100">
      <Format>image/gif</Format>
      <OnlineResource xlink:type="simple" xlink:href="http://localhost/polygon.gif"/>
  </LegendURL>

WMS and WMTS legend URL elements have a different structure and different mandatory elements. In WMTS only properties ``format`` and ``url`` are mandatory. In WMS properties ``width``, ``height``, ``format`` and ``url`` are mandatory.  