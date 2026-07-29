#!/usr/bin/perl -w
# -*-Perl-*-
#
### =============================================================== ###
#                                                                     #
#  The Mission Data Management Service Launcher Configuration         #
#                                                                     #
#  Function:                                                          #
#  This module provides utility subroutines for configurating the     #
#  MDMS command line programs.                                        #
#                                                                     #
#  Assumptions:                                                       #
#  - Perl 5 is installed on the target platform in /usr/bin/perl      #
#  - The $FEI5 environment variable must point to the directory       #
#    containing the domain.fei, the SSL certificate, and              #
#    mdmsconfig.pm files.                                             #
#                                                                     #
#  Copyright (c) 2006 by the California Institute of Technology.      #
#  ALL RIGHTS RESERVED.  United States Government Sponsorship         #
#  acknowledged. Any commercial use must be negotiated with the       #
#  Office of Technology at the California Institute of Technology.    #
#                                                                     #
#  The technical data in this document (or file) is controlled for    #
#  export under the Export Administration Regulations (EAR), 15 CFR,  #
#  Parts 730-774. Violations of these laws are subject to fines and   #
#  penalties under the Export Administration Act.                     #
#                                                                     #
#                                                                     #
#  Created:                                                           #
#  Oct. 21, 2004 T. Huang {Thomas.Huang@jpl.nasa.gov}                 #
#                                                                     #
#  Modifications:                                                     #
#  2018-06-29 William: removed V2HTML related codes                   #
#                                                                     #
#                                                                     #
#                                                                     #
### =============================================================== ###
#
# $Id: mdmsconfig.pm,v 1.24 2017/12/21 23:31:56 awt Exp $
#

use strict;
use File::Spec;
use File::Basename;

# the global default JRE version
use constant DEFAULT_JRE_VERSION => 1.5;

##
# Subroutine to check the user JVM version and return the correct
# Java executable using the default required JRE version
#
# @param $debug the debug flag
#
sub getCmd {
   my ($debug) = @_;
   return &getCmdReqVersion (DEFAULT_JRE_VERSION, $debug);
}

##
# Subroutine to check the user JVM version and return the correct
# Java executable
#
# @param $reqVersion the minimum required JVM version
# @param $debug the debug flag
#
sub getCmdReqVersion {
   my ($reqVersion, $debug) = @_;

   print "[DEBUG] Requires JRE version $reqVersion.\n" if ($debug);

   # Let's start by assuming that java is somewhere on the PATH and set our
   # command to 'java'.  Next, check if JAVA_HOME is set.  If so use it!
   # If not, check if V2JDK is set and use it if it is.  Otherwise, stick
   # with our initial assumption
   my $javaCmd = "java";
   my $javaHome = $ENV{JAVA_HOME} ? $ENV{JAVA_HOME} : "";
   if ($javaHome eq "") {
       my $v2Jdk = $ENV{V2JDK} ? $ENV{V2JDK} : "";
       if ($v2Jdk eq "") {
           ; #do nothing
       } else {
           $javaCmd = File::Spec->catfile($v2Jdk, 'bin', 'java');
       }
   } else {
       $javaCmd = File::Spec->catfile($javaHome, 'bin', 'java');
   }

   my $version = "dummy";
   $version = `$javaCmd -version 2>&1`;
   $version = $version ? $version : "";
   if ( $version eq "" ) {
        print "[ERROR] Unable to execute JRE version.\n";
        die "[ERROR] Current JRE command: $javaCmd.\n";
   }

   #if ( $? != 0 ) {
   #
   #}

   if ($version =~ m/([0-9]+\.?[0-9]*)/) {
      $version = $1;
      print "[DEBUG] $version", "\n" if ($debug);
      if ($version < $reqVersion) {
         print "[ERROR] This software requires JRE version ";
         print "$reqVersion or above.\n";
         die "[ERROR] Current JRE command: $javaCmd.\n";
      }
   } else {
      print "[ERROR] Unable to determine JRE version.\n";
      die "[ERROR] Current JRE command: $javaCmd.\n";
   }
   return $javaCmd; #"$javaHome/bin/java";
}

