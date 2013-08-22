/**
 * Exports wifis to xml format for later upload.
 */
package org.openbmap.soapclient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.openbmap.db.DataHelper;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.LogFile;
import org.openbmap.soapclient.FileUploader.UploadTaskListener;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

public class WifiExporter implements UploadTaskListener {

	private static final String TAG = WifiExporter.class.getSimpleName();

	/**
	 * OpenBmap wifi upload address
	 */
	private static final String WEBSERVICE_ADDRESS = "http://www.openbmap.org/upload_wifi/upl.php5";

	/**
	 * XML header.
	 */
	private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>";

	/**
	 * XML template for logfile header
	 */
	private static final String	OPEN_LOGFILE = "\n<logfile manufacturer=\"%s\""
			+ " model=\"%s\""
			+ " revision=\"%s\""
			+ " swid=\"%s\""
			+ " swver=\"%s\">";

	/**
	 * XML template closing logfile
	 */
	private static final String	CLOSE_LOGFILE	= "\n</logfile>";


	/**
	 * XML template closing scan tag
	 */
	private static final String	CLOSE_SCAN_TAG	= "\n</scan>";

	/**
	 * Entries per log file
	 */
	private static final int CHUNK_SIZE	= 100;


	private Context mContext;

	/**
	 * Session Id to export
	 */
	private int mSession;

	/**
	 * Message in case of an error
	 */
	private String errorMsg = null;

	/**
	 * Datahelper
	 */
	private DataHelper mDataHelper;

	/**
	 * Directory where xmls files are stored
	 */
	private String	mTempPath;

	private int	colLastAcc;

	private int	colLastSpeed;

	private int	colLastHead;

	private int	colLastAlt;

	private int	colLastLon;

	private int	colLastTimestamp;

	private int	colLastLat;

	private int	colReqAcc;

	private int	colReqSpeed;

	private int	colReqHead;

	private int	colReqAlt;

	private int	colReqLon;

	private int	colReqTimestamp;

	private int	colReqLat;

	private int	colEndPosId;

	private int	colBeginPosId;

	private int	colTimestamp;

	private int	colLevel;

	private int	colFreq;

	private int	colCapa;

	private int	colMd5Ssid;

	private int	colSsid;

	private int	colBssid;

	private String	mUser;

	private String	mPassword;

	/**
	 * Skip upload (for debugging purposes)
	 */
	private boolean	mSkipUpload;

	/**
	 * Skip delete (for debugging purposes)
	 */
	private boolean mSkipDelete;

	
	private static final String WIFI_SQL_QUERY = " SELECT " + Schema.TBL_WIFIS + "." + Schema.COL_ID + " AS \"_id\","
			+ Schema.COL_BSSID + ", "
			+ Schema.COL_SSID + ", "
			+ Schema.COL_MD5_SSID + ", "
			+ Schema.COL_CAPABILITIES + ", "
			+ Schema.COL_FREQUENCY + ", "
			+ Schema.COL_LEVEL + ", "
			+ Schema.TBL_WIFIS + "." + Schema.COL_TIMESTAMP + ", "
			+ Schema.COL_BEGIN_POSITION_ID + " AS \"request_pos_id\","
			+ Schema.COL_END_POSITION_ID + " AS \"last_pos_id\","
			+ Schema.TBL_WIFIS + "." + Schema.COL_SESSION_ID + ", "
			+ Schema.COL_IS_NEW_WIFI + " AS \"is_new_wifi\","
			+ " \"req\".\"latitude\" AS \"req_latitude\","
			+ " \"req\".\"longitude\" AS \"req_longitude\","
			+ " \"req\".\"altitude\" AS \"req_altitude\","
			+ " \"req\".\"accuracy\" AS \"req_accuracy\","
			+ " \"req\".\"timestamp\" AS \"req_timestamp\","
			+ " \"req\".\"bearing\" AS \"req_bearing\","
			+ " \"req\".\"speed\" AS \"req_speed\", "
			+ " \"last\".\"latitude\" AS \"last_latitude\","
			+ " \"last\".\"longitude\" AS \"last_longitude\","
			+ " \"last\".\"altitude\" AS \"last_altitude\","
			+ " \"last\".\"accuracy\" AS \"last_accuracy\","
			+ " \"last\".\"timestamp\" AS \"last_timestamp\","
			+ " \"last\".\"bearing\" AS \"last_bearing\","
			+ " \"last\".\"speed\" AS \"last_speed\""
			+ " FROM " + Schema.TBL_WIFIS 
			+ " JOIN \"" + Schema.TBL_POSITIONS + "\" AS \"req\" ON (\"request_pos_id\" = \"req\".\"_id\")"
			+ " JOIN \"" + Schema.TBL_POSITIONS + "\" AS \"last\" ON (\"last_pos_id\" = \"last\".\"_id\")"
			+ " WHERE " + Schema.TBL_WIFIS + "." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_BEGIN_POSITION_ID;

