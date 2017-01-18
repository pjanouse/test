RESULTS_CHECKER_HOME=$WORKSPACE/results-checker
RESULTS_CHECKER_FILTERS=$RESULTS_CHECKER_HOME/filters


# clone and build results checker
 git clone https://github.com/jirka007/results-checker.git $RESULTS_CHECKER_HOME
cd $RESULTS_CHECKER_HOME
mvn clean install -DskipTests

#clone and build messaging filters
git clone https://gitlab.mw.lab.eng.bos.redhat.com/mstyk/messaging-filters.git $RESULTS_CHECKER_FILTERS
cd $RESULTS_CHECKER_FILTERS
mvn clean install -DskipTests
mv $RESULTS_CHECKER_FILTERS/target/results-checker-messaging-filters-*.jar $RESULTS_CHECKER_FILTERS/target/results-checker-messaging-filters.jar

# run results checker
export SERVER_NAME=jenkins.mw.lab.eng.bos.redhat.com
export JAR_PATH=$RESULTS_CHECKER_FILTERS/target/results-checker-messaging-filters.jar
export PACKAGE="org.jboss.qe.collector.filter.messaging"
export REPORTS_DIRECTORY=$WORKSPACE/eap-testsuite/jboss-hornetq-testsuite/**/target/surefire-reports/*.xml

cd $RESULTS_CHECKER_HOME/target
java -jar results-checker-*
