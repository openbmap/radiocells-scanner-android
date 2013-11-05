#!/usr/bin/env bash

useradd osm

export DEBIAN_FRONTEND=noninteractive

apt-get update

# add osmjapan PPA repository
apt-get install -y python-software-properties
apt-add-repository -y ppa:osmjapan/ppa
#apt-add-repository -y ppa:osmjapan/testing
apt-add-repository -y ppa:miurahr/openstreetmap
apt-get update

# install nginx/openresty
apt-get install -y nginx-openresty
#apt-get install -y nginx-extras # > 1.4.1-0ppa1

# install mapnik
apt-get install -y libmapnik-dev
apt-get install -y ttf-unifont ttf-dejavu ttf-dejavu-core ttf-dejavu-extra

# !dirty.. create sym-link, so we have an folder with all fonts
ln -s /usr/share/fonts/truetype/unifont/unifont.ttf /usr/share/fonts/truetype/ttf-dejavu/

# default locale will be taken from user locale so we set locale to UTF8
sudo update-locale LANG=en_US.UTF-8
sudo update-locale LC_ALL="en_US.UTF-8"
export LANG=en_US.UTF-8
export LC_ALL="en_US.UTF-8"

# install postgis
apt-get install -y postgresql-9.1 postgresql-contrib-9.1 postgresql-9.1-postgis
# enable local trust and remote connections
sed -i 's/^local\s\+all\s\+all\s\+peer/local all all trust/g'  /etc/postgresql/9.1/main/pg_hba.conf 
sed -i 's/^\#listen_addresses = \x27localhost\x27/listen_addresses = \x27*\x27/g'  /etc/postgresql/9.1/main/postgresql.conf
/etc/init.d/postgresql restart

# install osm2pgsql
apt-get install -y --force-yes -o openstreetmap-postgis-db-setup::initdb=gis -o openstreetmap-postgis-db-setup::dbname=gis -o openstreetmap-postgis-db-setup::grant_user=osm openstreetmap-postgis-db-setup osm2pgsql

# install Tirex
apt-get install -y tirex-core tirex-backend-mapnik tirex-example-map
# remove default stuff
rm /etc/tirex/renderer/mapnik/example.conf
rm /etc/tirex/renderer/test.conf
rm -r /etc/tirex/renderer/test/

cp /vagrant/etc/tirex/tirex.conf /etc/tirex
cp /vagrant/etc/tirex/renderer/mapnik.conf /etc/tirex/renderer
cp /vagrant/etc/tirex/renderer/mapnik/base.conf /etc/tirex/renderer/mapnik/
cp /vagrant/etc/tirex/renderer/mapnik/wifis.conf /etc/tirex/renderer/mapnik/
rm -r /var/lib/tirex/tiles/example/
rm -r /var/lib/tirex/tiles/test/
mkdir -p /var/lib/tirex/tiles/base
mkdir -p /var/lib/tirex/tiles/wifis
chown -R tirex:tirex /var/lib/tirex/tiles/base
chown -R tirex:tirex /var/lib/tirex/tiles/wifis

# copy style files 
rm -r /usr/share/tirex/example-map
cp -r /vagrant/usr/share/tirex/example-map/ /usr/share/tirex/
chown -R tirex:tirex /usr/share/tirex/*

# get coastlines
cd /usr/share/tirex/example-map/
./get_coastlines.sh

# copy openbmap.sqlite
cp /vagrant/root/openbmap.sqlite /usr/share/tirex/example-map/

# fixing permissions
chmod -R 644 /usr/share/tirex/example-map/
chmod +x /usr/share/tirex/example-map/
chmod +x /usr/share/tirex/example-map/inc
chmod +x /usr/share/tirex/example-map/symbols
chmod +x /usr/share/tirex/example-map/wifi_symbols
chmod +x /usr/share/tirex/example-map/world_boundaries


# install Lua OSM library
apt-get install -y geoip-database lua5.1 lua-bitop
apt-get install -y lua-nginx-osm

# create missing folders
cd /vagrant
make

# for unknown reasons above make fails, so let's create the folders manually..
mkdir -p /opt/tileman/bin/
mkdir -p /var/opt/osmosis
mkdir -p /opt/tileman/html/
mkdir -p /opt/tileman/cache/
chmod 777 /opt/tileman/cache/
mkdir -p /opt/tileman/tiles/



# prepare nginx
rm /etc/nginx/sites-enabled/default
cp /vagrant/etc/nginx/sites-available/base-server /etc/nginx/sites-available/
cp /vagrant/etc/nginx/sites-available/wifis-server /etc/nginx/sites-available/
ln -s /vagrant/etc/nginx/sites-available/base-server /etc/nginx/sites-enabled/base-server
ln -s /vagrant/etc/nginx/sites-available/wifis-server /etc/nginx/sites-enabled/wifis-server

# install osmosis
apt-get install -y openjdk-7-jre
cd /tmp
if [ -f osmosis-latest.tgz ]; then
wget http://bretth.dev.openstreetmap.org/osmosis-build/osmosis-latest.tgz
fi
mkdir -p /opt/osmosis
cd /opt/osmosis;tar zxf /tmp/osmosis-latest.tgz
mkdir -p /var/opt/osmosis
chown osm /var/opt/osmosis

# install tileman package
apt-get install -y tileman

# adjust tileman settings (selected map etc.)
cp /vagrant/etc/tileman.conf /etc/

# development dependencies
apt-get install -y devscripts debhelper dh-autoreconf build-essential git
apt-get install -y libfreexl-dev libgdal-dev python-gdal gdal-bin
apt-get install -y libxml2-dev python-libxml2 libsvg

# install Redis-server
apt-get install -y redis-server

# setup postgis database
su postgres -c /usr/bin/tileman-create
su postgres -c "psql -d gis -c 'CREATE EXTENSION IF NOT EXISTS hstore;'"

# restart servers
/etc/init.d/tirex-master restart
/etc/init.d/tirex-backend-manager restart
/etc/init.d/nginx restart

# default test data is taiwan (about 16MB by .pbf)
#echo  COUNTRY=taiwan >> /etc/tileman.conf
(cd /tmp;su osm -c /usr/bin/tileman-load)