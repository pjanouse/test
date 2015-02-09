define(`foreach', `ifelse(eval($#>2), 1,
`pushdef(`$1', `$3')$2`'popdef(`$1')
`'ifelse(eval($#>3), 1, `$0(`$1', `$2', shift(shift(shift($@))))')')')dnl
dnl
define(ISSUE_DESCRIPTION, `Some multiline description
goes here')dnl
define(ISSUE_URL, http://bugzilla.redhat.com/show_bug.cgi?id=...)dnl
dnl
define(EAP_VERSION, 6.4.0.ER2)dnl
define(EAP_ZIP_NAME, jboss-eap-EAP_VERSION.zip)dnl
dnl
define(TEST_LIST, `TestClass1,TestClass2')dnl
define(ISSUE_VERIFICATION, `')dnl
dnl
define(VERIFIED_BY, msvehla@redhat.com)dnl
define(VERIFIED_DATE, esyscmd(`date +%F'))dnl
dnl
dnl
Issue description  
-----------------
ISSUE_DESCRIPTION
For more info see ISSUE_URL

Issue platform/DB/JVM dependency  
--------------------------------
depends on platform: NOT  
depends on DB      : NOT  
depends on JVM     : NOT  
  
Issue verification  
------------------
Run following tests from the internal HornetQ teststuite:
foreach(`t', ` t', TEST_LIST)dnl

or

Clone our testsuite from git:
git://git.app.eng.bos.redhat.com/jbossqe/eap-tests-hornetq.git

Go to eap-tests-hornetq/scripts and run groovy script PrepareServers.groovy
with downloaded EAP EAP_VERSION (groovy at least 1.8+ must be used) - modify paths as needed
groovy -DEAP_ZIP_URL=file:///home/mnovak/tmp/EAP_ZIP_NAME PrepareServers.groovy

(Script will prepare 4 servers - server1..4 in the directory where are you
currently standing.)

Export these paths to server directories and mcast address:
export JBOSS_HOME_1=$PWD/server1/jboss-eap
export JBOSS_HOME_2=$PWD/server2/jboss-eap
export JBOSS_HOME_3=$PWD/server3/jboss-eap
export JBOSS_HOME_4=$PWD/server4/jboss-eap
export MCAST_ADDR=235.3.4.5

Go to jboss-hornetq-testsuite/ and run the tests:

mvn clean install -Dtest=TEST_LIST | tee log

ISSUE_VERIFICATION

See HornetQ internal testsuite cookbook (https://mojo.redhat.com/docs/DOC-1013850) for more details
on how to work with the testsuite.

Verified by  
-----------
VERIFIED_BY
last update VERIFIED_DATE

