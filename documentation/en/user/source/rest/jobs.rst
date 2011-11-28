.. _rest.jobs:

Jobs REST API
=============

The REST API for job management provides a RESTful interface through which clients can view and manage jobs.

Operations
----------

The following is a list of valid entry points for the Job REST API. Each entry point lists the possible HTTP responses for each method (GET, POST, PUT, DELETE). Though not listed, a 500 Server Error is also possible from any of the following if GeoWebCache experiences an unexpected error.

/jobs
^^^^^

``/jobs.<format>``

.. list-table::
   :widths: 10,55,23,12
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return all jobs in the system
     - 200 - OK
     - XML, JSON
   * - POST
     - Update an existing job. Updates of jobs should go to ``/jobs/<Job ID>.<format>``, but this also works as long as the posted job has a valid Job ID.
     - 200 - OK
     - XML, JSON
   * - PUT
     - Creates a new job. If the job had a job Id it is ignored and a new one is assigned when the job is created. New jobs are immediately executed or scheduled.
     - 200
     - XML, JSON
   * - DELETE
     - Will complain that a Job ID was not provided. Instead send a HTTP DELETE to ``/jobs/<Job ID>.<format>``
     - 400 - BAD REQUEST
     -

*Representations*:

- :download:`XML <representations/jobs_xml.txt>`
- :download:`JSON <representations/jobs_json.txt>`


*Example URLs*:

- http://localhost:8080/geowebcache/rest/jobs.xml
- http://localhost:8080/geowebcache/rest/jobs.json

/job/<Job ID>
^^^^^^^^^^^^^

``/jobs/<Job ID>.<format>``

.. list-table::
   :widths: 10,55,23,12
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return a job in the system.
     - 200 - OK
     - XML, JSON
   * - 
     - Invalid or an omitted Job ID will result in this response.
     - 400 - BAD REQUEST
     - 
   * - POST
     - Update an existing job. Only jobs that haven't been run yet can be updated, and only certain fields can be changed. A running job can only have its status changed to 'KILLED', which will stop the job.
     - 200 - OK
     - XML, JSON
   * - 
     - Jobs that have completed cannot be updated. Running jobs can only be stopped. A POST that would have any other effects generates this HTTP response. 
     - 400 - BAD REQUEST
     - 
   * - PUT
     - Creates a new job. New jobs should go to ``/jobs.<format>``, but this also works, and the Job ID will be ignored.
     - 200 - OK
     - XML, JSON
   * - DELETE
     - Delete a job and it's logs from the system.
     - 200 - OK
     -

*Representations*:

- :download:`XML <representations/job_xml.txt>`
- :download:`JSON <representations/job_json.txt>`

*Example URLs*:

- http://localhost:8080/geowebcache/rest/jobs/1.xml
- http://localhost:8080/geowebcache/rest/jobs/1.json

/job/<Job ID>/logs
^^^^^^^^^^^^^^^^^^

``/jobs/<Job ID>/logs.<format>``

.. list-table::
   :widths: 10,55,23,12
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return all logs for a job in the system.
     - 200 - OK
     - XML, JSON
   * - 
     - Invalid or an omitted Job ID will result in this response.
     - 400 - BAD REQUEST
     - 
   * - POST
     - 
     - 405 - METHOD NOT ALLOWED
     - 
   * - PUT
     - 
     - 405 - METHOD NOT ALLOWED
     - 
   * - DELETE
     - 
     - 405 - METHOD NOT ALLOWED
     - 

*Representations*:

- :download:`XML <representations/joblogs_xml.txt>`
- :download:`JSON <representations/joblogs_json.txt>`

*Example URLs*:

- http://localhost:8080/geowebcache/rest/jobs/1.xml
- http://localhost:8080/geowebcache/rest/jobs/1.json

/estimate
^^^^^^^^^

``/estimate.<format>``

.. list-table::
   :widths: 10,55,23,12
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - 
     - 405 - METHOD NOT ALLOWED
     - 
   * - POST
     - Send layer, format, zoom level, grid set, thread count, max throughput and bounds information and get an estimate of the number of tiles, time estimate to complete seeding and anticipated disk space required.
     - 200 - OK
     - XML, JSON
   * - 
     - Most likeley because an unsupported format was requested.
     - 400 - BAD REQUEST
     - 
   * - PUT
     - 
     - 405 - METHOD NOT ALLOWED
     - 
   * - DELETE
     - 
     - 405 - METHOD NOT ALLOWED
     - 

*Representations*:

- :download:`XML Request <representations/estimate_xml.txt>`
- :download:`XML Response <representations/estimate_xml_response.txt>`
- :download:`JSON Request <representations/estimate_json.txt>`
- :download:`JSON Response <representations/estimate_json_response.txt>`

*Example URLs*:

- http://localhost:8080/geowebcache/rest/estimate.xml
- http://localhost:8080/geowebcache/rest/estimate.json

/settings
^^^^^^^^^

``/settings.<format>``

.. list-table::
   :widths: 10,55,23,12
   :header-rows: 1

   * - Method
     - Action
     - Return Code
     - Formats
   * - GET
     - Return settings taht can be set through this interface.
     - 200 - OK
     - XML, JSON
   * - 
     - Most likeley because an unsupported format was requested.
     - 400 - BAD REQUEST
     - 
   * - POST
     - Update the settings.
     - 200 - OK
     - XML, JSON
   * - 
     - Most likeley because an unsupported format was requested.
     - 400 - BAD REQUEST
     - 
   * - PUT
     - 
     - 405 - METHOD NOT ALLOWED
     - 
   * - DELETE
     - 
     - 405 - METHOD NOT ALLOWED
     - 

*Representations*:

- :download:`XML <representations/settings_xml.txt>`
- :download:`JSON <representations/settings_json.txt>`

*Example URLs*:

- http://localhost:8080/geowebcache/rest/settings.xml
- http://localhost:8080/geowebcache/rest/settings.json
