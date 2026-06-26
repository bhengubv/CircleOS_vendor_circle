/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * A labelled place near the user, from OpenStreetMap.
 */
package za.co.circleos.ar;

import android.location.Location;

final class Poi {

    final String name;
    final Location loc;
    float dist;       // metres, filled at draw time
    float bearing;    // degrees, filled at draw time

    Poi(String name, double lat, double lng) {
        this.name = name;
        this.loc = new Location("osm");
        this.loc.setLatitude(lat);
        this.loc.setLongitude(lng);
    }
}
