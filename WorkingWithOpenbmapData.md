# Getting Openbmap data #
## Your own sessions ##
Activate _Keep upload files_ (or _Simulate Upload_) in the settings screen. The next time you upload a session, log files are copied to your SD card. If you haven't changed the default settings, you'll find them in _/org.openbmap_ folder.

For a quick analysis, you can also activate _Save GPS track_ in settings menu. This will generate a gpx file on next upload. For privacy reasons, the gpx track is **not** uploaded, but saved locally on your device only. After export, you'll find them in /org.openbmap/ folder. Keep in mind, that gpx files aren't deleted automatically!

## Worldwide dataset ##
It's perhaps more interesting to analyse worldwide data, instead of just your recent sessions. So check-out world-wide cell and wifi data at the OpenBmap website (http://openbmap.org/latest/cellular/raw/input_raw.zip and http://openbmap.org/latest/wifi/raw/wifi-input_raw.zip)

# Preparing the data #
In both cases, you'll have tons of raw xml log files by now. This isn't very handy, so checkout the python 2.7.3 scripts, which create a more comfortable sqlite database, see http://code.google.com/p/openbmap/source/browse/#git%2Ftools

# Analysis tools #
So, what can you do with the data now? Well, that's up to you..
Share your ideas in the forum :-)

For instance, you can
  * Fire up Quantum GIS (http://www.qgis.org/) to analysis cell strength, cell density etc.

![http://wiki.openbmap.googlecode.com/git/Wifis%20in%20QGIS.png](http://wiki.openbmap.googlecode.com/git/Wifis%20in%20QGIS.png)

  * If you've choosen GPX export, you might want to have a look at your last session: I use JOSM (http://josm.openstreetmap.de/) as a sort of a swiss-army-knife. It's pretty cool to activate the sat image layer, leverage the highly detailed OSM data and combine that with your scanned wifi or cell data