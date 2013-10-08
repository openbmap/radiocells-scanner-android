/*
	Radiobeacon - Openbmap wifi and cell logger
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.openbmap.service.wireless.blacklists;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.util.Log;

/**
 * Creates initial wifi blacklist with some default entries
 */
public final class BssidBlackListBootstraper {

	private static final String	TAG	= BssidBlackListBootstraper.class.getSimpleName();

	/**
	 * XML opening tag prefix
	 */
	private static final String	PREFIX_OPEN	= "<prefix comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String	PREFIX_MIDDLE = "\">";

	/**
	 * XML closing tag prefix
	 */
	private static final String	PREFIX_CLOSE = "</prefix>";

	/**
	 * XML opening tag full mac address
	 */
	private static final String	ADDRESS_OPEN = "<bssid comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String	ADDRESS_MIDDLE = "\">";
	
	/**
	 * XML closing tag full mac address
	 */
	private static final String	ADDRESS_CLOSE	= "</bssid>";

	/**
	 * XML opening tag file
	 */
	private static final String	FILE_OPEN	= "<ignorelist>";

	/**
	 * XML closing tag file
	 */
	private static final String	FILE_CLOSE	= "</ignorelist>";

	private static final String[][] ADDRESSES = {
		{"Invalid mac", "00:00:00:00:00:00"} 
		// updates will follow:
		// check openbmap database for wifis mac which have more than one measurements
		// if measurements are too far way, it's either a mobile wifi or otherwise unreliable
	};

