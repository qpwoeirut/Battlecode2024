package c_micro;

import battlecode.common.MapLocation;

// TODO: instead of a list of sightings, have a heatmap-style grid where each cell is a 2x2-5x5 grid
public class EnemySighting {
    final static int STALE = 8;
    final static int NEARBY_DIST = 16;
    MapLocation location;
    int lastUpdate;

    public EnemySighting(MapLocation location, int round) {
        this.location = location;
        this.lastUpdate = round;
    }

    public boolean mergeIn(MapLocation location, int round) {
        if (stale(round)) {
            this.location = location;
            this.lastUpdate = round;
            return true;
        } else if (near(location)) {
            this.location = new MapLocation((this.location.x * 2 + location.x) / 3,(this.location.y * 2 + location.y) / 3);
            this.lastUpdate = round;
            return true;
        }
        return false;
    }

    public boolean near(MapLocation location) {
        return this.location.isWithinDistanceSquared(location, NEARBY_DIST);
    }

    public boolean stale(int round) {
        return lastUpdate + STALE <= round;
    }
}