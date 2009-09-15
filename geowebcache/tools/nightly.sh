#!/bin/bash
echo "This script is very purpose-specific, please edit the source to enable it for your particular environment"
exit 0

DATE=`date +%Y%m%d`
NICEDATE=`date +%Y-%m-%d`
rm -Rf /home/ak/nightly/geowebcache-*
svn export http://geowebcache.org/svn/trunk/geowebcache /home/ak/nightly/geowebcache-$DATE
zip -r geowebcache-$DATE-SRC.zip geowebcache-$DATE
cd /home/ak/nightly/geowebcache-$DATE
echo "Enabling WAR build"
sed -i s/'WAR -->'/'>'/g pom.xml
sed -i s/'<!-- WAR'/'<'/g pom.xml
echo "Replacing GWC_VERSION"
sed -i s/{GWC_VERSION}/NIGHTLY/g src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Replacing GWC_BUILD_DATE"
sed -i s/{GWC_BUILD_DATE}/${NICEDATE}/g src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Done"
mvn clean install -Dmaven.test.skip=true
cp LICENSE.txt target
cd target
zip /home/ak/nightly/geowebcache-$DATE-WAR.zip LICENSE.txt geowebcache.war
cd /home/ak/nightly
cp *.zip /var/www/html/geowebcache
echo cleaning up
find /var/www/html/geowebcache -type f -name 'geowebcache-*.zip' -mtime +5 -exec rm {} \;
