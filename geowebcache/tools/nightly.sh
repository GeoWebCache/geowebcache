#!/bin/bash
echo "This script is very purpose-specific, please edit the source to enable it for your particular environment"
#exit 0

TARGET_DIR=$HOME/nightly

if [ -d $TARGET_DIR ]; then
	echo "build directory is $TARGET_DIR"
else 
	echo "creating target directory $TARGET_DIR"
        mkdir -p $TARGET_DIR
fi 

cd $TARGET_DIR

DATE=`date +%Y%m%d`
NICEDATE=`date +%Y-%m-%d`
rm -Rf $TARGET_DIR/geowebcache-*
svn export http://geowebcache.org/svn/trunk/geowebcache $TARGET_DIR/geowebcache-$DATE
zip -r $TARGET_DIR/geowebcache-$DATE-SRC.zip geowebcache-$DATE
cd $TARGET_DIR/geowebcache-$DATE
#echo "Enabling WAR build"
#sed -i s/'WAR -->'/'>'/g pom.xml
#sed -i s/'<!-- WAR'/'<'/g pom.xml
echo "Replacing GWC_VERSION"
sed -i s/{GWC_VERSION}/NIGHTLY/g core/src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Replacing GWC_BUILD_DATE"
sed -i s/{GWC_BUILD_DATE}/${NICEDATE}/g core/src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Done"
mvn clean install -DskipTests
cp LICENSE.txt web/target
cd web/target
zip $TARGET_DIR/geowebcache-$DATE-WAR.zip LICENSE.txt geowebcache.war
cd $TARGET_DIR
cp *.zip /var/www/html/geowebcache
echo cleaning up
find /var/www/html/geowebcache -type f -name 'geowebcache-*.zip' -mtime +5 -exec rm {} \;
