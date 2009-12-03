#!/bin/bash
exit 0

VERSION=$1

BUILD_ROOT=/tmp/release
XSDOC_BIN=/home/ak/xsddoc-1.0/bin/xsddoc.unix
DATE=`date +%Y%m%d`
NICEDATE=`date +%Y-%m-%d`
rm -Rf $BUILD_ROOT
mkdir $BUILD_ROOT
cd $BUILD_ROOT
#TODO Copy to tag and check out from there
svn co http://geowebcache.org/svn/trunk
cd trunk/geowebcache
echo "Replacing GWC_VERSION"
sed -i s/{GWC_VERSION}/NIGHTLY/g src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Replacing GWC_BUILD_DATE"
sed -i s/{GWC_BUILD_DATE}/${NICEDATE}/g src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Replacing version in POM"
sed -i s/1\.2-SNAPSHOT/${VERSION}/g pom.xml
echo "Done"

# Saving a copy of the source
cd ..
svn export geowebcache geowebcache-$VERSION
zip -r ../geowebcache-$VERSION-SRC.zip geowebcache-$VERSION
cd geowebcache-$VERSION
echo "Building and deploying JAR"
mvn clean install 
#deploy

echo "Enabling WAR build"
sed -i s/'WAR -->'/'>'/g pom.xml
sed -i s/'<!-- WAR'/'<'/g pom.xml

echo "Building WAR"
mvn clean install -Dmaven.test.skip=true
cp LICENSE.txt target
cd target
zip ../../../geowebcache-${VERSION}-WAR.zip LICENSE.txt geowebcache.war
cd ../..
cd documentation/en/user
echo "Building documentation"
make clean html
cd build
mv html geowebcache-${VERSION}-docs
echo "Packing documentation"
zip -r ../../../../../geowebcache-${VERSION}-DOC.zip geowebcache-$VERSION-docs

echo "Building XSD documentation"
mkdir /tmp/release/$VERSION
cd /tmp/release

eval $XSDOC_BIN -o /tmp/release/$VERSION -t "GeoWebCache 1.2.1 Configuration Schema" /home/ak/gwc/trunk/src/main/resources/org/geowebcache/config/geowebcache.xsd
cd  /tmp/release/
zip -r $VERSION.zip $VERSION
