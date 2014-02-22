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

from xml.etree import ElementTree
import sqlite3 as lite
import sys
import fnmatch
import os
import shutil
import datetime

con = None
recreate_tables = True
force_foreign_keys = False
sep = os.sep
try:
    con = lite.connect('db.sqlite')
    cur = con.cursor()
    if recreate_tables:
        # drop existing table
        print ".. Droping existing tables "
        cur.execute("DROP TABLE IF EXISTS scan")
        cur.execute("DROP TABLE IF EXISTS gps")
        cur.execute("DROP TABLE IF EXISTS cells")
        cur.execute("DROP TABLE IF EXISTS timestamp")
        
        print ".. Creating tables "
        # re-create tables
        cur.execute("CREATE TABLE scan (_id INTEGER PRIMARY KEY AUTOINCREMENT,time text,distance text, source text)")
        cur.execute("CREATE TABLE timestamp (_id INTEGER PRIMARY KEY AUTOINCREMENT, import text)")
        if force_foreign_keys:
            cur.execute("PRAGMA foreign_keys=OFF")
            cur.execute("CREATE TABLE gps (_id INTEGER PRIMARY KEY AUTOINCREMENT,scan_id INTEGER, time text,lon numeric,lat numeric,altitude text,hdg numeric,spe text,accuracy numeric, source text, FOREIGN KEY(scan_id) REFERENCES scan(_id))")
            cur.execute("CREATE TABLE cells (_id INTEGER PRIMARY KEY AUTOINCREMENT,scan_id INTEGER, mcc text, mnc text, lac text, id text, psc text, ss text, act text, rxlev text, ta text, is_neighbour numeric, source text, FOREIGN KEY(scan_id) REFERENCES scan(_id))")
        else:
            cur.execute("CREATE TABLE gps (_id INTEGER PRIMARY KEY AUTOINCREMENT,scan_id INTEGER, time text,lon numeric,lat numeric,altitude numeric,heading numeric,spe text,accuracy numeric, source text)")
            cur.execute("CREATE TABLE cells (_id INTEGER PRIMARY KEY AUTOINCREMENT,scan_id INTEGER, mcc text, mnc text, lac text, id text, psc text, ss text, act text, rxlev text, ta text, is_neighbour numeric, source text)")
    
    print ".. Importing "            
    for file in os.listdir('.'):
        if fnmatch.fnmatch(file, '*.xml'):
            print file
            
            with open(file, 'rt') as f:
                try:
                    tree = ElementTree.parse(f)
                except ElementTree.ParseError:
                    print "---> Krank file found: ", file
                    file_is_krank = True
                    f.close()
                    src = ("."+sep+file)
                    dest = ("."+sep+"krank"+sep+file)
                    shutil.move(src, dest)
                    continue
                    
                
            # find out which kind of lat/lon bug we have (caused by export version, hence exportver is important)
            # Radiobeacon 00.6.xx: lat/lon swapped
            # Radiobeacon 00.7.00: lat/lon OK at beginning of scan, swapped at end of scan
            # Note that files with swver 00.6.xx may have either version of the lat/lon bug and exportver is empty
            # Files with exportver or swid of 00.7.01 or higher are OK
            latlonbugV6 = False
            latlonbugV7 = False
            if (tree.getroot().attrib.get('swid') == 'Radiobeacon'):
                if (tree.getroot().attrib.get('swver') == '00.7.00') and (tree.getroot().attrib.get('exportver', None) is None):
                    latlonbugV7 = True
                elif (tree.getroot().attrib.get('swver') in ['00.6.00', '00.6.01', '00.6.02']) and (tree.getroot().attrib.get('exportver', None) is None):
                    # find out if we have V6 or V7
                    node = tree.find('scan')
                    if node is not None:
                        # if we don't have any scans, the lat/lon bug is a moot point
                        isFirst = True
                        for gps in node.iter('gps'):
                            if isFirst:
                                lng1 = float(gps.attrib.get('lng'))
                                lat1 = float(gps.attrib.get('lat'))
                                isFirst = False
                            else:
                                lng2 = float(gps.attrib.get('lng'))
                                lat2 = float(gps.attrib.get('lat'))
                                # FIXME: this may be unreliable where lat = lon
                                if ((lng1 - lng2) ** 2 + (lat1 - lat2) ** 2) > ((lat1 - lng2) ** 2 + (lng1 - lat2) ** 2):
                                    latlonbugV7 = True;
                                else:
                                    latlonbugV6 = True;
                
            for node in tree.iter('scan'):
                time = node.attrib.get('time')
                distance = node.attrib.get('distance')
                cur.execute("INSERT INTO scan VALUES(NULL,?,?,?)", (time,distance,file))
                # con.commit()
                scan_id = cur.lastrowid
                # add gps info
                isFirst = True
                for gps in node.iter('gps'):
                    time= gps.attrib.get('time')
                    if latlonbugV6 or (latlonbugV7 and not isFirst):
                        lat= gps.attrib.get('lng')
                        lng= gps.attrib.get('lat')
                    else:
                        lng= gps.attrib.get('lng')
                        lat= gps.attrib.get('lat')
                    alt= gps.attrib.get('alt')
                    hdg= gps.attrib.get('hdg')
                    spe= gps.attrib.get('spe')
                    accuracy= gps.attrib.get('accuracy')
                    cur.execute("INSERT INTO gps VALUES(NULL,?,?,?,?,?,?,?,?,?)", (scan_id,time,lng,lat,alt,hdg,spe,accuracy,file))
                    # con.commit()
                    
                # add serving cell info
                for serving in node.iter('gsmserving'):
                    mcc = serving.attrib.get('mcc')
                    mnc = serving.attrib.get('mnc')
                    lac = serving.attrib.get('lac')
                    id = serving.attrib.get('id')
                    ss = serving.attrib.get('ss')
                    act = serving.attrib.get('act')
                    rxlev = serving.attrib.get('rxlev')
                    ta = serving.attrib.get('ta')
                    cur.execute("INSERT INTO cells VALUES(NULL,?,?,?,?,NULL,?,?,?,?,?,NULL,?)", (scan_id,mcc,mnc,lac,id,ss,act,rxlev,ta,file))
                    # con.commit()					
                    
					# add neighbour cell info
                for neighbour in node.iter('gsmneighbour'):
                    mcc = neighbour.attrib.get('mcc')
                    mnc = neighbour.attrib.get('mnc')
                    lac = neighbour.attrib.get('lac')
                    id = neighbour.attrib.get('id')
                    psc = neighbour.attrib.get('psc')
                    rxlev = neighbour.attrib.get('rxlev')
                    act = neighbour.attrib.get('act')

                    cur.execute("INSERT INTO cells VALUES(NULL,?,?,?,?,?,?,NULL,?,?,NULL,?,?)", (scan_id,mcc,mnc,lac,id,psc,act,rxlev,1,file))
                    # con.commit()
					
            # move file after run
            src = ("."+sep+file)
            dest = ("."+sep+"processed"+sep+file)
            shutil.move(src, dest)
            con.commit()
              
except lite.Error, e:
    
    print "Error %s:" % e.args[0]
    sys.exit(1)
    
finally:
    
    if con:
        now = (datetime.datetime.now(),)
        cur.execute("INSERT INTO timestamp VALUES(NULL,?)", now)
	    # re-enable foreign keys
        cur.execute("PRAGMA foreign_keys=OFF")
        con.close()

try:
    con = lite.connect('db.sqlite')
    
    cur = con.cursor()    
    cur.execute("SELECT * FROM scan")
	    
except lite.Error, e:
    
    print "Error %s:" % e.args[0]
    sys.exit(1)
    
finally:
    
    if con:
        con.close()


