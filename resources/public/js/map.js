var map = L.map('map').setView([48, 1.2], 2);

L.tileLayer('http://{s}.tile.cloudmade.com/3068c9a9c9b648cb910837cf3c5fce10/997/256/{z}/{x}/{y}.png', {
    attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://cloudmade.com">CloudMade</a>',
    maxZoom: 18
}).addTo(map);

var markers = L.markerClusterGroup();
		
for (var i = 0; i < addressPoints.length; i++) {
    var a = addressPoints[i];
    var name = a[2];
    var img = a[3];
    var wp = a[4];
    var legend = "<h1>" + name + "</h1>" + img + "<br/>" + wp;
    var marker = L.marker(new L.LatLng(a[0], a[1]), { title: legend });
    marker.bindPopup(legend);
    markers.addLayer(marker);
}
map.addLayer(markers);

// function onMapClick(e) {
//     // alert("You clicked the map at " + e.latlng);
//     document.getElementById('lat').value = e.latlng.lat;
//     document.getElementById('lng').value = e.latlng.lng;
// }

// map.on('moveend', function(e) {
//     console.log("\nmap was panned!");
//     // alert("Rien");
//     // location.reload();
// });

// map.on('click', onMapClick);
