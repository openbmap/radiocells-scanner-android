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


package org.openbmap.services.wireless.blacklists;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Creates initial wifi blacklist with some default entries
 */
public final class SsidBlackListBootstraper {

	private static final String TAG= SsidBlackListBootstraper.class.getSimpleName();

	/**
	 * XML opening tag prefix
	 */
	private static final String PREFIX_OPEN= "<prefix comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String PREFIX_MIDDLE = "\">";

	/**
	 * XML closing tag prefix
	 */
	private static final String PREFIX_CLOSE = "</prefix>";

	/**
	 * XML opening tag prefix
	 */
	private static final String SUFFIX_OPEN= "<suffix comment=\"";

	/**
	 * XML middle tag prefix
	 */
	private static final String SUFFIX_MIDDLE = "\">";

	/**
	 * XML closing tag prefix
	 */
	private static final String SUFFIX_CLOSE = "</suffix>";

	/**
	 * 
	 */
	private static final String START_TAG= "<ignorelist>";

	/**
	 * 
	 */
	private static final String END_TAG= "</ignorelist>";

	private static final String[][] PREFIXES = {
		{"default", "ASUS"},
		{"default", "Android Barnacle Wifi Tether"},
		{"default", "AndroidAP"},
		{"default", "AndroidTether"},
		{"default", "blackberry mobile hotspot"},
		{"default", "Clear Spot"},
		{"default", "ClearSpot"},
		{"default", "docomo"},
		{"Maintenance network on German ICE trains", "dr_I)p"},	
		{"default", "Galaxy Note"},
		{"default", "Galaxy S"},
		{"default", "Galaxy Tab"},
		{"default", "HelloMoto"},
		{"default", "HTC "},
		{"default", "iDockUSA"},
		{"default", "iHub_"},
		{"default", "iPad"},
		{"default", "ipad"},
		{"default", "iPhone"},
		{"default", "LG VS910 4G"},
		{"default", "MIFI"},
		{"default", "MiFi"},
		{"default", "mifi"},
		{"default", "MOBILE"},
		{"default", "Mobile"},
		{"default", "mobile"},
		{"default", "myLGNet"},
		{"default", "myTouch 4G Hotspot"},
		{"default", "PhoneAP"},
		{"default", "SAMSUNG"},
		{"default", "Samsung"},
		{"default", "Sprint"},
		{"Long haul buses", "megabus-wifi"},
		{"German long haul buses", "DeinBus"},
		{"German long haul buses", "MeinFernbus"},
		{"German long haul buses", "adac_postbus"},
		{"German long haul buses", "flixbus"},
		{"Long haul buses", "eurolines"},	
		{"Long haul buses", "ecolines"},
		{"Hurtigen lines", "guest@MS"},
		{"Hurtigen lines", "admin@MS"},
		{"German fast trains", "Telekom_ICE"},
		{"European fast trains", "thalysnet"},
		{"default", "Trimble "},
		{"default", "Verizon"},
		{"default", "VirginMobile"},
		{"default", "VTA Free Wi-Fi"},
		{"default", "webOS Network"},
		{"GoPro cams", "goprohero3"},
		{"Swiss Post Auto Wifi", "PostAuto"},
		{"Swiss Post Auto Wifi French", "CarPostal"},
		{"Swiss Post Auto Wifi Italian", "AutoPostale"},
		{"Huawei Smartphones", "Huawei"},
		{"Huawei Smartphones", "huawei"},
		{"Xiaomi Smartphones", "紅米手機"},
		
		// mobile hotspots
		{"German 1und1 mobile hotspots", "1und1 mobile"},
		{"xperia tablet", "xperia tablet"},
        {"Sony devices", "XPERIA"},
		{"xperia tablet", "androidhotspot"},
        {"HP laptops", "HP envy"},
		{"empty ssid (not really hidden, just not broadcasting..)", ""},


		// some ssids from our friends at https://github.com/dougt/MozStumbler
		{"default", "ac_transit_wifi_bus"},
		{"Nazareen express transportation services (Israel)", "Afifi"},
		{"Oslo airport express train on-train WiFi (Norway)","AirportExpressZone"}, 
		{"default", "AmtrakConnect"},
		{"default", "amtrak_"},
		{"Arriva Nederland on-train Wifi (Netherlands)", "arriva"}, 
		{"Arcticbus on-bus WiFi (Sweden)","Arcticbus Wifi"},
		{"Swiss municipial busses on-bus WiFi (Italian speaking part)","AutoPostale"},
		{"Barcelona tourisitic buses http://barcelonabusturistic.cat","Barcelona Bus Turistic "},
		{"Tromso on-boat (and probably bus) WiFi (Norway)"	,"Boreal_Kundenett"},
		{"Bus4You on-bus WiFi (Norway)","Bus4You-"},
		{"Capital Bus on-bus WiFi (Taiwan)", "CapitalBus"}, 
		{"Swiss municipial busses on-bus WiFi (French speaking part)" ,"CarPostal"},
		{"Ceske drahy (Czech railways)", "CDWiFi"},
		{"Copenhagen S-Tog on-train WiFi: http://www.dsb.dk/s-tog/kampagner/fri-internet-i-s-tog" ,"CommuteNet"},
		{"CSAD Plzen","csadplzen_bus"},
		{"Egged transportation services (Israel)", "egged.co.il"}, 
		{"Empresa municipal de transportes de Madrid","EMT-Madrid"}, 
		{"First Bus wifi (United Kingdom)","first-wifi"},
		{"Oslo airport transportation on-bus WiFi (Norway)" ,"Flybussekspressen"},
		{"Airport transportation on-bus WiFi all over Norway (Norway)" ,"Flybussen"},
		{"Flygbussarna.se on-bus WiFi (Sweden)"	,"Flygbussarna Free WiFi"},
		{"GB Tours transportation services (Israel)", "gb-tours.com"},
		{"default", "GBUS"},
		{"default", "GBusWifi"},
		{"Gogo in-flight WiFi", "gogoinflight"},
		{"Koleje Slaskie transportation services (Poland)" ,"Hot-Spot-KS"},
		{"ISRAEL-RAILWAYS","ISRAEL-RAILWAYS"},
		{"Stavanger public transport on-boat WiFi (Norway)"	,"Kolumbus"},
		{"Kystbussen on-bus WiFi (Norway)" ,"Kystbussen_Kundennett"},
		{"Hungarian State Railways onboard hotspot on InterCity trains (Hungary)", "MAVSTART-WiFi"}, 
		{"Nateev Express transportation services (Israel)"	,"Nateev-WiFi"},
		{"National Express on-bus WiFi (United Kingdom)" ,"NationalExpress"},
		{"Norgesbuss on-bus WiFi (Norway)"	,"Norgesbuss"},
		{"Norwegian in-flight WiFi (Norway)" ,"Norwegian Internet Access"},
		{"NSB on-train WiFi (Norway)"	,"NSB_INTERAKTIV"},
		{"Omnibus transportation services (Israel)", "Omni-WiFi"},
		{"OnniBus.com Oy on-bus WiFi (Finland)"	,"onnibus.com"},
		 {"Oxford Tube on-bus WiFi (United Kindom)" ,"Oxford Tube"},
		 {"Swiss municipial busses on-bus WiFi (German speaking part)" ,"PostAuto"},
		{"Qbuzz on-bus WiFi (Netherlands)", "QbuzzWIFI"},
		{"default", "SF Shuttle Wireless"},
		{"default", "ShuttleWiFi"},
		
		{"Southwest Airlines in-flight WiFi",  "Southwest WiFi"},
		{"default", "SST-PR-1"}, // Sears Home Service van hotspot?!
		{"Stagecoach on-bus WiFi (United Kingdom)" ,"stagecoach-wifi"},
		 
		{"Taipei City on-bus WiFi (Taiwan)", "TPE-Free Bus"},
		{"Taipei City on-bus WiFi (Taiwan)", "NewTaipeiBusWiFi"},
		{"Taiwan transport on-bus WiFi (Taiwan)", "Y5Bus_4G"},
		{"Taiwan transport on-bus WiFi (Taiwan)", "Y5Bus_LTE"},
        {"(Taiwan) Taoyuan MRT", "TyMetro"},
		{"Taiwan High Speed Rail on-train WiFi", "THSR-VeeTIME"},
		{"Triangle Transit on-bus WiFi"	,"TriangleTransitWiFi_"},
		{"Nederlandse Spoorwegen on-train WiFi by T-Mobile (Netherlands)", "tmobile"},
		{"Triangle Transit on-bus WiFi","TriangleTransitWiFi_"}, 
		{"VR on-train WiFi (Finland)", "VR-junaverkko"},
		{"Boreal on-bus WiFi (Norway)" ,"wifi@boreal.no"},
		{"Nettbuss on-bus WiFi (Norway)", "wifi@nettbuss.no"},
		{"BART", "wifi_rail"}
	};