	/**
	 * Default constructor
	 * @param mContext	Activities' mContext
	 * @param id Session id to export
	 * @param tempPath (full) path where temp files are saved. Will be created, if not existing.
	 * @param user Openbmap username
	 * @param password Openbmap password
	 */
	public WifiExporter(final Context context, final int id, final String tempPath, final String user, final String password, final Boolean skipUpload, final Boolean skipDelete) {
		this.mContext = context;
		this.mSession = id;
		this.mTempPath = tempPath;
		this.mUser = user;
		this.mPassword = password;

		if (skipUpload != null) {
			this.mSkipUpload = skipUpload;
		} else {
			this.mSkipUpload = false;
		}
		
		if (skipDelete != null) {
			this.mSkipDelete = skipDelete;
		} else {
			this.mSkipDelete = false;
		}
		
		ensureTempPath(mTempPath);
		
		mDataHelper = new DataHelper(context);
	}

	/**
	 * Ensures temp file folder is existing and writeable.
	 * If folder not yet exists, it is created
	 */
	private boolean ensureTempPath(final String path) {
		File folder = new File(path);

		boolean folderAccessible = false;
		if (folder.exists() && folder.canWrite()) {
			folderAccessible = true;
		}

		if (!folder.exists()) {
			folderAccessible = folder.mkdirs();
		}
		return folderAccessible;
	}
	
