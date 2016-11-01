#This script is useful to replace artemis jars inside EAP 7/Wildfly 10
echo "Scripts takes 2 arguments: <artemis_project_home_dir> <eap7_home>"

export ARTEMIS_HOME=$1
export JBOSS_HOME=$2

export VERSION=`grep -ir -A 2 "artemis-pom" $1/pom.xml | grep version | sed s,.*\<version\>,,g |sed s,\<\/version\>,,g `
echo "VERSION = $VERSION"

function findJar() {
  local MODULES_DIR=$JBOSS_HOME/modules/system/layers/base
  local OVERLAYS_DIR=$MODULES_DIR/.overlays

  if [ -f $OVERLAYS_DIR/.overlays ]; then
    while read PATCH; do
      local RESULT=$(find $OVERLAYS_DIR/$PATCH -name $1)
      if [ ${RESULT}0 != 0 ]; then
        break
      fi
    done < $OVERLAYS_DIR/.overlays
  fi
  if [ ${RESULT}0 == 0 ]; then
    local RESULT=$(find $MODULES_DIR -name $1)
  fi
  echo $RESULT
}

#replace jars
cp $ARTEMIS_HOME/artemis-cli/target/artemis-cli-$VERSION.jar $(findJar artemis-cli-*.jar)
cp $ARTEMIS_HOME/artemis-protocols/artemis-hqclient-protocol/target/artemis-hqclient-protocol-$VERSION.jar $(findJar artemis-hqclient-protocol-*.jar)
cp $ARTEMIS_HOME/artemis-native/target/artemis-native-$VERSION.jar $(findJar artemis-native-*.jar)
cp $ARTEMIS_HOME/artemis-commons/target/artemis-commons-$VERSION.jar $(findJar artemis-commons-*.jar)
cp $ARTEMIS_HOME/artemis-jms-client/target/artemis-jms-client-$VERSION.jar $(findJar artemis-jms-client-*.jar)
cp $ARTEMIS_HOME/artemis-selector/target/artemis-selector-$VERSION.jar $(findJar artemis-selector-*.jar)
cp $ARTEMIS_HOME/artemis-core-client/target/artemis-core-client-$VERSION.jar $(findJar artemis-core-client-*.jar)
cp $ARTEMIS_HOME/artemis-jms-server/target/artemis-jms-server-$VERSION.jar $(findJar artemis-jms-server-*.jar)
cp $ARTEMIS_HOME/artemis-server/target/artemis-server-$VERSION.jar $(findJar artemis-server-*.jar)
cp $ARTEMIS_HOME/artemis-dto/target/artemis-dto-$VERSION.jar $(findJar artemis-dto-*.jar)
cp $ARTEMIS_HOME/artemis-journal/target/artemis-journal-$VERSION.jar $(findJar artemis-journal-*.jar)

cp $ARTEMIS_HOME/artemis-ra/target/artemis-ra-$VERSION.jar $(findJar artemis-ra-*.jar)
cp $ARTEMIS_HOME/artemis-service-extensions/target/artemis-service-extensions-$VERSION.jar $(findJar artemis-service-extensions-*.jar)

cp $ARTEMIS_HOME/artemis-protocols/artemis-hornetq-protocol/target/artemis-hornetq-protocol-$VERSION.jar $(findJar artemis-hornetq-protocol-*.jar)

#cp $ARTEMIS_HOME/artemis-native/bin/libartemis-native-64.so $(findJar libartemis-native-64.so)
#cp $ARTEMIS_HOME/artemis-native/bin/libartemis-native-32.so $(findJar libartemis-native-32.so)



#update jboss-client.jar
rm -rf tmp
mkdir ./tmp
cd ./tmp
unzip -qq $JBOSS_HOME/bin/client/jboss-client.jar
rm -rf ./org/apache/activemq/artemis
unzip -qq -o $ARTEMIS_HOME/artemis-commons/target/artemis-commons-$VERSION.jar -x \*META-INF\*
unzip -qq -o $ARTEMIS_HOME/artemis-core-client/target/artemis-core-client-$VERSION.jar -x \*META-INF\*
unzip -qq -o $ARTEMIS_HOME/artemis-protocols/artemis-hqclient-protocol/target/artemis-hqclient-protocol-$VERSION.jar -x \*META-INF\*
unzip -qq -o $ARTEMIS_HOME/artemis-jms-client/target/artemis-jms-client-$VERSION.jar -x \*META-INF\*
unzip -qq -o $ARTEMIS_HOME/artemis-selector/target/artemis-selector-$VERSION.jar -x \*META-INF\*
unzip -qq -o $ARTEMIS_HOME/artemis-journal/target/artemis-journal-$VERSION.jar -x \*META-INF\*
unzip -qq -o $ARTEMIS_HOME/artemis-native/target/artemis-native-$VERSION.jar  -x \*META-INF\*

zip -qq -r jboss-client.jar *
cp jboss-client.jar $JBOSS_HOME/bin/client/jboss-client.jar
cd ..
rm -rf tmp

md5sum $ARTEMIS_HOME/artemis-cli/target/artemis-cli-$VERSION.jar $(findJar artemis-cli-*.jar)
md5sum $ARTEMIS_HOME/artemis-protocols/artemis-hqclient-protocol/target/artemis-hqclient-protocol-$VERSION.jar $(findJar artemis-hqclient-protocol-*.jar)
md5sum $ARTEMIS_HOME/artemis-native/target/artemis-native-$VERSION.jar $(findJar artemis-native-*.jar)
md5sum $ARTEMIS_HOME/artemis-commons/target/artemis-commons-$VERSION.jar $(findJar artemis-commons-*.jar)
md5sum $ARTEMIS_HOME/artemis-jms-client/target/artemis-jms-client-$VERSION.jar $(findJar artemis-jms-client-*.jar)
md5sum $ARTEMIS_HOME/artemis-selector/target/artemis-selector-$VERSION.jar $(findJar artemis-selector-*.jar)
md5sum $ARTEMIS_HOME/artemis-core-client/target/artemis-core-client-$VERSION.jar $(findJar artemis-core-client-*.jar)
md5sum $ARTEMIS_HOME/artemis-jms-server/target/artemis-jms-server-$VERSION.jar $(findJar artemis-jms-server-*.jar)
md5sum $ARTEMIS_HOME/artemis-server/target/artemis-server-$VERSION.jar $(findJar artemis-server-*.jar)
md5sum $ARTEMIS_HOME/artemis-dto/target/artemis-dto-$VERSION.jar $(findJar artemis-dto-*.jar)
md5sum $ARTEMIS_HOME/artemis-journal/target/artemis-journal-$VERSION.jar $(findJar artemis-journal-*.jar)

md5sum $ARTEMIS_HOME/artemis-ra/target/artemis-ra-$VERSION.jar $(findJar artemis-ra-*.jar)
md5sum $ARTEMIS_HOME/artemis-service-extensions/target/artemis-service-extensions-$VERSION.jar $(findJar artemis-service-extensions-*.jar)

md5sum $ARTEMIS_HOME/artemis-protocols/artemis-hornetq-protocol/target/artemis-hornetq-protocol-$VERSION.jar $(findJar artemis-hornetq-protocol-*.jar)
md5sum $ARTEMIS_HOME/artemis-native/bin/libartemis-native-64.so $(findJar libartemis-native-64.so)
md5sum $ARTEMIS_HOME/artemis-native/bin/libartemis-native-32.so $(findJar libartemis-native-32.so)
