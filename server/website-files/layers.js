var map = new OpenLayers.Map({
    div: "map",
    projection: "EPSG:900913",
    layers: [
        new OpenLayers.Layer.XYZ(
            "Local Tile Server", 
            [
                "http://localhost:8080/${z}/${x}/${y}.png"
            ],
            {
                attribution: "All data, imagery and map information provided by your local tile server, <a href='http://www.openstreetmap.org/' target='_blank'>Open Street Map</a> and contributors, <a href='http://opendatacommons.org/licenses/odbl/' target='_blank'>Open Database License (ODbL)</a>",
                transitionEffect: "resize"
            }
        ),
        new OpenLayers.Layer.XYZ(
            "MapQuest", 
             [
                "http://otile1.mqcdn.com/tiles/1.0.0/hyb/${z}/${x}/${y}.png",
                "http://otile2.mqcdn.com/tiles/1.0.0/hyb/${z}/${x}/${y}.png",
                "http://otile3.mqcdn.com/tiles/1.0.0/hyb/${z}/${x}/${y}.png",
                "http://otile4.mqcdn.com/tiles/1.0.0/hyb/${z}/${x}/${y}.png"
            ],
            {
                attribution: "Map Data &copy; <a href='http://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors. Tiles Courtesy of <a href='http://www.mapquest.com/' target='_blank'>MapQuest</a>",
                transitionEffect: "resize",
                visibility: false,
                isBaseLayer: false
            }
        ),
        new OpenLayers.Layer.XYZ(
            "Openbmap Wifis",
            [
                "http://localhost:8081/${z}/${x}/${y}.png"
            ],
            {
                attribution: "WiFi Data &copy; <a href='http://www.openbmap.org'>OpenBMap</a> contributors, licensed under <a href='http://openbmap.org/openBmap-wifi-odbl-10.txt' target='_blank'</a>",
                transitionEffect: "resize",
                isBaseLayer: false
            }
        )
    ]
});

// set map center to Paris
 map.setCenter(new OpenLayers.LonLat(2.3177, 48.8580)
          .transform(
            new OpenLayers.Projection("EPSG:4326"), // transform from WGS 1984
            new OpenLayers.Projection("EPSG:900913") // to Spherical Mercator Projection
          ), 10 // Zoom level
        );
map.addControl(new OpenLayers.Control.LayerSwitcher());