	/**
	 * Builds wifi and cell xml files and saves/uploads them
	 */
	protected final Boolean doInBackground() {
		Log.d(TAG, "Start wifi export. Datasource: " + WIFI_SQL_QUERY);

		final LogFile headerRecord = mDataHelper.loadLogFileBySession(mSession);

		final DatabaseHelper mDbHelper = new DatabaseHelper(mContext);

		Cursor cursorWifis = mDbHelper.getReadableDatabase().rawQuery(WIFI_SQL_QUERY, new String[]{String.valueOf(mSession)});
		// otherwise saveAndMoveCursor skips first entry
		//cursorWifis.moveToFirst();

		colBssid = cursorWifis.getColumnIndex(Schema.COL_BSSID);
		colSsid = cursorWifis.getColumnIndex(Schema.COL_SSID);
		colMd5Ssid = cursorWifis.getColumnIndex(Schema.COL_MD5_SSID);
		colCapa = cursorWifis.getColumnIndex(Schema.COL_CAPABILITIES);
		colFreq = cursorWifis.getColumnIndex(Schema.COL_FREQUENCY);
		colLevel = cursorWifis.getColumnIndex(Schema.COL_LEVEL);
		colTimestamp = cursorWifis.getColumnIndex(Schema.COL_TIMESTAMP);
		colBeginPosId = cursorWifis.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		colEndPosId = cursorWifis.getColumnIndex(Schema.COL_END_POSITION_ID);

		colReqLat = cursorWifis.getColumnIndex("req_" + Schema.COL_LATITUDE);
		colReqTimestamp = cursorWifis.getColumnIndex("req_" + Schema.COL_TIMESTAMP);
		colReqLon = cursorWifis.getColumnIndex("req_" + Schema.COL_LONGITUDE);
		colReqAlt = cursorWifis.getColumnIndex("req_" + Schema.COL_ALTITUDE);
		colReqHead = cursorWifis.getColumnIndex("req_" + Schema.COL_BEARING);
		colReqSpeed = cursorWifis.getColumnIndex("req_" + Schema.COL_SPEED);
		colReqAcc = cursorWifis.getColumnIndex("req_" + Schema.COL_ACCURACY);

		colLastLat = cursorWifis.getColumnIndex("last_" + Schema.COL_LATITUDE);
		colLastTimestamp = cursorWifis.getColumnIndex("last_" + Schema.COL_TIMESTAMP);
		colLastLon = cursorWifis.getColumnIndex("last_" + Schema.COL_LONGITUDE);
		colLastAlt = cursorWifis.getColumnIndex("last_" + Schema.COL_ALTITUDE);
		colLastHead = cursorWifis.getColumnIndex("last_" + Schema.COL_BEARING);
		colLastSpeed = cursorWifis.getColumnIndex("last_" + Schema.COL_SPEED);
		colLastAcc = cursorWifis.getColumnIndex("last_" + Schema.COL_ACCURACY);

		long startTime = System.currentTimeMillis();

		int i = 0;
		// creates cursor of [CHUNK_SIZE] wifis per file
		while (!cursorWifis.isAfterLast()) {
			Log.i(TAG, "Cycle " + i);
			String fileName  = mTempPath + generateFilename(mUser);
			saveAndMoveCursor(fileName, headerRecord, cursorWifis);

			i += CHUNK_SIZE;

			// so far upload one by one, i.e. array size always 1
			ArrayList<String> files = new ArrayList<String>();
			files.add(fileName);
			
			if (!mSkipUpload) {
				new FileUploader(this, mUser, mPassword, WEBSERVICE_ADDRESS).execute(
						files.toArray(new String[files.size()]));
			}
		}

		long difference = System.currentTimeMillis() - startTime;
		Log.i(TAG, "Serialize wifi took " + difference + " ms");

		cursorWifis.close();
		cursorWifis = null;
		mDbHelper.close();

		return true;
	}


