<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:gwc="http://geowebcache.org/schema/1.1.4" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" version="1.0" encoding="utf-8" indent="yes"/>

<xsl:template match="node()|*">
  <xsl:copy>
    <xsl:apply-templates/>
  </xsl:copy>
</xsl:template>

<xsl:template match="/|comment()|processing-instruction()">
    <xsl:copy>
      <xsl:apply-templates/>
    </xsl:copy>
</xsl:template>

<xsl:template match="*">
    <xsl:element name="{local-name()}">
      <xsl:apply-templates select="@*|node()"/>
    </xsl:element>
</xsl:template>

<xsl:template match="@*">
    <xsl:attribute name="{local-name()}">
      <xsl:value-of select="."/>
    </xsl:attribute>
</xsl:template>

<xsl:template match="gwc:gwcConfiguration">
  <gwcConfiguration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://geowebcache.org/schema/1.1.5"
    xsi:schemaLocation="http://geowebcache.org/schema/1.1.5 http://geowebcache.org/schema/1.1.5/geowebcache.xsd">
    <xsl:apply-templates/>
  </gwcConfiguration>
</xsl:template>

</xsl:stylesheet>
