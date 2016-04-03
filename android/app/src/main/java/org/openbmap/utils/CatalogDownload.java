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

public class CatalogDownload {
    private String mUpdated;
    private String mTitle;
    private String mRegion;
    private String mUrl;
    private String mId;


    public CatalogDownload(String title, String region, String url, String id, String updated) {
        setTitle(title);
        setRegion(region);
        setUrl(url);
        setId(id);
        setUpdated(updated);
    }

    public String getUpdated() {
        return mUpdated;
    }

    public void setUpdated(String updated) {
        this.mUpdated = updated;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public String getRegion() {
        return mRegion;
    }

    public void setRegion(String region) {
        this.mRegion = region;
    }

    /**
     * Returns catalog's download url relative to server base
     * e.g. /static/us.sqlite
     * @return
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Set catalog's download url (relative to server base)
     * @param url
     */
    public void setUrl(String url) {
        this.mUrl = url;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        this.mId = id;
    }
}
