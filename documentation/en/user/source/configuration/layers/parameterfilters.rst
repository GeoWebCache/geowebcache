.. _configuration.layers.parameterfilters:

Parameter Filters
=================

Parameter filters provide templates for extracting arbitrary parameters from requests. This allows using GeoWebCache in scenarios such as time series data, multiple styles for the same layer or with CQL filters set by the client.

There are four types of parameter filters:

#. **String filters** (``<stringParameterFilter>``)
#. **Floating point number filters** (``<floatParameterFilter>``)
#. **Integer filters** (``<integerParameterFilter>``)
#. **Regular expression filters**  (``<regexParameterFilter>``)

A given layer can have multiple types of parameter filters.

If a request does not comply with the allowed values of the set parameter, the request will fail, usually with an error such as::

   400: <value> violates filter for parameter <key>

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
       <values>
         <string> ... </string>
         <string> ... </string>
          ... 
       </values>
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

Floating point filter
---------------------

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

For example, given a parameter called ``elevation``, where the allowed values are ``-42.5``, ``0``, and ``100`` and the default value being ``100``, the filter would be:

.. code-block:: xml

   <parameterFilters>
     <floatParameterFilter>
       <key>elevation</key>
       <defaultValue>-42.5</defaultValue>
       <values>
         <float>42.5</float>
         <float>0</float>
         <float>100</float>
       </values>
       <threshold>50</threshold>
     </floatParameterFilter>
   </parameterFilters>

Note also the above example sets a threshold of ``50``.  A value that is within the threshold of any of the allowed values will still proceed, albeit rounded to one of the allowed values.  So in this example, a value of ``75`` would be successfully requested as ``100.0``, but a value of ``200`` will fail.

Thresholds are also valuable when managing possible floating point rounding errors.  For example, if your data has accuracy down to the sixth decimal place, you may want to use a threshold of ``1e-6`` to ensure proper matching.

Note that the request value produced by the filter will *always* include a decimal point and floating point arithmetic has limitted precision that can means certain values can't be correctly represented.  If you are working with exclusively integer ("whole number") values, it's better to use the ``integerParameterFilter`` instead.

Integer filter
--------------

This works in much the same way as the floating point filter, but only allows whole numbers, including negatives.

Again, four pieces of information are required:

* **Key** (``<key>``).  This key is not case sensitive.
* **Default value** (``<defaultValue>``).
* **List of values** (``<values>``, ``<int>``). 
* **Threshold** (``<threshold>``).

This information is presented in the following schema inside the ``<wmsLayer>`` tag:

.. code-block:: xml

   <parameterFilters>
     <integerParameterFilter>
       <key> ... </key>
       <defaultValue> ... </defaultValue>
       <values>
         <int> ... </int>
         <int> ... </int>
         ...
       </values>
       <threshold> ... </threshold>
     </integerParameterFilter>
   </parameterFilters>

If the paramter were ``dim_year``, where the allowed values are ``1996``, and ``2006`` and the default value being ``2006``, the filter would be:

.. code-block:: xml

   <parameterFilters>
     <integerParameterFilter>
       <key>dim_year</key>
       <defaultValue>2006</defaultValue>
       <values>
         <float>1996</float>
         <float>2006</float>
       </values>
       <threshold>2</threshold>
     </floatParameterFilter>
   </parameterFilters>

Note also the above example sets a threshold of ``2`` to only cover the specific value listed and one year either side.  So in this example, a value of ``2007`` would be successfully requested as ``2006``, but a value of ``2008`` will fail.

Note that unlike the ``floatParameterFilter``, there is no decimal point in the requested value.


Regular expression filter
-------------------------

For a finer control of parameter values, GeoWebCache can recognize regular expressions for the value in a filter.  If a requested value matches the pattern in the regular expression, the request will proceed.

.. note:: GeoWebCache uses standard Java regular expressions.  For more information, please see the regular expression pattern documentation at:  `<http://download.oracle.com/javase/1.5.0/docs/api/java/util/regex/Pattern.html>`_.

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

Regular expressions allows you to specify the same allowed styles as in the above string filter example.  To set two allowed values for the "styles" parameter: "polygon", "population", with a third default blank value for when the parameter is unspecified, the regular expression would be::

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

Case normalization
------------------

You can normalize the case of a Regular Expression or String rule to upper or lower case by adding a ``normalize`` element.  This takes a ``case`` and an optional ``locale``.  ``case`` may be ``NONE`` for no normalization, ``UPPER`` for upper case, or ``LOWER`` for lower case.  ``locale`` may be any Java locale identifier supported by the JVM and the JVM default locale will be used if not specified.

.. code-block:: xml

   <parameterFilters>
     <regexParameterFilter>
       <key>styles</key>
       <defaultValue></defaultValue>
       <normalize>
         <case>UPPER</case>
	 <locale>en_CA</locale> <!-- Canadian English locale-->
       <regex>^(|polygon|population)$</regex>
     </regexParameterFilter>
   </parameterFilters>

If upper or lower case normalization is used, matching with legal values will be case insensitive, otherwise it will be case sensitive.  The default value is never normalized.

