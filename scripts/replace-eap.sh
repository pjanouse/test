#this script is useful to replace libraries on a wildfly master
# it assumes JBOSS_HOME pre defined
# run this from the project home
echo "Scripts takes 2 arguments: <artemis_project_home_dir> <eap7_home>"

export ARTEMIS_HOME=$1
export JBOSS_HOME=$2


# fis the VERSION before running it
export VERSION="1.1.0.jboss-SNAPSHOT"
#replace jars
cp $ARTEMIS_HOME/artemis-cli/target/artemis-cli-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-cli-*.jar
cp $ARTEMIS_HOME/artemis-protocols/artemis-hqclient-protocol/target/artemis-hqclient-protocol-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-hqclient-protocol-*.jar
cp $ARTEMIS_HOME/artemis-native/target/artemis-native-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-native-*.jar
cp $ARTEMIS_HOME/artemis-commons/target/artemis-commons-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-commons-*.jar
cp $ARTEMIS_HOME/artemis-jms-client/target/artemis-jms-client-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-jms-client-*.jar
cp $ARTEMIS_HOME/artemis-selector/target/artemis-selector-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-selector-*.jar
cp $ARTEMIS_HOME/artemis-core-client/target/artemis-core-client-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-core-client-*.jar
cp $ARTEMIS_HOME/artemis-jms-server/target/artemis-jms-server-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-jms-server-*.jar
cp $ARTEMIS_HOME/artemis-server/target/artemis-server-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-server*.jar
cp $ARTEMIS_HOME/artemis-dto/target/artemis-dto-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-dto-*.jar
cp $ARTEMIS_HOME/artemis-journal/target/artemis-journal-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-journal-*.jar

cp $ARTEMIS_HOME/artemis-ra/target/artemis-ra-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/ra/main/artemis-ra-*.jar
cp $ARTEMIS_HOME/artemis-service-extensions/target/artemis-service-extensions-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/ra/main/artemis-service-extensions-*.jar

cp $ARTEMIS_HOME/artemis-protocols/artemis-hornetq-protocol/target/artemis-hornetq-protocol-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/protocol/hornetq/main/artemis-hornetq-protocol*.jar

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
zip -qq -r jboss-client.jar *
cp jboss-client.jar $JBOSS_HOME/bin/client/jboss-client.jar
cd ..
rm -rf tmp

md5sum $ARTEMIS_HOME/artemis-cli/target/artemis-cli-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-cli-*.jar
md5sum $ARTEMIS_HOME/artemis-protocols/artemis-hqclient-protocol/target/artemis-hqclient-protocol-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-hqclient-protocol-*.jar
md5sum $ARTEMIS_HOME/artemis-native/target/artemis-native-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-native-*.jar
md5sum $ARTEMIS_HOME/artemis-commons/target/artemis-commons-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-commons-*.jar
md5sum $ARTEMIS_HOME/artemis-jms-client/target/artemis-jms-client-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-jms-client-*.jar
md5sum $ARTEMIS_HOME/artemis-selector/target/artemis-selector-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-selector-*.jar
md5sum $ARTEMIS_HOME/artemis-core-client/target/artemis-core-client-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-core-client-*.jar
md5sum $ARTEMIS_HOME/artemis-jms-server/target/artemis-jms-server-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-jms-server-*.jar
md5sum $ARTEMIS_HOME/artemis-server/target/artemis-server-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-server*.jar
md5sum $ARTEMIS_HOME/artemis-dto/target/artemis-dto-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-dto-*.jar
md5sum $ARTEMIS_HOME/artemis-journal/target/artemis-journal-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/main/artemis-journal-*.jar

md5sum $ARTEMIS_HOME/artemis-ra/target/artemis-ra-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/ra/main/artemis-ra-*.jar
md5sum $ARTEMIS_HOME/artemis-service-extensions/target/artemis-service-extensions-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/ra/main/artemis-service-extensions-*.jar

md5sum $ARTEMIS_HOME/artemis-protocols/artemis-hornetq-protocol/target/artemis-hornetq-protocol-$VERSION.jar $JBOSS_HOME/modules/system/layers/base/org/apache/activemq/artemis/protocol/hornetq/main/artemis-hornetq-protocol*.jar