	private static final String[][] PREFIXES =  {
		// automotive manufacturers
		{"Harman/Becker Automotive Systems, used by Audi", "00:1C:D7"},
		{"Harman/Becker Automotive Systems GmbH", "9C:DF:03"},
		{"Continental Automotive Systems", "00:1E:AE"},
		{"Continental Automotive Systems", "00:54:AF"},
		{"Bosch Automotive Aftermarket", "70:C6:AC"},
		{"Continental Automotive Czech Republic s.r.o.", "9C:28:BF"},
		{"Robert Bosch LLC Automotive Electronics", "D0:B4:98"},
		{"Panasonic Automotive Systems Company of America", "E0:EE:1B"},
		{"QCOM Technology Inc (Reporting as InCar Hotspot, probably Daimler)", "00:0D:F0"},
		{"LessWire AG","00:06:C6"},
		{"Wistron Neweb Corp.","00:0B:6B"}, // device reporting as ssid Moovbox
		// mobile devices
		{"Murata Manufacturing Co., Ltd., used on some LG devices", "44:A7:CF"},
		{"Murata Manufacturing Co., Ltd., used in some mobile devices", "40:F3:08"},
		{"LG Electronics, used in Nexus 4",	"10:68:3F"},
		{"Apple", "00:26:B0"},
		{"Apple", "00:26:BB"},
		{"Apple Computer Inc.", "00:19:E3"},
		{"Apple", "00:25:00"},
		{"Apple", "00:26:4A"},
		{"Apple", "00:C6:10"},
		
		// Sony Mobile
		{"00:0A:D9","Sony Ericsson Mobile Communications AB"},
		{"00:0E:07","Sony Ericsson Mobile Communications AB"},
		{"00:0F:DE","Sony Ericsson Mobile Communications AB"},
		{"00:12:EE","Sony Ericsson Mobile Communications AB"},
		{"00:16:20","Sony Ericsson Mobile Communications AB"},
		{"00:16:B8","Sony Ericsson Mobile Communications"},
		{"00:18:13","Sony Ericsson Mobile Communications"},
		{"00:19:63","Sony Ericsson Mobile Communications AB"},
		{"00:1A:75","Sony Ericsson Mobile Communications"},
		{"00:1B:59","Sony Ericsson Mobile Communications AB"},
		{"00:1C:A4","Sony Ericsson Mobile Communications"},
		{"00:1D:28","Sony Ericsson Mobile Communications AB"},
		{"00:1E:45","Sony Ericsson Mobile Communications AB"},
		{"00:1F:E4","Sony Ericsson Mobile Communications"},
		{"00:21:9E","Sony Ericsson Mobile Communications"},
		{"00:22:98","Sony Ericsson Mobile Communications"},
		{"00:23:45","Sony Ericsson Mobile Communications"},
		{"00:23:F1","Sony Ericsson Mobile Communications"},
		{"00:24:EF","Sony Ericsson Mobile Communications"},
		{"00:25:E7","Sony Ericsson Mobile Communications"},
		{"00:EB:2D","Sony Mobile Communications AB"},
		{"18:00:2D","Sony Mobile Communications AB"},
		{"1C:7B:21","Sony Mobile Communications AB"},
		{"20:54:76","Sony Mobile Communications AB"},
		{"24:21:AB","Sony Ericsson Mobile Communications"},
		{"30:17:C8","Sony Ericsson Mobile Communications AB"},
		{"30:39:26","Sony Ericsson Mobile Communications AB"},
		{"40:2B:A1","Sony Ericsson Mobile Communications AB"},
		{"4C:21:D0","Sony Mobile Communications AB"},
		{"58:17:0C","Sony Ericsson Mobile Communications AB"},
		{"5C:B5:24","Sony Ericsson Mobile Communications AB"},
		{"68:76:4F","Sony Mobile Communications AB"},
		{"6C:0E:0D","Sony Ericsson Mobile Communications AB"},
		{"6C:23:B9","Sony Ericsson Mobile Communications AB"},
		{"84:00:D2","Sony Ericsson Mobile Communications AB"},
		{"8C:64:22","Sony Ericsson Mobile Communications AB"},
		{"90:C1:15","Sony Ericsson Mobile Communications AB"},
		{"94:CE:2C","Sony Mobile Communications AB"},
		{"B4:52:7D","Sony Mobile Communications AB"},
		{"B4:52:7E","Sony Mobile Communications AB"},
		{"B8:F9:34","Sony Ericsson Mobile Communications AB"},
		{"D0:51:62","Sony Mobile Communications AB"},
		{"E0:63:E5","Sony Mobile Communications AB"},

		// mostly devices reporting ssid AndroidAP
		{"Samsung Electronics Co.", "8C:77:12"},
		{"Samsung Electronics Co.", "9C:E6:E7"},
		{"HTC Corporation", "A8:26:D9"},
		{"Longcheer Technology (Singapore) Pte Ltd", "00:23:B1"},
		{"Samsung Electronics Co.", "D0:C1:B1"},
		{"EQUIP'TRANS", "00:01:00"},
		{"CyberTAN Technology", "00:01:36"},
		{"PORTech Communications", "00:03:7E"},
		{"Atheros Communications", "00:03:7F"},
		{"Adax", "00:07:10"},
		{"Samsung Electronics Co.", "00:07:AB"},
		{"Qisda Corporation", "00:17:CA"},
		{"UNIGRAND LTD", "00:18:00"},
		{"SIM Technology Group Shanghai Simcom Ltd.", "00:18:60"},
		{"Cameo Communications", "00:18:E7"},
		{"Intelliverese - DBA Voicecom", "00:19:00"},
		{"YuHua TelTech (ShangHai) Co.", "00:19:65"},
		{"Hon Hai Precision Ind. Co.", "00:19:7D"},
		{"Panasonic Mobile Communications Co.", "00:19:87"},
		{"VIZIO", "00:19:9D"},
		{"Boundary Devices", "00:19:B8"},
		{"MICRO-STAR INTERNATIONAL CO.", "00:19:DB"},
		{"FusionDynamic Ltd.", "00:1A:91"},
		{"ASUSTek COMPUTER INC.", "00:1A:92"},
		{"Hisense Mobile Communications Technoligy Co.", "00:1A:95"},
		{"ECLER S.A.", "00:1A:96"},
		{"Asotel Communication Limited Taiwan Branch", "00:1A:98"},
		{"Smarty (HZ) Information Electronics Co.", "00:1A:99"},
		{"ShenZhen Kang Hui Technology Co.", "00:1B:10"},
		{"Nintendo Co.", "00:1B:EA"},
		{"Hon Hai Precision Ind. Co.", "00:1C:26"},
		{"AirTies Wireless Networks", "00:1C:A8"},
		{"Shenzhen Sang Fei Consumer Communications Co.", "00:1D:07"},
		{"ARRIS Group", "00:1D:D0"},
		{"Samsung Electronics Co.", "00:1D:F6"},
		{"Palm", "00:1D:FE"},
		{"ShenZhen Huawei Communication Technologies Co.", "00:1E:10"},
		{"Hon Hai Precision Ind.Co.", "00:1E:4C"},
		{"Wingtech Group Limited", "00:1E:AD"},
		{"Edimax Technology Co. Ltd.", "00:1f:1f"},
		{"Hon Hai Precision Ind. Co.", "00:1F:E1"},
		{"LEXMARK INTERNATIONAL", "00:20:00"},
		{"JEOL SYSTEM TECHNOLOGY CO. LTD", "00:20:10"},
		{"Samsung Electro-Mechanics", "00:21:19"},
		{"Motorola Mobility", "00:21:36"},
		{"Murata Manufacturing Co.", "00:21:E8"},
		{"SMT C Co.", "00:22:31"},
		{"Digicable Network India Pvt. Ltd.", "00:22:5D"},
		{"Liteon Technology Corporation", "00:22:5F"},
		{"Hewlett-Packard Company", "00:22:64"},
		{"ZTE Corporation", "00:22:93"},
		{"Kyocera Corporation", "00:22:94"},
		{"Nintendo Co.", "00:22:D7"},
		{"AMPAK Technology", "00:22:F4"},
		{"Arcadyan Technology Corporation", "00:23:08"},
		{"LNC Technology Co.", "00:23:10"},
		{"Hon Hai Precision Ind. Co.", "00:23:4D"},
		{"HTC Corporation", "00:23:76"},
		{"AzureWave Technologies (Shanghai) Inc.", "00:24:23"},
		{"Onda Communication spa", "00:24:6F"},
		{"Universal Global Scientific Industrial Co.", "00:24:7E"},
		{"Sony Computer Entertainment Inc.", "00:24:8D"},
		{"ASRock Incorporation", "00:25:22"},
		{"Hon Hai Precision Ind. Co.", "00:25:56"},
		{"Microsoft Corporation", "00:25:AE"},
		{"TEAC Australia Pty Ltd.", "00:26:00"},
		{"Samsung Electro-Mechanics", "00:26:37"},
		{"Trapeze Networks", "00:26:3E"},
		{"Hon Hai Precision Ind. Co.", "00:26:5C"},
		{"Murata Manufacturing Co.", "00:26:E8"},
		{"Universal Global Scientific Industrial Co.", "00:27:13"},
		{"Rebound Telecom. Co.", "00:27:15"},
		{"Murata Manufacturing Co.", "00:37:6D"},
		{"EFM Networks", "00:08:9F"},
		{"TwinHan Technology Co.", "00:08:CA"},
		{"Simple Access Inc.", "00:09:10"},
		{"Shenzhen Tp-Link Technology Co Ltd.", "00:0A:EB"},
		{"Airgo Networks", "00:0A:F5"},
		{"AXIS COMMUNICATIONS AB", "00:40:8C"},
		{"MARVELL SEMICONDUCTOR", "00:50:43"},
		{"AIWA CO.", "00:50:71"},
		{"CORVIS CORPORATION", "00:50:72"},
		{"IEEE REGISTRATION AUTHORITY", "00:50:C2"},
		{"EPIGRAM", "00:90:4C"},
		{"CYBERTAN TECHNOLOGY", "00:90:A2"},
		{"K.J. LAW ENGINEERS", "00:90:C0"},
		{"THE APPCON GROUP", "00:A0:6F"},
		{"MITSUMI ELECTRIC CO.", "00:A0:96"},
		{"LG Electronics ", "00:AA:70"},
		{"ALFA", "00:C0:CA"},
		{"KYOCERA CORPORATION", "00:C0:EE"},
		{"AMIGO TECHNOLOGY CO.", "00:D0:41"},
		{"REALTEK SEMICONDUCTOR CORP.", "00:E0:4C"},
		{"KODAI HITEC CO.", "00:E0:54"},
		{"MATSUSHITA KOTOBUKI ELECTRONICS INDUSTRIES", "00:E0:5C"},
		{"HUAWEI TECHNOLOGIES CO.", "00:E0:FC"},
		{"Traverse Technologies Australia", "00:0A:FA"},
		{"Lumenera Corporation", "00:0B:E2"},
		{"Ralink Technology", "00:0C:43"},
		{"Cooper Industries Inc.", "00:0C:C1"},
		{"Arima Communication Corporation", "00:0D:92"},
		{"Advantech AMT Inc.", "00:0E:02"},
		{"CTS electronics", "00:0E:72"},
		{"zioncom", "00:0E:E8"},
		{"CIMSYS Inc", "00:11:22"},
		{"Chi Mei Communication Systems", "00:11:94"},
		{"TiVo", "00:11:D9"},
		{"ASKEY COMPUTER CORP.", "00:11:F5"},
		{"Camille Bauer", "00:12:34"},
		{"ConSentry Networks", "00:12:36"},
		{"Samsung Electronics Co.", "00:12:47"},
		{"Lenovo Mobile Communication Technology Ltd.", "00:12:FE"},
		{"GuangZhou Post Telecom Equipment ltd", "00:13:13"},
		{"AMOD Technology Co.", "00:13:F1"},
		{"Motorola Mobility", "00:14:9A"},
		{"Gemtek Technology Co.", "00:14:A5"},
		{"Intel Corporate", "00:15:00"},
		{"Actiontec Electronics", "00:15:05"},
		{"LibreStream Technologies Inc.", "00:16:13"},
		{"Sunhillo Corporation", "00:16:43"},
		{"TPS", "00:16:6A"},
		{"Yulong Computer Telecommunication Scientific Co.", "00:16:6D"},
		{"Dovado FZ-LLC", "00:16:A6"},
		{"Compal Communications", "00:16:D4"},
		{"ARCHOS", "00:16:DC"},
		{"Methode Electronics", "00:17:05"},
		{"YOSIN ELECTRONICS CO.", "00:17:A6"},
		{"SK Telesys", "00:17:B2"},
		{"KTF Technologies Inc.", "00:17:C3"},
	};