	/**
	 * Builds a valid wifi log file. The number of records per file is limited (CHUNK_SIZE). Once the limit is reached,
	 * a new file has to be created.
	 * A log file file consists of an header with basic information on cell manufacturer and model, software id and version.
	 * Below the log file header, scans are inserted. Each scan can contain several wifis
	 * @see <a href="http://sourceforge.net/apps/mediawiki/myposition/index.php?title=Wifi_log_format">openBmap format specification</a>
	 * @param fileName Filename including full path
	 * @param headerRecord Header information record
	 * @param cursor Cursor to read from
	 */
	private void saveAndMoveCursor(final String fileName, final LogFile headerRecord, final Cursor cursor) {

		// for performance reasons direct database access is used here (instead of content provider)
		// all columns are casted to string for the same reason
		try {

			File file = new File(fileName);
			//FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()), 30 * 1024);

			// Write header
			bw.append(XML_HEADER);
			bw.append(String.format(Locale.US, OPEN_LOGFILE,
					headerRecord.getManufacturer(), headerRecord.getModel(), headerRecord.getRevision(), headerRecord.getSwid(), headerRecord.getSwVersion()));

			long previousBeginId = 0;
			//long previousEndId = 0;
			String currentBegin = "";
			String currentEnd = "";
			String previousEnd = "";

			int i = 0;
			// Iterate wifis cursor until last row reached or CHUNK_SIZE is reached 			
			while (i < CHUNK_SIZE && cursor.moveToNext()) {

				final long beginId = Long.valueOf(cursor.getString(colBeginPosId));

				currentBegin = cursorToBeginPositionXml(cursor);
				currentEnd = cursorToEndPositionXml(cursor);

				if (i == 0) {
					// Write first scan and gps tag at the beginning
					bw.append("\n<scan time=\"" +  cursor.getLong(colTimestamp) + "\" >");
					bw.append(currentBegin);
				} else {
					// Later on, scan and gps tags are only needed, if we have a new scan
					if (beginId != previousBeginId) {

						// write end gps tag for previous scan
						bw.append(previousEnd);
						bw.append(CLOSE_SCAN_TAG);

						// Write new scan and gps tag 
						// TODO distance calculation, seems optional
						bw.append("\n<scan time=\"" + cursor.getLong(colTimestamp) + "\" >");
						bw.append(currentBegin);
					}
				}

				/*
				 *  At this point, we will always have an open scan and gps tag,
				 *  so write wifi xml now
				 */
				bw.write(cursorToXml(cursor));

				previousBeginId = beginId;
				previousEnd = currentEnd;

				i++;
			}

			// If we are at the last wifi, close open scan and gps tag
			bw.write(previousEnd);
			bw.write(CLOSE_SCAN_TAG);

			bw.append(CLOSE_LOGFILE);
			// ensure that everything is really written out and close 
			bw.close();
			file = null;

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private String cursorToEndPositionXml(final Cursor chunk) {
		return "\n\t<gps time=\"" + chunk.getString(colLastTimestamp) + "\""
				+ " lng=\"" + chunk.getString(colLastLat) + "\""
				+ " lat=\"" + chunk.getString(colLastLon) + "\""
				+ " alt=\"" + chunk.getString(colLastAlt) + "\""
				+ " hdg=\"" + chunk.getString(colLastHead)  + "\""
				+ " spe=\"" + chunk.getString(colLastSpeed) + "\""
				+ " accuracy=\"" + chunk.getString(colLastAcc) + "\""
				+ " />";
	}

	private String cursorToBeginPositionXml(final Cursor chunk) {
		return "\n\t<gps time=\"" + chunk.getString(colReqTimestamp) + "\""
				+ " lng=\"" + chunk.getString(colReqLat) + "\""
				+ " lat=\"" + chunk.getString(colReqLon) + "\""
				+ " alt=\"" + chunk.getString(colReqAlt) + "\""
				+ " hdg=\"" + chunk.getString(colReqHead)  + "\""
				+ " spe=\"" + chunk.getString(colReqSpeed) + "\""
				+ " accuracy=\"" + chunk.getString(colReqAcc) + "\""
				+ " />";
	}

	/**
	 * @param cursor
	 * @return
	 */
	private String cursorToXml(final Cursor cursor) {
		return "\n\t\t<wifiap bssid=\"" + cursor.getString(colBssid) + "\""
				+ " md5essid=\"" + cursor.getString(colMd5Ssid) + "\""
				+ " ssid=\"" + cursor.getString(colSsid) + "\""
				+ " capa=\"" + cursor.getString(colCapa) + "\""
				+ " ss=\"" + cursor.getString(colLevel) + "\""
				+ " ntiu=\"" + cursor.getString(colFreq) + "\""
				+ "/>";
	}


	/**
	 * Generates filename
	 * Template for wifi logs:
	 * username_V1_250_log20120110201943-wifi.xml
	 * i.e. [username]_V[format version]_log[date]-wifi.xml
	 * Keep in mind, that openbmap server currently only accepts filenames following the above mentioned
	 * naming pattern, otherwise files are ignored.
	 * @return filename
	 */
	private static String generateFilename(final String user) {	
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		Date now = new Date();
		final String filename = /*user + "_V1_"*/ "V1_" + formatter.format(now) + "-wifi.xml";
		return filename;
	}

	/* (non-Javadoc)
	 * Fired upon upload completion. Will clean-up temp files
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadCompleted()
	 */
	@Override
	public final void onUploadCompleted(final ArrayList<String> uploaded) {
		if (uploaded != null) {
			if (!mSkipDelete) {
				for (int i = 0; i < uploaded.size(); i++) {
					File temp = new File(uploaded.get(i));
					if (!temp.delete()) {
						Log.e(TAG, "Error deleting " + uploaded.get(i));
					}	
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.openbmap.soapclient.FileUploader.UploadTaskListener#onUploadFailed(java.lang.String)
	 */
	@Override
	public void onUploadFailed(final String error) {
		// TODO Auto-generated method stub

	}

}
