/**
 * Exports  cells to xml format for later upload.
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

import org.openbmap.Preferences;
import org.openbmap.db.DataHelper;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.CellRecord;
import org.openbmap.db.model.LogFile;
import org.openbmap.soapclient.FileUploader.UploadTaskListener;

import android.R.bool;
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;


public class CellExporter implements UploadTaskListener {

	private static final String TAG = CellExporter.class.getSimpleName();

	/**
	 * OpenBmap cell upload address
	 */
	private static final String WEBSERVICE_ADDRESS = "http://openBmap.org/upload/upl.php5";

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
	private int mId;

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

	private int colNetworkType;
	private int colIsCdma;
	private int colIsServing;
	private int colIsNeigbor;
	private int colCellId;
	private int colPsc;
	private int colOperatorName;
	private int colOperator;
	private int colMcc;
	private int colMnc;
	private int colLac;
	private int colStrengthDbm;
	private int colTimestamp;
	private int colPositionId;
	private int colSessionId;

	private int	colReqLat;

	private int	colReqTimestamp;

	private int	colReqLon;

	private int	colReqAlt;

	private int	colReqHead;

	private int	colReqSpeed;

	private int	colReqAcc;

	private int	colBeginPosId;

	private int	colEndPosId;

	/**
	 *  Upload credentials : openbmap username
	 */
	private final String	mUser;

	/**
	 *  Upload credentials : openbmap password
	 */
	private final String	mPassword;

	/**
	 * Skip upload (for debugging purposes)
	 */
	private boolean	mSkipUpload;

	/**
	 * Skip delete (for debugging purposes)
	 */
	private boolean mSkipDelete;

	private static final String CELL_SQL_QUERY = " SELECT " + Schema.TBL_CELLS + "." + Schema.COL_ID + ", "
			+ Schema.COL_NETWORKTYPE + ", "
			+ Schema.COL_IS_CDMA + ", "
			+ Schema.COL_IS_SERVING + ", "
			+ Schema.COL_IS_NEIGHBOR + ", "
			+ Schema.COL_CELLID + ", "
			+ Schema.COL_LAC + ", "
			+ Schema.COL_MCC + ", "
			+ Schema.COL_MNC + ", "
			+ Schema.COL_PSC + ", "
			+ Schema.COL_BASEID + ", "
			+ Schema.COL_NETWORKID + ", "
			+ Schema.COL_SYSTEMID + ", "
			+ Schema.COL_OPERATORNAME + ", "
			+ Schema.COL_OPERATOR + ", "
			+ Schema.COL_STRENGTHDBM + ", "
			+ Schema.TBL_CELLS + "." + Schema.COL_TIMESTAMP + ", "
			+ Schema.COL_BEGIN_POSITION_ID + ", "
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
			+ " FROM " + Schema.TBL_CELLS 
			+ " JOIN \"" + Schema.TBL_POSITIONS + "\" AS \"req\" ON (" + Schema.COL_BEGIN_POSITION_ID + " = \"req\".\"_id\")"
			+ " JOIN \"" + Schema.TBL_POSITIONS + "\" AS \"last\" ON (" + Schema.COL_END_POSITION_ID + " = \"last\".\"_id\")"
			+ " WHERE " + Schema.TBL_CELLS + "." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_BEGIN_POSITION_ID;

	/**
	 * Default constructor
	 * @param mContext	Activities' mContext
	 * @param id Session id to export
	 * @param tempPath (full) path where temp files are saved. Will be created, if not existing.
	 * @param user Openbmap username
	 * @param password Openbmap password
	 */
	public CellExporter(final Context context, final int id, final String tempPath, final String user, final String password, final Boolean skipUpload, final Boolean skipDelete) {
		this.mContext = context;
		this.mId = id;
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
	 * Builds cell xml files and saves/uploads them
	 */
	protected final Boolean doInBackground() {
		Log.d(TAG, "Start cell export. Datasource: " + CELL_SQL_QUERY);

		final LogFile headerRecord = mDataHelper.loadLogFileBySession(mId);

		final DatabaseHelper mDbHelper = new DatabaseHelper(mContext);

		Cursor cursorCells = mDbHelper.getReadableDatabase().rawQuery(CELL_SQL_QUERY, new String[]{String.valueOf(mId)});
		// Otherwise saveAndMoveCursor skips first entry
		//cursorCells.moveToFirst();

		colNetworkType = cursorCells.getColumnIndex(Schema.COL_NETWORKTYPE);
		colIsCdma = cursorCells.getColumnIndex(Schema.COL_IS_CDMA);
		colIsServing = cursorCells.getColumnIndex(Schema.COL_IS_SERVING);
		colIsNeigbor = cursorCells.getColumnIndex(Schema.COL_IS_NEIGHBOR);
		colCellId = cursorCells.getColumnIndex(Schema.COL_CELLID);
		colPsc = cursorCells.getColumnIndex(Schema.COL_PSC);
		colOperatorName = cursorCells.getColumnIndex(Schema.COL_OPERATORNAME);
		colOperator = cursorCells.getColumnIndex(Schema.COL_OPERATOR);
		colMcc = cursorCells.getColumnIndex(Schema.COL_MCC);
		colMnc = cursorCells.getColumnIndex(Schema.COL_MNC);
		colLac = cursorCells.getColumnIndex(Schema.COL_LAC);
		colStrengthDbm = cursorCells.getColumnIndex(Schema.COL_STRENGTHDBM);
		colTimestamp = cursorCells.getColumnIndex(Schema.COL_TIMESTAMP);
		colBeginPosId = cursorCells.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		colEndPosId = cursorCells.getColumnIndex(Schema.COL_END_POSITION_ID);
		colSessionId = cursorCells.getColumnIndex(Schema.COL_SESSION_ID);
		colReqLat = cursorCells.getColumnIndex("req_" + Schema.COL_LATITUDE);
		colReqTimestamp = cursorCells.getColumnIndex("req_" + Schema.COL_TIMESTAMP);
		colReqLon = cursorCells.getColumnIndex("req_" + Schema.COL_LONGITUDE);
		colReqAlt = cursorCells.getColumnIndex("req_" + Schema.COL_ALTITUDE);
		colReqHead = cursorCells.getColumnIndex("req_" + Schema.COL_BEARING);
		colReqSpeed = cursorCells.getColumnIndex("req_" + Schema.COL_SPEED);
		colReqAcc = cursorCells.getColumnIndex("req_" + Schema.COL_ACCURACY);

		long startTime = System.currentTimeMillis();

		// MCC for filename is taken from first cell in chunk. Currently this value is not parsed on server side, so this should be ok
		cursorCells.moveToFirst();
		final String mcc = cursorCells.getString(colMcc);
		cursorCells.moveToPrevious();

		int i = 0;
		while (!cursorCells.isAfterLast()) {
			// creates files of 100 cells each
			Log.i(TAG, "Cycle " + i);

			final String fileName  = mTempPath + generateFilename(mUser, mcc);

			saveAndMoveCursor(fileName, headerRecord, cursorCells);

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
		Log.i(TAG, "Serialize cells took " + difference + " ms");

		cursorCells.close();
		cursorCells = null;
		mDbHelper.close();

		return true;
	}


	/**
	 * Builds a valid cell log file. The number of records per file is limited (CHUNK_SIZE). Once the limit is reached,
	 * a new file has to be created. The file is saved at the specific location.
	 * A log file file consists of an header with basic information on cell manufacturer and model, software id and version.
	 * Below the log file header, scans are inserted. Each scan can contain several wifis
	 * @see <a href="http://sourceforge.net/apps/mediawiki/myposition/index.php?title=Wifi_log_format">openBmap format specification</a>
	 * @param fileName Filename, including full path
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
			// Iterate cells cursor until last row reached or CHUNK_SIZE is reached 			
			while (i < CHUNK_SIZE && cursor.moveToNext()) {

				final long beginId = Long.valueOf(cursor.getString(colBeginPosId));

				currentBegin = cursorToBeginPositionXml(cursor);
				// TODO check, begin position is used instead of end position
				currentEnd = cursorToBeginPositionXml(cursor);

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
				 *  so write cell xml now
				 */
				bw.write(cursorToEntryXml(cursor));

				previousBeginId = beginId;
				previousEnd = currentEnd;

				i++;
			}

			// If we are at the last cell, close open scan and gps tag
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
	private String cursorToEntryXml(final Cursor cursor) {
		// TODO: PSC is yet missing
		String result = "";
		if (cursor.getLong(colIsServing) != 0) {

			result = "\n\t\t<gsmserving mcc=\"" + cursor.getString(colMcc) + "\"" 
					+ " mnc=\"" + cursor.getString(colMnc) + "\""
					+ " lac=\"" + cursor.getString(colLac) + "\""
					+ " id=\"" + cursor.getString(colCellId) + "\""
					+ " ss=\"" + cursor.getString(colStrengthDbm) + "\""
					+ " act=\"" + CellRecord.NetworkTypeDescription.get(colNetworkType) + "\""
					+ "/>"; 

		} else if (cursor.getLong(colIsNeigbor) != 0) {
			// TODO: Check act
			result	= "\n\t\t<gsmneighbour mcc=\"" + cursor.getString(colMcc) + "\"" 
					+ " mnc=\"" + cursor.getString(colMnc) + "\""
					+ " lac=\"" + cursor.getString(colLac) + "\""
					+ " id=\"" + cursor.getString(colCellId) + "\""
					+ " psc=\"" + cursor.getString(colPsc) + "\""
					+ " rxlev=\"" + cursor.getString(colStrengthDbm) + "\""
					+ " act=\"" + CellRecord.NetworkTypeDescription.get(colNetworkType) + "\""
					+ "/>";
		}
		return result;
	}

	/**
	 * Generates filename
	 * Template for cell logs:
	 * username_V2_250_log20120110201943-cellular.xml
	 * i.e. [username]_V[format version]_[mcc]_log[date]-cellular.xml
	 * Keep in mind, that openbmap server currently only accepts filenames following the above mentioned
	 * naming pattern, otherwise files are ignored.
	 * @return filename
	 */
	private static String generateFilename(final String user, final String mcc) {	
		// TODO: filename collisions possible, if called in less than a second
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		Date now = new Date();
		final String filename = /*user + "_V2_" */ "V2_" + mcc + "_log" + formatter.format(now) + "-cellular.xml";
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