#sub getCmdReqVersionOLD {
#   my ($reqVersion, $debug) = @_;
#
#   print "[DEBUG] Requires JRE version $reqVersion.\n" if ($debug);
#
#   my $v2Jdk = $ENV{V2JDK} ? $ENV{V2JDK} : "";
#   if ($v2Jdk eq "") {
#      $v2Jdk =
#         File::Spec->catfile(File::Spec->rootdir(), 'usr', 'java');
#   }
#
#   my $javaHome = $ENV{JAVA_HOME} ? $ENV{JAVA_HOME} : "";
#   if ($javaHome eq "") {
#      $javaHome = $v2Jdk;
#   }
#
#   my $javaCmd = File::Spec->catdir($javaHome, 'bin', 'java');
#   my $version = `$javaCmd -version 2>&1`;
#   if ($version =~ m/([0-9]+\.[0-9]+)/) {
#      $version = $1;
#      print "[DEBUG] $version", "\n" if ($debug);
#      if ($version < $reqVersion) {
#         print "[ERROR] This software requires JRE version ";
#         print "$reqVersion or above.\n";
#         die "[ERROR] Current JRE location $javaHome.\n";
#      }
#   } else {
#      print "[ERROR] Unable to determine JRE version.\n";
#      die "[ERROR] Current JRE location $javaHome.\n";
#   }
#   return $javaCmd; #"$javaHome/bin/java";
#}

##
# Subroutine to set and return the FEI5 environment variable
#
# @param $default the default location if the environment is not set
# @param $debug the debug flag
#
sub setFEI5 {
   my ($default, $debug) = @_;
   my $fei5 = $ENV{FEI5} ? $ENV{FEI5} : "";
   if ($fei5 eq "") {
      warn "FEI5 environment variable is not set.\n";
      warn "Using default location $default.\n";
      $fei5 = $default;
      $ENV{FEI5} = $fei5;
   }
   return $fei5;
}

##
# Subroutine to set and return the application classpath
#
# @param $classpath the user input classpath to be prepend to the
#    application classpath
# @param $debug the debug flag
#
sub getClasspath {
   my ($classpath, $debug) = @_;

   # MIPL env detection to support Java development
   # NOTE: removed V2HTML related codes.
   $classpath = "";
   my $fei5 =
      &setFEI5 (File::Spec->catdir($ENV{PWD}, "..", "config"), $debug);
   my $seperator = (($^O eq "MSWin32") ||
                   ($^O eq "dos") ||
                   ($^O eq "cygwin")) ? ";" : ":";

   my $mdmsjarpattern =
              File::Spec->catdir($fei5, "..", "lib", "*.jar");
           my @mdmsjarfiles =glob($mdmsjarpattern);
           print "[DEBUG] MDMS jarfiles=@mdmsjarfiles\n" if ($debug);
           foreach my $jar (@mdmsjarfiles ) {
                   $classpath .="$seperator$jar";
           }
   # this is used by the server
   # jgroups requires that the location of the keystore used by the encrypt protocol
   # be stored in the classpath
   $classpath .="$seperator$fei5";
   return $classpath;
}


##
# Subroutine to return the FEI restart directory location
#
# @param $debug the debug flag
#
sub getRestartDir {
   my ($debug) = @_;
   my $restartdir = $ENV{FEI5CCDIR} ? $ENV{FEI5CCDIR} : $ENV{HOME};
   print "[DEBUG] Restart directory = $restartdir" if ($debug);
   return $restartdir;
}

