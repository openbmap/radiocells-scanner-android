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

package org.openbmap.utils;

public class MapDownload {
    private String mUpdated;
    private String mTitle;
    private String mRegion;
    private String mUrl;
    private String mId;


    public MapDownload(String title, String region, String url, String id, String updated) {
        setTitle(title);
        setRegion(region);
        setUrl(url);
        setId(id);
        setUpdated(updated);
    }

    public String getUpdated() {
        return mUpdated;
    }

    public void setUpdated(String mUpdated) {
        this.mUpdated = mUpdated;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public String getRegion() {
        return mRegion;
    }

    public void setRegion(String mRegion) {
        this.mRegion = mRegion;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String mUrl) {
        this.mUrl = mUrl;
    }

    public String getId() {
        return mId;
    }

    public void setId(String mId) {
        this.mId = mId;
    }
}
