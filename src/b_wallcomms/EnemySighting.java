package b_wallcomms;

import battlecode.common.MapLocation;

public class EnemySighting {
    final static int STALE = 8;
    final static int NEARBY_DIST = 10;
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