##
# Subroutine to return the JVM arguments for the FEI server.
#
# @param $classpath the user input classpath
# @param $debug the debug flag
#
sub getJVMArgsForServer {
  my ($classpath, $debug) = @_;
  my $fei5 =
      &setFEI5 (File::Spec->catdir($ENV{PWD}, "..", "config"), $debug);
   $classpath = &getClasspath ($classpath, $debug);

   my $app = File::Basename::basename($0);

   my @args;
   push @args, "-classpath", "$classpath";
   push @args, "-Dkomodo.config.dir=$fei5";
   # SSL truststore: PKCS12 format created through BC-FIPS provider
   # for FIPS 140-2/140-3 compliance
   push @args, "-Djavax.net.ssl.trustStore=$fei5/mdms-fei.keystore";
   push @args, "-Djavax.net.ssl.trustStorePassword=imfine";
   push @args, "-Djavax.net.ssl.trustStoreType=PKCS12";
   push @args, "-Djava.net.preferIPv4Stack=true";
   # FIPS 140-2/140-3 compliant cipher suites (AES-GCM and AES-CBC with SHA-2)

  # Disabling TLV 1.2 cipher suites
  # Note that   In TLSv1.2, the cipher suite name encodes everything — key exchange algorithm, authentication, encryption, and MAC hash — all in one string:
  #                 TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
  #                 ^key exch^ ^auth^ ^encrypt+mode^ ^hash^
   #   push @args, "-Dkomodo.net.ciphers=TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_256_GCM_SHA384,TLS_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA256";

   #     In TLSv1.3, those concerns were separated:
   #     - Key exchange is handled separately via named groups (secp256r1, etc.) — already configured via -Djdk.tls.namedGroups
   #     - Authentication is handled separately via the certificate itself
   #     - The cipher suite only specifies encryption + hash:
   #
   #     TLS_AES_256_GCM_SHA384
   #      ^encrypt+mode^ ^hash^
   #
   #     So there are only 3 possible TLSv1.3 cipher suites in Java 17 total (TLS_AES_256_GCM_SHA384, TLS_AES_128_GCM_SHA256, TLS_CHACHA20_POLY1305_SHA256),
   #       TLS_CHACHA20_POLY1305_SHA256 is NOT FIPS compliant

   push @args, "-Dkomodo.net.ciphers=TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256";
   push @args, "-Dkomodo.net.protocol=TLSv1.3";
   # FIPS-approved named groups only (no x25519)
   push @args, "-Djdk.tls.namedGroups=secp256r1,secp384r1,secp521r1";
   # Force JDK's XML parser to avoid conflict with old xerces JAR in dependencies (Java 17+)
   push @args, "-Djavax.xml.parsers.DocumentBuilderFactory=com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";
   push @args, "-Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";

   if ($^O eq "darwin") {
      push @args, "-Dcom.apple.backgroundOnly=true";
      push @args, "-Djava.awt.headless=true";
   }

   if ($debug) {
      push @args, "-Dmdms.enable.debug";
   }

   # Environment variable overrides for Docker/container environments
   my $feiDebug = $ENV{FEI_DEBUG} ? $ENV{FEI_DEBUG} : "0";
   if ($feiDebug eq "1") {
      push @args, "-Dmdms.enable.debug";
   }
   my $sslDebug = $ENV{FEI_SSL_DEBUG} ? $ENV{FEI_SSL_DEBUG} : "0";
   if ($sslDebug eq "1") {
      push @args, "-Djavax.net.debug=ssl:handshake:verbose";
   }

   return @args;
}

##
# Subroutine to return the JVM arguments for the FEI applcation.
#
# @param $classpath the user input classpath
# @param $debug the debug flag
#
sub getJVMArgs {
   my ($classpath, $debug) = @_;
   return &getJVMArgsCheckGUI ($classpath, 0, $debug);
}

