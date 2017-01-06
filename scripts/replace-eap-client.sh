#This script is useful to replace artemis jars inside EAP 7/Wildfly 10
echo "Scripts takes 2 arguments: <artemis_project_home_dir> <eap7_home>"

export ARTEMIS_HOME=$1
export JBOSS_HOME=$2

export VERSION=`grep -ir -A 2 "artemis-pom" $1/pom.xml | grep version | sed s,.*\<version\>,,g |sed s,\<\/version\>,,g `
echo "VERSION = $VERSION"

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