	private static final String[][] SUFFIXES = {
		{"default", "MacBook"},
		{"default", "MacBook Pro"},
		{"default", "MiFi"},
		{"default", "MyWi"},
		{"default", "Tether"},
		{"default", "iPad"},
		{"default", "iPhone"},
		{"default", "ipad"},
		{"default", "iphone"},
		{"default", "tether"},
		{"default", "adhoc"},
		{"Google's SSID opt-out", "_nomap"}
	};



	public static void run(final String filename) {
		final File folder = new File(filename.substring(1, filename.lastIndexOf(File.separator)));
		boolean folderAccessible = false;
		if (folder.exists() && folder.canWrite()) {
			folderAccessible = true;
		}

		if (!folder.exists()) {
			Log.i(TAG, "Folder missing: create " + folder.getAbsolutePath());
			folderAccessible = folder.mkdirs();
		}

		if (folderAccessible) {
			final StringBuilder sb = new StringBuilder();
			sb.append(START_TAG);
			for (final String[] prefix : PREFIXES) {
				sb.append(PREFIX_OPEN).append(prefix[0]).append(PREFIX_MIDDLE).append(prefix[1]).append(PREFIX_CLOSE);
			}

			for (final String[] suffix : SUFFIXES) {
				sb.append(SUFFIX_OPEN).append(suffix[0]).append(SUFFIX_MIDDLE).append(suffix[1]).append(SUFFIX_CLOSE);
			}
			sb.append(END_TAG);

			try {
				final File file = new File(filename);
				final BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
				bw.append(sb);
				bw.close();
				Log.i(TAG, "Created default blacklist, " + PREFIXES.length + SUFFIXES.length + " entries");
			} catch (final IOException e) {
				Log.e(TAG, "Error writing blacklist");
			} 
		} else {
			Log.e(TAG, "Folder not accessible: can't write blacklist");
		}

	}

	private SsidBlackListBootstraper() {
	}
}