##
# Subroutine to return the JVM arguments for the FEI applications.
# It checks for GUI flag to determin if GUI-related VM parameters
# needs to be set.
#
# @param $classpath the user input classpath
# @param $gui the GUI boolean flag
# @param $debug the debug flag
#
sub getJVMArgsCheckGUI {

   my ($classpath, $gui, $debug) = @_;

   my $fei5 =
      &setFEI5 (File::Spec->catdir($ENV{PWD}, "..", "config"), $debug);
   $classpath = &getClasspath ($classpath, $debug);
   my $restartdir = &getRestartDir ($debug);
   my $app = File::Basename::basename($0);

   my @args;
   push @args, "-classpath", "$classpath";
   # SSL truststore: PKCS12 format created through BC-FIPS provider
   # for FIPS 140-2/140-3 compliance
   push @args, "-Djavax.net.ssl.trustStore=$fei5/mdms-fei.keystore";
   push @args, "-Djavax.net.ssl.trustStorePassword=imfine";
   push @args, "-Djavax.net.ssl.trustStoreType=PKCS12";
   push @args, "-Dkomodo.restart.dir=$restartdir";
   push @args, "-Dkomodo.config.dir=$fei5";
   push @args, "-Dkomodo.domain.file=$fei5/domain.fei";
   push @args, "-Dmdms.logging.config=$fei5/mdms.lcf";
   push @args, "-Dkomodo.public.key=$fei5/public.der";
   push @args, "-Dkomodo.query.interval=1";
   push @args, "-Dkomodo.client.pulse=300";
   push @args, "-Dmdms.user.application=$app";
   push @args, "-Djava.net.preferIPv4Stack=true";
   push @args, "-Dkomodo.filehandling.enable=true";
   # FIPS 140-2/140-3 compliant cipher suites (AES-GCM and AES-CBC with SHA-2)
   # Disabling TLV 1.2 cipher suites
   # Note that   In TLSv1.2, the cipher suite name encodes everything — key exchange algorithm, authentication, encryption, and MAC hash — all in one string:
   #                 TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
   #                 ^key exch^ ^auth^ ^encrypt+mode^ ^hash^
#   push @args, "-Dkomodo.net.ciphers=TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_256_GCM_SHA384,TLS_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA256";

#     In TLSv1.3, those concerns were separated:
#     - Key exchange is handled separately via named groups (secp256r1, etc.) — already configured via -Djdk.tls.namedGroups
#     - Authentication is handled separately via the certificate itself
#     - The cipher suite only specifies encryption + hash:
#
#     TLS_AES_256_GCM_SHA384
#      ^encrypt+mode^ ^hash^
#
#     So there are only 3 possible TLSv1.3 cipher suites in Java 17 total (TLS_AES_256_GCM_SHA384, TLS_AES_128_GCM_SHA256, TLS_CHACHA20_POLY1305_SHA256),
#       TLS_CHACHA20_POLY1305_SHA256 is NOT FIPS compliant

   push @args, "-Dkomodo.net.ciphers=TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256";
   push @args, "-Dkomodo.net.protocol=TLSv1.3";
   # FIPS-approved named groups only (no x25519)
   push @args, "-Djdk.tls.namedGroups=secp256r1,secp384r1,secp521r1";
   # Force JDK's XML parser to avoid conflict with old xerces JAR in dependencies (Java 17+)
   push @args, "-Djavax.xml.parsers.DocumentBuilderFactory=com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";
   push @args, "-Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";

   if ($^O eq "darwin" && !$gui) {
      push @args, "-Dcom.apple.backgroundOnly=true";
      push @args, "-Djava.awt.headless=true";
   }

   if ($debug) {
      push @args, "-Dmdms.enable.debug";
   }

   # Environment variable overrides for Docker/container environments
   my $feiDebug = $ENV{FEI_DEBUG} ? $ENV{FEI_DEBUG} : "0";
   if ($feiDebug eq "1") {
      push @args, "-Dmdms.enable.debug";
   }
   my $sslDebug = $ENV{FEI_SSL_DEBUG} ? $ENV{FEI_SSL_DEBUG} : "0";
   if ($sslDebug eq "1") {
      push @args, "-Djavax.net.debug=ssl:handshake:verbose";
   }

   return @args;
}

1;
