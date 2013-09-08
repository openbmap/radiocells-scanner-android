#!/usr/bin/python
#
#	Radiobeacon tools - Cell database builder
#    Copyright (C) 2013  wish7
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU Affero General Public License as
#    published by the Free Software Foundation, either version 3 of the
#    License, or (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU Affero General Public License for more details.
#
#    You should have received a copy of the GNU Affero General Public License
#    along with this program.  If not, see <http://www.gnu.org/licenses/>.

# Fixes lat/lon bug, which was present in 
#   00.6.00     lat/lon switched in begin and end position
#   00.6.01     lat/lon switched in begin and end position
#   00.6.02     lat/lon switched in begin and end position
#   00.7.00     lat/lon switched in end position
# Affected files will be corrected and assigned a new version number
#   00.6.10     for all 00.6.xx releases
#   00.6.20     for all 00.7.00 (there shouldn't be too much 00.7.00 files floating around anyway, as this bug was fixed very quick)
# A unmodified copy (of krank) files is saved in backup folder

import fileinput
import re
import sys
import fnmatch
import os
import shutil
import datetime

con = None
sep = os.sep
try:
  
    print ".. Fixing lat/lon bug "            
    for file in os.listdir('.'):
        if fnmatch.fnmatch(file, '*.xml'):
            
            # check if file is affected
            with open(file, 'r') as f:
                versionLine = f.readline()
                versionLine = f.readline()
                # match
                #<logfile manufacturer="samsung" model="GT-I9100" revision="4.2.2" swid="Radiobeacon" swver="00.6.00">
                versionPattern = r'(<logfile.*?)swver=\"(\S+)\"'
                versionTuples = re.search(versionPattern, versionLine, re.DOTALL)
                if versionTuples:
                    version = versionTuples.group(2)
                    if (version == "00.6.00" or version == "00.6.01" or version =="00.6.02") :
                        print file + ";version " + version + ";heal me"
                        fixBeginAndEnd = True
                        fixEndOnly = False
                    elif (version == "00.7.00") :
                        print file + ";version " + version + ";heal me"
                        fixBeginAndEnd = False
                        fixEndOnly = True
                    else:
                        print file + ";version" + version + ";skipped"
                        fixBeginAndEnd = False
                        fixEndOnly = False
                else:
                    fixBeginAndEnd = False
                    fixEndOnly = False
                    
            if fixBeginAndEnd:     
                # fixes all occurrences of gps tag
                result = open("."+sep+"healed"+sep+file,'w')
                for line in fileinput.input(file):
                    unchanged = True
                    # replace version string 
                    versionTuples = re.search(versionPattern, line, re.DOTALL)
                    if versionTuples:
                        result.write(versionTuples.group(1) + "swver=\"00.6.10\">\n")
                        unchanged = False
                        
                    # switches lng and lat attribute value
                    #   before <gps time="20130830225014" lng="67.8606" lat="20.24" alt="568.8" hdg="280" spe="0" accuracy="5" />
                    #   after  <gps time="20130830225014" lng="20.24" lat="67.8606" alt="568.8" hdg="280" spe="0" accuracy="5" /> 
                    gpsPattern = r'(<gps.*?)lng=\"(\S+)\" lat=\"(\S+)\"(.*?>)'
                    gpsTuples = re.search(gpsPattern, line, re.DOTALL)
                    if gpsTuples:
                        #print "Old " + line
                        #print "New " + gpsTuples.group(1) + gpsTuples.group(2) + gpsTuples.group(3) + gpsTuples.group(4)
                        result.write("\t"+gpsTuples.group(1) + "lng=\"" + gpsTuples.group(3) + "\" lat=\"" + gpsTuples.group(2) + "\"" + gpsTuples.group(4) + "\n")
                        unchanged = False
                    
                    # if not version or gps line skip
                    if unchanged == True:
                        result.write(line)      
                
                # save a copy without above changes
                src = ("."+sep+file)
                dest = ("."+sep+"backup"+sep+file)
                shutil.move(src, dest) 
            elif fixEndOnly :
                # fix only closing tag
                result = open("."+sep+"healed"+sep+file,'w')
                
                i = 0
                for line in fileinput.input(file):
                    unchanged = True
                 
                    # replace version string 
                    versionTuples = re.search(versionPattern, line, re.DOTALL)
                    if versionTuples:
                        result.write(versionTuples.group(1) + "swver=\"00.6.20\">\n")
                        unchanged = False
                        
                    # switches lng and lat inc end position tag, that is only every second tag
                    #   before <gps time="20130830225014" lng="67.8606" lat="20.24" alt="568.8" hdg="280" spe="0" accuracy="5" />
                    #   after  <gps time="20130830225014" lng="20.24" lat="67.8606" alt="568.8" hdg="280" spe="0" accuracy="5" /> 
                    gpsPattern = r'(<gps.*?)lng=\"(\S+)\" lat=\"(\S+)\"(.*?>)'
                    gpsTuples = re.search(gpsPattern, line, re.DOTALL)
                    if gpsTuples:
                        i = i + 1
                        if i % 2 == 0 :
                            result.write("\t"+gpsTuples.group(1) + "lng=\"" + gpsTuples.group(3) + "\" lat=\"" + gpsTuples.group(2) + "\"" + gpsTuples.group(4) + "\n")
                            unchanged = False
                        else :
                            unchanged = True
                    # if not version or gps line skip
                    if unchanged == True:
                        result.write(line)      
                
                # save a copy without above changes
                src = ("."+sep+file)
                dest = ("."+sep+"backup"+sep+file)
                shutil.move(src, dest) 
            else :
                # move unaffected files after run
                src = ("."+sep+file)
                dest = ("."+sep+"not_affected"+sep+file)
                shutil.move(src, dest)
finally:
    print ".. Done "         