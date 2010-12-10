.. _configuration.parameterfilter:

Parameter Filters
=================

Parameter filters provide templates for extracting arbitrary parameters from a requests. This makes it possible to use GeoWebCache in scenarios such as time series data, multiple styles for the same layer or with CQL filters set by the client.

There are three types of parameter filters:

#. **String filters** (``<stringParameterFilter>``)
#. **Numerical filters** (``<floatParameterFilter>``)
#. **Regular expression filters**  (``<regexParameterFilter>``)

It is possible to have multiple types of parameter filters for a given layer.

If a request does not comply with the allowed values of the set parameter, the request will fail, usually with an error such as::

   400: <value> violates filter for parameter <key>

.. note:: Parameter filters are not compatible with the demos on the demo page.  You will need to test standard WMS requests in order to utilize parameter filters.


String filter
-------------

GeoWebCache can also use an allowable list of string values in a parameter filter for a given key.  If the string in the request matches one of the string specified in the parameter filter, the request will proceed.

When specifying a string filter, three pieces of information are required:

* **Key** (``<key>``).  The key is not case sensitive.
* **Default value** (``<defaultValue>``).
* **List of strings** (``<values>``, ``<string>``).  The strings are case sensitive.

This information is presented in the following schema inside the ``<wmsLayer>`` tag:

.. code-block:: xml

   <parameterFilters>
     <stringParameterFilter>
       <key> ...  </key>
       <defaultValue> ... </defaultValue>
       <string>
         <value> ... </value>
         <value> ... </value>
          ... 
       </string>
     </stringParameterFilter>
   </parameterFilters>

For example, it is possible to set the allowed values of the "styles" parameter to one of three values:  "polygon", "population", with a third default blank value for when the parameter is unspecified.

The resulting parameter filter would be:

.. code-block:: xml

   <parameterFilters>
     <stringParameterFilter>
       <key>styles</key>
       <defaultValue></defaultValue>
       <values>
         <string></string>
         <string>population</string>
         <string>polygon</string>
       </values>
     </stringParameterFilter>
   </parameterFilters>

Numerical filter
----------------

Similar to a string filter, GeoWebCache can also recognize a list of numerical values for a given key.  If the value requested matches one of the values specified in the filter, the request will proceed.

When specifying a numerical filter, four pieces of information are required:

* **Key** (``<key>``).  This key is not case sensitive.
* **Default value** (``<defaultValue>``).
* **List of values** (``<values>``, ``<float>``). 
* **Threshold** (``<threshold>``).

This information is presented in the following schema inside the ``<wmsLayer>`` tag:

.. code-block:: xml

   <parameterFilters>
     <floatParameterFilter>
       <key> ... </key>
       <defaultValue> ... </defaultValue>
       <values>
         <float> ... </float>
         <float> ... </float>
         ...
       </values>
       <threshold> ... </threshold>
     </floatParameterFilter>
   </parameterFilters>

For example, given a parameter called "year" (assuming this was recognized by the WMS), where the allowed values are "1999" and "2006" and the default value being "2006", the filter would be:

.. code-block:: xml

   <parameterFilters>
     <floatParameterFilter>
       <key>year</key>
       <defaultValue>2006</defaultValue>
       <values>
         <float>1999</float>
         <float>2006</float>
       </values>
       <threshold>1</threshold>
     </floatParameterFilter>
   </parameterFilters>

Note also the above example sets a threshold of 1.  A value that is within the threshold of any of the allowed values will still proceed, albeit rounded to one of the allowed values\.  So in this example, a value of "1997" would be successfully requested as "1996", but a value of "2002" will fail.


Regular expression filter
-------------------------

For more fine control of parameter values, GeoWebCache can recognize regular expressions for the value in a  filter.  If a requested value matches the pattern in the regular expression, the request will proceed.

When specifying a regular expression filter, three pieces of information are required:

* **Key** (``<key>``).  The key is not case sensitive.
* **Default value** (``<defaultValue>``).
* **Regular expression** (``<regex>``).

This information is presented in the following schema inside the ``<wmsLayer>`` tag:

.. code-block:: xml

   <parameterFilters>
     <regexParameterFilter>
       <key> ... </key>
       <defaultValue> ... </defaultValue>
       <regex> ... </regex>
     </regexParameterFilter>
   </parameterFilters>

Using regular expressions, it is possible to specify the same allowed styles as in the above string filter example.  To set two allowed values for the "styles" parameter: "polygon", "population", with a third default blank value for when the parameter is unspecified, the regular expression would be::

  ^(|polygon|population)$  

The resulting parameter filter would be:

.. code-block:: xml

   <parameterFilters>
     <regexParameterFilter>
       <key>styles</key>
       <defaultValue></defaultValue>
       <regex>^(|polygon|population)$</regex>
     </regexParameterFilter>
   </parameterFilters>

