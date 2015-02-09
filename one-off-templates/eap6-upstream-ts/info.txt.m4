define(ISSUE_DESCRIPTION, `Some multiline description
goes here')dnl
define(ISSUE_URL, http://bugzilla.redhat.com/show_bug.cgi?id=...)dnl
dnl
define(HORNETQ_REPO, git://github.com/hornetq/hornetq-oneoffs.git)dnl
define(HORNETQ_REF, oneoff-HornetQ_2_3_5_FINAL-BZ1096984)dnl
define(EAP_VERSION, 6.1.1)dnl
dnl
define(TEST_LIST, `ClusteredGroupingTest,WildcardAddressManagerUnitTest')dnl
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
 - get HornetQ oneoff repo from HORNETQ_REPO, tag
   HORNETQ_REF
 - unpack EAP EAP_VERSION and install the patch as per instructions (delete old JARs from HornetQ EAP module)
 - run 'mvn clean install' in hornetq repo
 - run $ROOT/replace.sh [EAP home] [HornetQ repo root]
 - run 'mvn install -Phudson-tests -DfailIfNoTests=false -Dtest=TEST_LIST'
   in hornetq repo to verify all fixes

All tests need to pass.

Verified by  
-----------  
VERIFIED_BY
last update VERIFIED_DATE

