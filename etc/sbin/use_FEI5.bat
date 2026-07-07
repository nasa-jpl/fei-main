@echo off

rem ====================================================================== ###
rem                                                                          #
rem  The File Exchange Interface (FEI) Environment Setup Script              #
rem                                                                          #
rem  Function:                                                               #
rem  Simple MS-DOS script to add FEI5 launchers to clients path.             #
rem                                                                          #
rem  Created:                                                                #
rem  Jan. 28, 2005 created to simplify server distribution                   #
rem                                                                          #
rem  Copyright (c) 2006 by the California Institute of Technology.           #
rem  ALL RIGHTS RESERVED.  United States Government Sponsorship              #
rem  acknowledged. Any commercial use must be negotiated with the            #
rem  Office of Technology at the California Institute of Technology.         #
rem                                                                          #
rem  The technical data in this document (or file) is controlled for         #
rem  export under the Export Administration Regulations (EAR), 15 CFR,       #
rem  Parts 730-774. Violations of these laws are subject to fines and        #
rem  penalties under the Export Administration Act.                          #
rem                                                                          #
rem ====================================================================== ###
rem
rem $Id: use_FEI5.bat,v 1.2 2006/10/05 17:37:10 awt Exp $

rem Get the current working directory
set CWD=%CD%

rem Set to the location of Java distribution (should be set prior)
rem Go to http://www.java.com to download Sun's Java
rem set JAVA_HOME=C:\progra~1\java_1.4.2

rem Set the FEI5 variable 
set FEI5=%CWD%\config

rem Add FEI5 launchers to the path
set PATH=%FEI5%\..\sbin;%PATH%