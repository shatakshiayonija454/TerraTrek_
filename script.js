// Patiala city centre (map's starting position; this is not a live GPS reading).
const PATIALA = [30.3398, 76.3869];

const map = L.map("map").setView(PATIALA, 13);
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(map);

let watchId = null;
let userMarker = null;
let accuracyCircle = null;
let routeLine = L.polyline([], { color: "#16866d", weight: 4, opacity: 0.75 }).addTo(map);

const $ = (id) => document.getElementById(id);
const startBtn = $("startBtn");
const stopBtn = $("stopBtn");

function setStatus(active, title, message) {
  $("statusDot").classList.toggle("active", active);
  $("statusText").textContent = title;
  $("message").textContent = message;
}

function showPosition(position) {
  const { latitude, longitude, accuracy } = position.coords;
  const point = [latitude, longitude];

  $("lat").textContent = latitude.toFixed(6);
  $("lng").textContent = longitude.toFixed(6);
  $("accuracy").textContent = `± ${Math.round(accuracy)} metres`;
  $("updated").textContent = new Date(position.timestamp).toLocaleTimeString();

  if (!userMarker) {
    userMarker = L.marker(point).addTo(map).bindPopup("Your current device location");
  } else {
    userMarker.setLatLng(point);
  }

  if (!accuracyCircle) {
    accuracyCircle = L.circle(point, { radius: accuracy, color: "#16866d",
      fillColor: "#16866d", fillOpacity: 0.10, weight: 1 }).addTo(map);
  } else {
    accuracyCircle.setLatLng(point);
    accuracyCircle.setRadius(accuracy);
  }

  routeLine.addLatLng(point);
  map.setView(point, Math.max(map.getZoom(), 15));
  setStatus(true, "Live location active", "Your device location is updating when the browser receives a new GPS position.");
}

function showError(error) {
  const messages = {
    1: "Location permission was denied. Allow location access in your browser settings and try again.",
    2: "Your device could not determine a location. Check GPS/location services and try again.",
    3: "The location request timed out. Try again in an area with a clearer GPS signal."
  };
  setStatus(false, "Location unavailable", messages[error.code] || error.message || "An unknown location error occurred.");
  stopTracking(false);
}

function startTracking() {
  if (!("geolocation" in navigator)) {
    setStatus(false, "Not supported", "This browser does not support device geolocation.");
    return;
  }
  if (!window.isSecureContext) {
    setStatus(false, "Secure connection required", "Open the published GitHub Pages HTTPS link. Browser location access usually requires HTTPS.");
    return;
  }
  if (watchId !== null) return;

  setStatus(false, "Requesting permission…", "Allow location access in the browser prompt.");
  startBtn.disabled = true;
  watchId = navigator.geolocation.watchPosition(showPosition, showError, {
    enableHighAccuracy: true,
    maximumAge: 3000,
    timeout: 20000
  });
  stopBtn.disabled = false;
}

function stopTracking(updateMessage = true) {
  if (watchId !== null) {
    navigator.geolocation.clearWatch(watchId);
    watchId = null;
  }
  startBtn.disabled = false;
  stopBtn.disabled = true;
  if (updateMessage) setStatus(false, "Tracking stopped", "Your device location is no longer being updated.");
}

startBtn.addEventListener("click", startTracking);
stopBtn.addEventListener("click", () => stopTracking(true));
$("centerBtn").addEventListener("click", () => map.setView(PATIALA, 13));

// Start at Patiala; live coordinates are only shown after the visitor grants permission.
