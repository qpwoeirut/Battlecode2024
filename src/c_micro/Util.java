package c_micro;

import battlecode.common.*;

import java.util.Random;

public class Util {
    static void moveRandom(RobotController rc, Random rng) throws GameActionException {
        final Direction dir = Direction.values()[rng.nextInt(8)];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
        else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
        else if (rc.canMove(dir.rotateLeft().rotateLeft())) rc.move(dir.rotateLeft().rotateLeft());
        else if (rc.canMove(dir.rotateRight().rotateRight())) rc.move(dir.rotateRight().rotateRight());
        else if (rc.canMove(dir.opposite().rotateLeft())) rc.move(dir.opposite().rotateLeft());
        else if (rc.canMove(dir.opposite().rotateRight())) rc.move(dir.opposite().rotateRight());
        else if (rc.canMove(dir.opposite())) rc.move(dir.opposite());
    }

    static void tryMove(RobotController rc, Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER) return;
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
        else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
        else if (rc.canMove(dir.rotateLeft().rotateLeft())) rc.move(dir.rotateLeft().rotateLeft());
        else if (rc.canMove(dir.rotateRight().rotateRight())) rc.move(dir.rotateRight().rotateRight());
    }

    static RobotInfo nearestRobot(MapLocation loc, RobotInfo[] robots) {
        int dist = 1_000_000;
        RobotInfo nearest = null;
        for (int i = robots.length; i --> 0; ) {
            if (dist > loc.distanceSquaredTo(robots[i].location)) {
                dist = loc.distanceSquaredTo(robots[i].location);
                nearest = robots[i];
            }
        }
        return nearest;
    }

    static EnemySighting nearestSighting(MapLocation loc, EnemySighting[] sightings, int nSightings, int round) {
        int dist = 1_000_000;
        EnemySighting nearest = null;
        for (int i = nSightings; i --> 0; ) {
            if (dist > loc.distanceSquaredTo(sightings[i].location) && !sightings[i].stale(round)) {
                dist = loc.distanceSquaredTo(sightings[i].location);
                nearest = sightings[i];
            }
        }
        return nearest;
    }

    // returns highest index in case of tie, which is ideal for enemyReachCount since the highest index is CENTER
    static int minIndex(int[] arr) {
        int idx = -1;
        int value = 1_000_000_000;
        for (int i = arr.length; i --> 0;) {
            if (value > arr[i]) {
                value = arr[i];
                idx = i;
            }
        }
        return idx;
    }

    static boolean locationInArray(MapLocation needle, MapLocation[] haystack) {
        for (int i = haystack.length; i --> 0; ) {
            if (needle.equals(haystack[i])) return true;
        }
        return false;
    }

    static int chebyshevDistance(MapLocation loc1, MapLocation loc2) {
        return Math.max(Math.abs(loc1.x - loc2.x), Math.abs(loc1.y - loc2.y));
    }

    // CENTER if no movement required; null if impossible to reach after moving
    static Direction directionToReach(RobotController rc, MapLocation location, int radiusSquared) {
        if (rc.getLocation().isWithinDistanceSquared(location, radiusSquared)) return Direction.CENTER;
        if (rc.canMove(Direction.NORTH) && rc.getLocation().add(Direction.NORTH).isWithinDistanceSquared(location, radiusSquared)) return Direction.NORTH;
        if (rc.canMove(Direction.WEST) && rc.getLocation().add(Direction.WEST).isWithinDistanceSquared(location, radiusSquared)) return Direction.WEST;
        if (rc.canMove(Direction.SOUTH) && rc.getLocation().add(Direction.SOUTH).isWithinDistanceSquared(location, radiusSquared)) return Direction.SOUTH;
        if (rc.canMove(Direction.EAST) && rc.getLocation().add(Direction.EAST).isWithinDistanceSquared(location, radiusSquared)) return Direction.EAST;
        if (rc.canMove(Direction.NORTHWEST) && rc.getLocation().add(Direction.NORTHWEST).isWithinDistanceSquared(location, radiusSquared)) return Direction.NORTHWEST;
        if (rc.canMove(Direction.SOUTHWEST) && rc.getLocation().add(Direction.SOUTHWEST).isWithinDistanceSquared(location, radiusSquared)) return Direction.SOUTHWEST;
        if (rc.canMove(Direction.SOUTHEAST) && rc.getLocation().add(Direction.SOUTHEAST).isWithinDistanceSquared(location, radiusSquared)) return Direction.SOUTHEAST;
        if (rc.canMove(Direction.NORTHEAST) && rc.getLocation().add(Direction.NORTHEAST).isWithinDistanceSquared(location, radiusSquared)) return Direction.NORTHEAST;
        return null;
    }

    static boolean canMoveAndAct(RobotController rc, MapLocation toMove, MapLocation actTarget) throws GameActionException {
        return rc.onTheMap(toMove) &&  // onTheMap isn't limited by vision
                (!rc.canSenseLocation(toMove) || rc.sensePassability(toMove)) &&  // assume passable if unknown
                toMove.isWithinDistanceSquared(actTarget, GameConstants.ATTACK_RADIUS_SQUARED);
    }

    static float attackDmg(int level) {
        switch (level) {
            case 1: return 150 * 1.05f;
            case 2: return 150 * 1.10f;
            case 3: return 150 * 1.15f;
            case 4: return 150 * 1.20f;
            case 5: return 150 * 1.30f;
            case 6: return 150 * 1.50f;
            default: return 150;
        }
    }
}
