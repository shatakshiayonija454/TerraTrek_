# Patiala Live GPS

A static website that starts with a map centred on Patiala and can display the live location of the device opening the website.

## Files
- `index.html` — page structure and map libraries
- `style.css` — responsive styling
- `script.js` — map setup and browser GPS tracking

## Publish on GitHub Pages
1. Create a new GitHub repository (for example, `patiala-live-gps`).
2. Upload all three files (`index.html`, `style.css`, `script.js`) to the repository's root.
3. Commit the changes.
4. Open **Settings → Pages**.
5. Under **Build and deployment**, choose **Deploy from a branch**.
6. Select branch **main** and folder **/(root)**, then Save.
7. Wait for GitHub Pages to publish, then open the URL shown in Settings → Pages.

## Use
1. Open the published `https://...github.io/...` URL on a phone or computer.
2. Press **Start live location**.
3. Allow the browser's location permission.
4. The marker and coordinates update as the browser receives new positions.
5. Press **Stop tracking** to stop updates.

## Important
- The map initially centres on Patiala (30.3398, 76.3869). Those are city-centre map coordinates, not a live GPS reading.
- Live coordinates come from the device opening the site. Every visitor must grant permission on their own device.
- Browser geolocation requires a secure context such as HTTPS. GitHub Pages provides HTTPS.
- This starter project does not transmit or store location data on a server. It only displays it in the visitor's browser.
- A static website cannot secretly track another person's phone or provide a shared live feed from a separate GPS device. That would require a device/app sending consented location updates to a backend service.
- Leaflet and OpenStreetMap tiles are loaded from their public CDNs/services, so an internet connection is required.
