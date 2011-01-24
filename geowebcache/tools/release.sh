#!/bin/bash
#NOTE this script is deprecated by the release process described in ../release/RELEASE_GUIDE.txt
VERSION=$1

exit 0

if [ $# -eq 0 ] 
then
  exit 0
else

BUILD_ROOT=/tmp/release
DATE=`date +%Y%m%d`
NICEDATE=`date +%Y-%m-%d`

rm -Rf $BUILD_ROOT
mkdir $BUILD_ROOT
cd $BUILD_ROOT
svn copy http://geowebcache.org/svn/trunk http://geowebcache.org/svn/tags/$VERSION -m "Tagging $VERSION"
svn co http://geowebcache.org/svn/tags/$VERSION

# For testing
#svn co http://geowebcache.org/svn/trunk $VERSION
cd $VERSION/geowebcache
echo "Replacing GWC_VERSION"
sed -i s/{GWC_VERSION}/NIGHTLY/g src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Replacing GWC_BUILD_DATE"
sed -i s/{GWC_BUILD_DATE}/${NICEDATE}/g src/main/java/org/geowebcache/GeoWebCacheDispatcher.java
echo "Replacing version in POM"
sed -i s/1\.2-SNAPSHOT/${VERSION}/g pom.xml
echo "Done"

echo "Press a key"
read tmp

# Saving a copy of the source
echo "Packaging the source code"
cd ..
svn export geowebcache geowebcache-$VERSION
zip -r ../geowebcache-$VERSION-SRC.zip geowebcache-$VERSION

cd geowebcache-$VERSION
echo "Building and deploying JAR"
mvn clean install deploy

echo "Enabling WAR build"
sed -i s/'WAR -->'/'>'/g pom.xml
sed -i s/'<!-- WAR'/'<'/g pom.xml

echo "Press a key"
read tmp

echo "Building WAR"
mvn clean install -Dmaven.test.skip=true
cp LICENSE.txt target
cd target

echo "Press a key"
read tmp

zip ../../../geowebcache-${VERSION}-WAR.zip LICENSE.txt geowebcache.war
cd ../..
cd documentation/en/user
echo "Building documentation"
make clean html
cd build
mv html geowebcache-${VERSION}-docs

echo "Press a key"
read tmp

echo "Packing documentation"
zip -r /tmp/release/geowebcache-${VERSION}-DOC.zip geowebcache-$VERSION-docs

echo "Building XSD documentation"
cd /tmp/release
mkdir schema
mkdir schema/$VERSION

/home/ak/xsddoc-1.0/bin/xsddoc -o /tmp/release/schema/$VERSION -t "GeoWebCache $VERSION Configuration Schema" /tmp/release/$VERSION/geowebcache/src/main/resources/org/geowebcache/config/geowebcache.xsd

cd  /tmp/release/schema
zip -r ../$VERSION.zip $VERSION

fi
