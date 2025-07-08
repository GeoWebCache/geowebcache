Frequently Asked Questions
==========================

This section will answer common questions about GeoWebCache.

Does GeoWebCache support WFS feature caching?

  Not currently.  Earlier versions of GeoWebCache had an experimental prototype of vector feature caching, but it was highly unstable and was removed from GeoWebCache as of version 1.2.5.
  
  However when used with a vector tiles output format GeoWebCache can cache a vector representation of the features within each tile. It is up to client software to stich together the resulting shapes during vector tile rendering.