	public static void run(final String filename) {
		File folder = new File(filename.substring(1, filename.lastIndexOf(File.separator)));
		boolean folderAccessible = false;
		if (folder.exists() && folder.canWrite()) {
			folderAccessible = true;
		}

		if (!folder.exists()) {
			Log.i(TAG, "Folder missing: create " + folder.getAbsolutePath());
			folderAccessible = folder.mkdirs();
		}

		if (folderAccessible) {
			StringBuilder sb = new StringBuilder();
			sb.append(FILE_OPEN);
			for (String[] prefix : PREFIXES) {
				sb.append(PREFIX_OPEN + prefix[0] + PREFIX_MIDDLE + prefix[1] + PREFIX_CLOSE);
			}

			for (String[] address : ADDRESSES) {
				sb.append(ADDRESS_OPEN + address[0] + ADDRESS_MIDDLE + address[1] + ADDRESS_CLOSE);
			}

			sb.append(FILE_CLOSE);

			try {
				File file = new File(filename);
				BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
				bw.append(sb);
				bw.close();
				Log.i(TAG, "Created default blacklist, " + ADDRESSES.length + " entries");
			} catch (IOException e) {
				Log.e(TAG, "Error writing blacklist");
			} 
		} else {
			Log.e(TAG, "Folder not accessible: can't write blacklist");
		}

	}

	private BssidBlackListBootstraper() {

	}
}
