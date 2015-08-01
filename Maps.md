# Introduction #

Radiobeacon uses mapsforge compatible maps. Therefore you'll find plenty of ready-to-use maps in the internet. Frequently requested maps are already integrated into Radiobeacon client (Settings: **Download map**).

If you're missing a map just drop us a line so we might include it in the future..

## Other map sources ##
If your country isn't on the list, you may want to check some alternative map sources
  * [Openandromaps](http://www.openandromaps.org)
  * [Mapsforge](http://download.mapsforge.org/)
  * [Radiobeacon manual download](https://googledrive.com/host/0B97gHr4MqjHpM2h0QVR5SWJOcGs/maps)

After downloading such a map, copy it to your SD card into _/sdcard/org.openbmap/maps_ and restart Radiobeacon client.
The next time you'll go into Settings, choose **Select map** and there you go.

# Re-using maps from other apps #
You might want to re-use existing maps downloaded for other apps to save space. To achieve this configure **Map folder** in settings. Note, that **Map folder** is relative to internal SD card. So to use e.g. Locus maps in Radiobeacon enter

**/../locus/maps**

If maps are stored on a second external SD card, things become more complicated. 'Escape' from internal SD card first, by providing relative paths to Android root, e.g.

**/../../..** on Nexus 4

Then append the mount point of your external sd card, e.g.

**/../../../external**

and finally append your map folder, e.g.

**/../../../external/my\_map\_folder**

Note, this is experimental, so please report unexpected behaviour..

# Create your own map #
If you prefer to create your own maps, check out the map generator script in repository. As input it will take any pbf files and will generate a Radiobeacon compatible map file.

Prerequisites:
  1. Save [osmosis 0.42](http://dev.openstreetmap.de:23457/hudson/job/osmosis-release/8/) at _C:\osmosis\_
> Watch exactly for version 0.42, as earlier versions have problems with 64-bit ids, laters won't yet work for unknown reasons
  1. Save [maps generator script](http://code.google.com/p/openbmap/source/browse/#git%2Ftools%2Fmap_generator) at _C:\osmosis\bin\_
  1. Download [mapsforge-mapswriter-plugin](https://ci.mapsforge.org/job/mapsforge-rewrite/lastBuild/org.mapsforge$mapsforge-map-writer)
  1. Copy mapsforge-map-writer-0.4.0-SNAPSHOT-jar-with-dependencies.jar into osmosis plugin folder _C:\Users\[USERNAME](USERNAME.md)\AppData\Roaming\Openstreetmap\Osmosis\Plugins\_
  1. Create download and maps folder: _C:\osmosis\downloads\_ and _C:\osmosis\maps\_
  1. Get some [pbf file](http://downloads.geofabrik.de) and save in _C:\osmosis\downloads_
  1. Run _generate\_maps.bat_, wait some time and find the results in _maps_ folder
  1. Don't forget to copy your map to your sdcard  _/sdcard/org.openbmap/maps_ :-)