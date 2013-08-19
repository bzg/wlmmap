var map = L.map('map').setView([51.505, -0.09], 13);

L.tileLayer('http://{s}.tile.cloudmade.com/3068c9a9c9b648cb910837cf3c5fce10/997/256/{z}/{x}/{y}.png', {
    attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://cloudmade.com">CloudMade</a>',
    maxZoom: 18
}).addTo(map);

// var marker = L.marker([51.5, -0.09]).addTo(map);

function onMapClick(e) {
    // alert("You clicked the map at " + e.latlng);
    document.getElementById('lat').value = e.latlng.lat;
    document.getElementById('lng').value = e.latlng.lng;
}

map.on('click', onMapClick);
