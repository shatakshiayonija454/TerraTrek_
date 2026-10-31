# TerraTrek 🌱

A responsive sustainable travel website prototype.

## Live website
After publishing this repository, enable **Settings → Pages → Deploy from a branch → main → / (root)**. GitHub will show the public site URL.

## Features
- Embedded TerraTrek logo
- Trip planner with cost, journey time, CO₂ and weighted ranking
- Daily green streak stored in browser localStorage
- Women's safety interface with demo route preferences and personal demo ratings

## Running
Open `index.html` in a browser. No Java installation is needed for the GitHub Pages website.

## Source
The original `TripPlanner.java` and `TerraTrekServer.java` are included for reference. The browser calculator ports the formulas and mode assumptions from `TripPlanner.java`.

## Limitations
All travel costs and emissions are estimates based on simplified assumptions, not real-time data. The route cards in the women's safety section are fictional examples and **do not respond to the entered origin or destination**. They must not be relied on for real-world navigation or safety decisions. Ratings are personal, stored only in the current browser, and are not community safety reports. The streak is stored only in the current browser, not synced across devices. GitHub Pages cannot execute the included Java server.
