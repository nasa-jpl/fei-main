# C-shell for developer to test for FMQ authentication without running the Apache Tomcat server.
#
# Arguments:
#
#  1 = userName
#  2 = authenticateMethod {USE_MYSQL_TO_AUTHENTICATE,USE_SYBASE_TO_AUTHENTICATE,USE_TFA_TO_AUTHENTICATE,USE_LDAP_TO_AUTHENTICATE}
#
# Make sure to compile the code with this first:
#
# % ant compile-mdms
#
# and having a directory containing these files:
#
#     config/komodo.config
#     config/komodo-aa-registry.cfg
#     config/.bindings
#
# where the .bindings is a softlink pointing to the approppriate .binding_* file according to what's being used to connect to database.
#
# For MySQL,  .bindings -> .bindings_build
# For Sybase, .bindings -> .bindings_dev
#
# Setting for developer with existing directory ~/tools/test_registry containg said above files and .bindings softlink pointing to .buildings_build
#

setenv KOMODO_HOME  ~/tools/test_registry 

# Change directory to directory contain this script or put the directory on your PATH environment.
#
# For developer:
#
# % cd ~/sandbox/FEI5/java/etc/bin
# % 
#
# Before running the next one, the softlink of .bindings have to point to .bindings_build file
#
# cd ~/tools/test_registry
# rm -f .bindings
# ln -s .bindings_build .bindings
# 
# rm -f komodo.config
# ln -s komodo.config_build komodo.config 
#
# To test using MySQL
#
# % source  fmq_aa_authenticator_test.csh <sample-user> USE_MYSQL_TO_AUTHENTICATE
#
# Before running the next one, the softlink of .bindings have to point to .bindings_dev file
#
# cd ~/tools/test_registry
# rm -f .bindings
# ln -s .bindings_dev .bindings
# 
# rm -f komodo.config
# ln -s komodo.config_dev komodo.config

# To test using Sybase.
#
# % source  fmq_aa_authenticator_test.csh <sample-user> USE_SYBASE_TO_AUTHENTICATE
#
# To test using the two tokens authentication.
#
# % source  fmq_aa_authenticator_test.csh <sample-user> USE_TFA_TO_AUTHENTICATE
#
# To test using JPL's LDAP authentication (your normal JPL login for timecard).
#
# % source  fmq_aa_authenticator_test.csh <sample-user> USE_LDAP_TO_AUTHENTICATE 

set user_name               = $1
set authentication_method   = $2

java -Dkomodo.home=$KOMODO_HOME  -classpath "$HOME/sandbox/FEI5/java/build/classes:/usr/local/vicar/core-d/html/jars/*:/usr/local/vicar/core-d/html/jars:/usr/local/vicar/core-d/html/jars/mdms-komodo-client.jar:/usr/local/vicar/core-d/html/jars/mdms-komodo-lib.jar:/usr/local/vicar/core-d/html/jars/xercesImpl.jar:/usr/local/vicar/core-d/html/jars/mdms.jar" jpl.mipl.mdms.web.fmq.store.security.AAValidator $user_name $authentication_method

exit;

# If successful, you should see the following output on your screen and the authenticationStatus is true.  If not successful, the authenticationStatus is false and you'll have
# investigage your environment set up:

#AAValidator::userName [<sample-user>]
#AAValidator::passcode [*****]
#Current IP address : xx.xx.xx.xx
#ipAddress = [137.78.38.117]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:Input:
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:i_userName [<sample-user>]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:i_passcode [*****]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:i_useTokenFlag [false]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:i_hostAddress  [137.78.38.117]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:m_komodoConfigFile [/home/<sample-user>/tools/test_registry/config/komodo.config]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:komodo.registry.aa.pluginClass        [jpl.mipl.mdms.FileService.komodo.registry.aa.MySQLAARegistry]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:komodo.registry.class                 [jpl.mipl.mdms.FileService.komodo.registry.dbms.MySQLRegistry]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator::authenticate:komodo.registry.aa.pluginClass.config [/home/<sample-user>/tools/test_registry/config/komodo-aa-registry.cfg]
#Fri Apr 18 12:26:42 PDT 2014 FMQAuthenticator:::dbRegistryFilename [/home/<sample-user>/tools/test_registry/config]
#log4j:WARN No appenders could be found for logger (jpl.mipl.mdms.FileService.komodo.registry.dbms.MySQLRegistry).
#log4j:WARN Please initialize the log4j system properly.
#Fri Apr 18 12:26:43 PDT 2014 FMQAuthenticator:::m_registry [jpl.mipl.mdms.FileService.komodo.registry.dbms.MySQLRegistry@117f31e]
#Fri Apr 18 12:26:43 PDT 2014 FMQAuthenticator:::m_aaregistry [jpl.mipl.mdms.FileService.komodo.registry.aa.MySQLAARegistry@5d9084]
#Fri Apr 18 12:26:43 PDT 2014 FMQAuthenticator::authenticate: Using PASSCODE_AUTHENTICATION
#Initializing c3p0 pool... com.mchange.v2.c3p0.PoolBackedDataSource@1b3f8f6 [ connectionPoolDataSource -> com.mchange.v2.c3p0.WrapperConnectionPoolDataSource@acb158 [ acquireIncrement -> 3, acquireRetryAttempts -> 30, acquireRet
#ryDelay -> 1000, autoCommitOnClose -> false, automaticTestTable -> null, breakAfterAcquireFailure -> false, checkoutTimeout -> 0, connectionTesterClassName -> com.mchange.v2.c3p0.impl.DefaultConnectionTester, factoryClassLocati
#on -> null, forceIgnoreUnresolvedTransactions -> false, idleConnectionTestPeriod -> 60, initialPoolSize -> 2, maxIdleTime -> 300, maxPoolSize -> 5, maxStatements -> 0, maxStatementsPerConnection -> 0, minPoolSize -> 2, nestedDa
#taSource -> com.mchange.v2.c3p0.DriverManagerDataSource@12bcd4b [ description -> null, driverClass -> com.mysql.jdbc.Driver, factoryClassLocation -> null, jdbcUrl -> jdbc:mysql://my-server.jpl.nasa.gov:xxx/komodo_build, propert
#ies -> {user=******, password=******} ] , preferredTestQuery -> SELECT name FROM servergroups, propertyCycle -> 300, testConnectionOnCheckin -> false, testConnectionOnCheckout -> true, usesTraditionalReflectiveProxies -> false 
#] , factoryClassLocation -> null, numHelperThreads -> 3, poolOwnerIdentityToken -> 1bbf1ca ] 
#Fri Apr 18 12:26:44 PDT 2014 FMQAuthenticator::authenticate:authSuccess = true
#Fri Apr 18 12:26:44 PDT 2014 FMQAuthenticator::authenticate: token = [xxx]
#0:xxx      1397885204000
#Fri Apr 18 12:26:44 PDT 2014 FMQAuthenticator:::m_aaregistry [jpl.mipl.mdms.FileService.komodo.registry.aa.MySQLAARegistry@5d9084]
#AAValidator::userName             [<sample-user>]
#AAValidator::authenticationStatus [true]

