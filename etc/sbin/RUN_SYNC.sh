#!/bin/sh 
#
### ==================================================================== ###
#                                                                          #
#  The File Exchange Interface (FEI) Bridge Startup                        #
#                                                                          #
#  Function:                                                               #
#  Script to startup the FEI5 message bridge.                              #
#                                                                          #
#  Copyright (c) 2006 by the California Institute of Technology.           #
#  ALL RIGHTS RESERVED.  United States Government Sponsorship              #
#  acknowledged. Any commercial use must be negotiated with the            #
#  Office of Technology at the California Institute of Technology.         #
#                                                                          #
#  The technical data in this document (or file) is controlled for         #
#  export under the Export Administration Regulations (EAR), 15 CFR,       #
#  Parts 730-774. Violations of these laws are subject to fines and        #
#  penalties under the Export Administration Act.                          #
#                                                                          #
#  History:                                                                #
#  Aug. 18, 2006 initial release                                           #
#                                                                          #
#                                                                          #
### ==================================================================== ###
#
# $Id: RUN_SYNC.sh,v 1.2 2008/02/13 20:16:34 awt Exp $
#

MYNAME=`basename $0`
WRITE="echo "
DEBUG=1
umask 077


# Set variables
MAIN_CLASS=jpl.mipl.mdms.FileService.komodo.services.sync.QSync

# Check for FEI5 environment variable
if [ "${FEI5}Z" = "Z" ]
  then
     ${WRITE} ${MYNAME}: [ERROR] FEI5 environment variable is not set. 1>&2
     exit 1
fi


# include function 'module'
if [ ! -f ${FEI5}/mdmsconfig.sh ]
  then
     ${WRITE} ${MYNAME}: [ERROR] Could not locate mdmsconfig.sh in FEI5 directory. 1>&2
     exit 1
fi
. ${FEI5}/mdmsconfig.sh


# get Java command
JAVACMD=`getCmd`
if [ "${CLASSPATH}Z" = "Z" ]
  then 
     LCLASSPATH=""
  else
     LCLASSPATH=${CLASSPATH}
fi

# get JVM argument string
JVMARGS=`getJVMArgsForServer ${DEBUG} ${LCLASSPATH}`

# set some porperty values

LOG_CONF=${FEI5}/mdmsserver.lcf

LOG_FILE="default_fei_bridge.log"
CONFIG_FILE=""
HOSTNAME=`hostname`
while getopts "g:t:c:" OPTKEY 
do 
  case $OPTKEY in
    c) CONFIG_FILE={${OPTARG};;
    ?) ;;
  esac
done

# append specialized JVM arguments
JVMARGS="${JVMARGS} -Dmdms.logging.config=${LOG_CONF}"
JVMARGS="${JVMARGS} -Dmdms.logging.file=${LOG_FILE}"
JVMARGS="${JVMARGS} -Dkomodo.home=${FEI5}/.."
JVMARGS="${JVMARGS} -Dkomodo.version=${FEI5}/.."


if [ ${DEBUG} -eq 1 ]
  then
    echo ${JAVACMD} ${JVMARGS} ${MAIN_CLASS} ${CONFIG_FILE}
fi

# invoke Java command
${JAVACMD} ${JVMARGS} ${MAIN_CLASS} ${CONFIG_FILE}
EXIT_STAT=$?

if [ ${EXIT_STAT} -ne 0 ]
  then
   # exit with JAVA return status
   if [ ${DEBUG} -eq 1 ]
     then
       ${WRITE} ${MYNAME}: [ERROR] Program invocation exited with error status: ${EXIT_STAT} 1>&2
   fi
   exit ${EXIT_STAT}
fi

exit 0
   
