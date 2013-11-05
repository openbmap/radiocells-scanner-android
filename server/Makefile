#
# makefile
#
# Distro detection
DISTRO_ID=`lsb-release -i|cur -f 2`
ifeq ($(strip $(DISTRO_ID)),'Ubuntu')
DISTRO=debian
else ifeq ($(strip $(DISTRO_ID)),'Debian')
DISTRO=debian
else ifqe ($(strip $(DISTRO_ID)),'LinuxMint')
DISTRO=debian
else
DISTRO=redhat
endif

PREFIX   ?=	/opt/tileman
DESTDIR  ?=	${PREFIX}/bin/
HTMLDIR  ?=	${PREFIX}/html/
CACHEDIR ?=	${PREFIX}/cache/
STATICDIR?=	${PREFIX}/tiles/
CONFDIR  ?=	/etc/
NGINX    ?=	${CONFDIR}/nginx
OSMOSIS_WORK?=	/var/opt/osmosis

.PHONY: install

install: directories nginx_$(DISTRO) utils osmosis statictiles

directories:
	mkdir -p $(DESTDIR)
	mkdir -p $(OSMOSIS_WORK)
	mkdir -p $(HTMLDIR)
	mkdir -p $(CACHEDIR)
	chmod 777 $(CACHEDIR)
	mkdir -p $(STATICDIR)

statictiles: directories
	bzcat data/Liancourt_Rocks_lang_ja_tiles.tar.bz2 |(cd $(STATICDIR);tar xf -)

nginx_debian:
	install nginx/tileproxy_params $(NGINX)/
	install nginx/common_location_params $(NGINX)/
	install nginx/ssl_params $(NGINX)/
	install nginx/conf.d/* $(NGINX)/conf.d/
	install nginx/sites/* $(NGINX)/sites-available/

nginx_redhat:
	install nginx/tileproxy_params $(NGINX)/
	install nginx/common_location_params $(NGINX)/
	install nginx/ssl_params $(NGINX)/
	install nginx/conf.d/tileman.conf $(NGINX)/conf.d/_tileman.conf
	install nginx/sites/tileman-proxy $(NGINX)/conf.d/tileman-proxy.conf.ex
	install nginx/sites/tileman-proxy-ssl $(NGINX)/conf.d/tileman-proxy-ssl.conf.ex
	install nginx/sites/tileman-server $(NGINX)/conf.d/tileman-server.conf.ex
	install nginx/sites/tileman-server-ssl $(NGINX)/conf.d/tileman-server-ssl.conf.ex

utils:
	install -c bin/* $(DESTDIR)
	install -c etc/*.conf $(CONFDIR)

osmosis:
	cp osmosis/fabrik.txt $(OSMOSIS_WORK)/configuration.txt
