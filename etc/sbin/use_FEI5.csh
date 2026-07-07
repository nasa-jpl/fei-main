#!/bin/csh -f
#
### ==================================================================== ###
#                                                                          #
#  The File Exchange Interface (FEI) Environment Setup Script              #
#                                                                          #
#  Function:                                                               #
#  Simple shell script to add FEI5 launchers to clients path.              #
#                                                                          #
#  Created:                                                                #
#  Jan. 28, 2005 created to simplify server distribution                   #
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
#  Modifications:                                                          #
### ==================================================================== ###
#
# $Id: use_FEI5.csh,v 1.2 2006/10/05 17:37:10 awt Exp $
#

setenv FEI5 ${cwd}/config
setenv PATH ${FEI5}/../sbin:${PATH}
