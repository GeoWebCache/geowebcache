<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" version="1.0" encoding="utf-8" indent="yes"/>

<xsl:template match="node()|*">
  <xsl:copy>
    <xsl:apply-templates/>
  </xsl:copy>
</xsl:template>

<xsl:template match="wmslayer">
  <wmsLayer>
    <xsl:apply-templates/>
  </wmsLayer>
</xsl:template>

<xsl:template match="SRS">
  <srs>
    <xsl:apply-templates/>
  </srs>
</xsl:template>

<xsl:template match="errormime">
  <errorMime>
    <xsl:apply-templates/>
  </errorMime>
</xsl:template>

<xsl:template match="WMSUrl">
  <wmsUrl>
    <xsl:apply-templates/>
  </wmsUrl>
</xsl:template>

<xsl:template match="layers">
  <gwcConfiguration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:noNamespaceSchemaLocation="http://geowebcache.org/schema/1.0.0/geowebcache.xsd"
                  xmlns="http://geowebcache.org/schema/1.0.0">
  <version>1.0.0</version>
  <layers>
    <xsl:apply-templates/>
  </layers>
  </gwcConfiguration>
</xsl:template>

<xsl:template match="gwcConfiguration">
  <gwcConfiguration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://geowebcache.org/schema/1.0.2"
    xsi:schemaLocation="http://geowebcache.org/schema/1.0.2 http://geowebcache.org/schema/1.0.2/geowebcache.xsd">
  <version>1.0.0</version>
  <layers>
    <xsl:apply-templates/>
  </layers>
  </gwcConfiguration>
</xsl:template>


</xsl:stylesheet>
