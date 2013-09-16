@echo off
REM
REM ===================================================================================================
REM Converts all pbf files in ..\downloads into a Radiobeacon compatible map
REM Processed map files are available in sub-folder ..\maps
REM ===================================================================================================
REM
REM Prerequisites:
REM     osmosis 0.42 (http://dev.openstreetmap.de:23457/hudson/job/osmosis-release/8/)
REM         Version is important, as earlier versions have problems with 64-bit ids, laters won't work
REM         for unknown reasons
REM     mapsforge-mapswriter-plugin (https://ci.mapsforge.org/job/mapsforge-rewrite/lastBuild/org.mapsforge$mapsforge-map-writer/)
REM         To be copied in folder C:\Users\[USERNAME]\AppData\Roaming\Openstreetmap\Osmosis\Plugins
REM 
REM Installation
REM	Unzip osmosis 0.42 to your hard disk	
REM	Copy this script into your osmosis\bin folder
REM 	Get some pbf files from http://download.geofabrik.de/ and save them in ..\downloads
REM	Start generate_maps.bat
REM 
REM Important note: 
REM
REM !!! Keep an eye at your temp folder, i.e. C:\Users\[USERNAME]\AppData\Local\Temp !!!
REM
REM     osmosis sometimes doesn't cleanup properly, so you may end with tens of gigabytes
REM     temp files e.g. idxNodes(...).tmp, idxWays(...).tmp
REM     It's safe to delete these files after map generation.
REM

setlocal enableextensions
setlocal enabledelayedexpansion

REM Create input and output folder
if not exist ..\downloads md ..\downloads
if not exist ..\maps md ..\maps

REM Process pbf files
for /R "..\downloads\." %%f in (*.pbf) do (
    set source=%%~nxf
    set target=..\maps\!source:-latest.osm.pbf=!.map
    call :body !source! !target!
    echo Processed !source!
)
goto :eof

:body
    REM Run osmosis
    echo =================== Processing %1 %2 ==================================
    call osmosis --rb file=..\downloads\%1 --mapfile-writer file=%2 type=hd
    exit /b 1

:eof
    echo. Finished..
    pause