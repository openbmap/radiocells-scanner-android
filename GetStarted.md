# What does it do? #
Radiobeacon scans cells and wifis and uploads their position to the open-source project OpenBmap (http://www.openbmap.org)

# Short manual #
### Tracking and uploading ###

Tap MENU and select new session to begin tracking session. To stop tracking press MENU once again. By LONG PRESS on session, you'll get a context menu, where you can delete or upload track.

Don't forget to enter your **openbmap credentials** in the settings menu  before trying to upload.

### Maps ###
You can also display your current position on a map (works even offline). Therefore select **Download map** in settings menu and choose your country. Unfortunately some countries are yet missing, especially France.

If your map is not on the list, you might want to manually add a map. Check available maps at http://download.mapsforge.org/maps/ or http://www.openandromaps.org/downloads and copy the downloaded maps in your _\org.openbmap\maps\_ folder. Restart the app and don't forget to **Select map** in settings.

### GPX tracks ###
You might want to analyse where you walked later: Therefore radiobeacon client can generate a gpx file, which can later be analysed in your favorite desktop programs, be it Quantum GIS, JOSM,... If needed, activate **Save GPX track** in settings menu. Tracks are stored on your SD card (i.e. _\org.openbmap\_).

Please be aware, that gpx track files can consume a considerable amount of space.

### Analysing track data offline ###
If you want to have a look at the raw xml files, activate **Keep upload files** in the settings menu and check the SD card folder (i.e. '\org.openbmap\')

### Displaying well-known wifis on map ###
You might want to find places, which haven't yet been added to the OpenBmap database.

Therefore it would be very useful to see, what's in the database already. This is exactly, what the reference database does: at a glance you'll see all known wifis on the map.

Click **Download wifi catalog** in the settings to get your offline copy. Right now the database is around 200 MB, but updated only at sporadic intervals. In the future this might be automated.