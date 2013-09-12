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

 # basic steps
            #   read file info from file's second line
            #   check if swver is affected version (00.6.00, 00.6.01, 00.6.02 or 00.7.00)
            #   if exportver tag is present everything is good (indicates version >= 0.7.1)
            #   if no exportver and swver 0.7.0 fix end position only
            #   if no exportver and swver 0.6.0, 0.6.1,0.6.2 use heuristics to determine which exportver has been used
            #   large differences fix end position indicates exportver 0.7.0, thus fix end position only
            #   small differences otherwise fix both
            
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
            
                latlonbugV6 = False
                latlonbugV7 = False
                
                # interpret header
                # match <logfile manufacturer="samsung" model="GT-I9100" revision="4.2.2" swid="Radiobeacon" swver="00.6.00" exportver="00.7.00">
                versionLine = f.readline()
                versionLine = f.readline()
                versionPattern = r'(<logfile.*?)swver=\"(\S+)\"'
                versionTuples = re.search(versionPattern, versionLine, re.DOTALL)
                if versionTuples:
                    version = versionTuples.group(2)
                    # possibly affected versions
                    if (version == "00.6.00" or version == "00.6.01" or version =="00.6.02" or version == "00.7.00") :
                        # search again, check if we find exportver, this means exported with good version
                        exportVersion = None
                        exportPattern = r'(<logfile.*?)exportver=\"(\S+)\"'
                        exportTuples = re.search(exportPattern, versionLine, re.DOTALL)
                        
                        if exportTuples:
                            exportVersion = exportTuples.group(2)
                        
                        if not exportVersion is None:
                            # tracked with faulty version, but exported with good version, so nothing to do
                            print file + ";version " + version + ";skipped (exported with " + exportVersion +")"
                        else:
                            # definitely affected..
                            if version == "00.7.00" :
                                latlonbugV7 = True
                                print file + ";version " + version + ";heal me"
                            else:
                                isFirstGps = True
                                for line in fileinput.input(file):
                                    # iterate first two gps tags
                                    gpsPattern = r'(<gps.*?)lng=\"(\S+)\" lat=\"(\S+)\"(.*?>)'
                                    gpsTuples = re.search(gpsPattern, line, re.DOTALL)
                                    if gpsTuples and isFirstGps:
                                        lng1 = float(gpsTuples.group(2))
                                        lat1 = float(gpsTuples.group(3))
                                        isFirstGps = False
                                    elif gpsTuples and not isFirstGps:
                                        lng2 = float(gpsTuples.group(2))
                                        lat2 = float(gpsTuples.group(3))
                                        # large difference between first and second gps
                                        if ((lng1 - lng2) ** 2 + (lat1 - lat2) ** 2) > ((lat1 - lng2) ** 2 + (lng1 - lat2) ** 2):
                                            latlonbugV7 = True
                                            print file + ";version " + version + ";(heuristic) latlonbugV7"
                                        else:
                                            latlonbugV6 = True
                                            print file + ";version " + version + ";latlonbugV6"
                                        break
                                fileinput.close()
                    else:
                        # tracked and exported with good version
                        print file + ";version " + version + ";skipped (good version)"
                else:
                    print file + ";version unknown;skipped"
                    
            if latlonbugV6:     
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
            elif latlonbugV7 :
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
                #print file + " not affected"
                # move unaffected files after run
                src = ("."+sep+file)
                dest = ("."+sep+"not_affected"+sep+file)
                shutil.move(src, dest)
finally:
    print ".. Done "         