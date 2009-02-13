                                Per Layer Security
                                ------------------


Introduction
------------
As of version 1.7.0-rc1 Geoserver has a feature called Per layer security. This
feature provides granular control over who is able to access a specific layer.
See http://geoserver.org/display/GEOS/GSIP+19+-+Per+layer+security for further 
reading. The same feature is now implemented in GeoWebCache in a simplified variant.


Features
--------
When using AuthWMSService instead of WMSService you get an access control for 
each layer in a GetCapabilities request and for each GetMap request. A user will 
be presented with a filtred list of layers in the Capabilities document. To be 
able to serve this filtred list of layers the Capabilities document has to be 
rendered for each request and not buffered as before (default behavior for 
WMSService.handleGetCapabilitiesDocument()). The default behavior of proxying the 
Capabilites document from one of the back end WMS:s is also overridden and 
replaced with the generated information (thus exposing geowebcache as a single 
front for two or more back end WMS:s). Layer rules are specified in a properties
file, as is user information but user information can also be fetched from an ldap.


Technical overview
------------------
The application is configured to use org.geowebcache.security.wms.AuthWMSService
in the bean called gwcServiceWMS in the application spring context. 
AuthWMSService requires a dataAccessManager which must implement the 
org.geowebcache.security.wms.DataAccessManage interface. Request for 
GetCapabilities or GetMap are now handled by AuthWMSService (in the methods 
handleRequest() and getTile() respectively). A request to GetCapability starts 
of the generation of the Capability document (handled by AuthWMSRequests).
A request to GetMap does all WMSService handling of the request by calling 
super.getTile() with the extra check if the current user is allowed to see 
requested layer. 


Authentication management
-------------------------
A direct requirement of introducing the per layer security feature is that each 
request has to be sent through the Acegi Security filter chain. This is to be 
able to determine the principal of a request - even if it the principal is 
anonymous. To accomplish this the following changes must be made:
web.xml
 - all urls are sent through the Acegi filter chain: 
   url-pattern "/rest/*" becomes "/*"
 - all servlet names that was "geowebcace" becomes "geowebcache-secure" (in order
   to use geowebcache-secure-servlet.xml which has an example spring configuration
   that utilizes AuthWMSService, DataAccessManagement, etc.)
 - import acegi-secure-config.xml instead of acegi-config.xml


Layer rules
-----------
The information regarding who is allowed to see what is stored in 
src/main/webapp/WEB-INF/layers.properties (see that file for instructions on how 
to create layer rules). layers.properties may also be configured to a 
properties file of choice as a property to the bean gwcDataAccessManager in
src/main/webapp/WEB-INF/geowebcache-secure-servlet.xml


Getting user roles
------------------
The default configuration comes with two stores for getting user information.
First is the simple property file users.properties where you specify one user
per row in the following format:
username=password,[role]*
Second there is an example configuration to set up a connection to an ldap.
This configuration is included in acegi-config.xml and is to be modified to match
your ldap server configuration.  