The code is currently in a state of flux. Please come back soon :)

To build, provided you have Sun Java 1.5 =< , Maven 2.0.x
mvn clean install 

To run:
mvn clean install jetty:run

To package WAR file:
1) Edit pom.xml, enable the war plugin (remove comment markup)
2) mvn clean install
3) Retrieve geowebcache.war from target directory.

