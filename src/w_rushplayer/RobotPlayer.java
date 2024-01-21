package w_rushplayer;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    static final Random rng = new Random();

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        rng.setSeed(rc.getID());

        final MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // funny shuffle thing
        for (int i = spawnLocs.length; i --> 0; ) {
            final int j = rng.nextInt(i + 1);
            MapLocation tmp = spawnLocs[i];
            spawnLocs[i] = spawnLocs[j];
            spawnLocs[j] = tmp;
        }

        while (true) {
            try {
                if (rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
                    rc.buyGlobal(GlobalUpgrade.ACTION);
                }
                if (rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
                    rc.buyGlobal(GlobalUpgrade.CAPTURING);
                }
                if (rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                    rc.buyGlobal(GlobalUpgrade.HEALING);
                }

                if (!rc.isSpawned()) {
                    spawn(rc, spawnLocs);
                }
                if (rc.isSpawned()) {
                    if (rc.getRoundNum() <= GameConstants.SETUP_ROUNDS) {
                        setup(rc);
                    } else {
                        play(rc);
                    }
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void spawn(RobotController rc, MapLocation[] spawnLocs) throws GameActionException {
        for (int i = spawnLocs.length; i --> 0; ) {
            if (rc.canSpawn(spawnLocs[i])) {
                rc.spawn(spawnLocs[i]);
                for (int d = 8; d --> 0; ) {
                    if (!locationInArray(rc.getLocation().add(Direction.values()[d]), spawnLocs)) {
                        if (rc.canMove(Direction.values()[d])) {
                            rc.move(Direction.values()[d]);
                            return;
                        }
                    }
                }
                return;
            }
        }
    }

    static boolean locationInArray(MapLocation needle, MapLocation[] haystack) {
        for (int i = haystack.length; i --> 0; ) {
            if (needle.equals(haystack[i])) return true;
        }
        return false;
    }

    static void setup(RobotController rc) throws GameActionException {
        if (!getCrumbs(rc)) {
            fill(rc);
            moveRandom(rc);
        }
    }

    static void play(RobotController rc) throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
        for (int i = flags.length; i --> 0; ) {
            if (rc.canPickupFlag(flags[i].getLocation())) {
                rc.pickupFlag(flags[i].getLocation());
            } else {
                tryMove(rc, rc.getLocation().directionTo(flags[i].getLocation()));
            }
        }
        if (rc.hasFlag()) {
            tryMove(rc, rc.getLocation().directionTo(nearestLocation(rc.getLocation(), rc.getAllySpawnLocations())));
        }

        final RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
        final RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
        if (enemies.length > 0) {
            if (attack(rc, enemies)) {
                rc.setIndicatorString("attacked");
            }
        }
        if (heal(rc, allies)) {
            rc.setIndicatorString("healed");
        }

        MapLocation[] broadcasts = rc.senseBroadcastFlagLocations();
        if (broadcasts.length > 0) {
            MapLocation target = broadcasts[0];
            if (broadcasts.length >= 2 && broadcasts[1].x < target.x) target = broadcasts[1];
            if (broadcasts.length >= 3 && broadcasts[2].x < target.x) target = broadcasts[2];
            tryMove(rc, rc.getLocation().directionTo(target));
        }
        moveRandom(rc);
    }

    static boolean attack(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        int attackScore = -1;
        int bestIndex = -1;
        for (int i = enemies.length; i --> 0;) {
            if (rc.getLocation().distanceSquaredTo(enemies[i].location) <= GameConstants.ATTACK_RADIUS_SQUARED) {
                final int score = 1000 - enemies[i].health + enemies[i].healLevel + enemies[i].attackLevel + enemies[i].buildLevel;
                if (attackScore < score) {
                    attackScore = score;
                    bestIndex = i;
                }
            }
        }

        if (bestIndex != -1) {
            if (rc.canAttack(enemies[bestIndex].location)) {
                rc.attack(enemies[bestIndex].location);
                return true;
            }
        }
        return false;
    }

    static boolean heal(RobotController rc, RobotInfo[] allies) throws GameActionException {
        int healScore = 0;
        int bestIndex = -1;
        for (int i = allies.length; i --> 0;) {
            final int score = 1000 - allies[i].health + allies[i].healLevel + allies[i].attackLevel + allies[i].buildLevel;
            final int distPenalty = rc.getLocation().isWithinDistanceSquared(allies[i].location, GameConstants.HEAL_RADIUS_SQUARED) ? 1 : chebyshevDistance(rc.getLocation(), allies[i].location);
            if (allies[i].health < GameConstants.DEFAULT_HEALTH && healScore < score / distPenalty) {
                healScore = score / distPenalty;
                bestIndex = i;
            }
        }

        if (bestIndex != -1) {
            if (rc.canHeal(allies[bestIndex].location)) {
                rc.heal(allies[bestIndex].location);
                return true;
            } else {
                tryMove(rc, rc.getLocation().directionTo(allies[bestIndex].location));
            }
        }
        return false;
    }
    static boolean fill(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
        for (int i = nearbyMapInfos.length; i --> 0; ) {
            if (nearbyMapInfos[i].isWater() && rc.canFill(nearbyMapInfos[i].getMapLocation())) {
                rc.fill(nearbyMapInfos[i].getMapLocation());
                return true;
            }
        }
        return false;
    }

    public static boolean getCrumbs(RobotController rc) throws GameActionException {
        MapLocation[] crumbs = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);
        if (crumbs.length > 0) {
            rc.setIndicatorString("getting crumbs");
            MapLocation closestCrumb = crumbs[0];
            for (MapLocation crumb : crumbs) {
                if (rc.getLocation().distanceSquaredTo(crumb) < rc.getLocation().distanceSquaredTo(closestCrumb)) {
                    closestCrumb = crumb;
                }
            }
            tryMove(rc, rc.getLocation().directionTo(closestCrumb));
        }
        return false;
    }

    static void moveRandom(RobotController rc) throws GameActionException {
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
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
        else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
        else if (rc.canMove(dir.rotateLeft().rotateLeft())) rc.move(dir.rotateLeft().rotateLeft());
        else if (rc.canMove(dir.rotateRight().rotateRight())) rc.move(dir.rotateRight().rotateRight());
    }

    static RobotInfo nearestRobot(MapLocation loc, RobotInfo[] robots) {
        int dist = 1_000_000;
        RobotInfo nearest = null;
        for (int i = robots.length;  i --> 0;) {
            if (dist > loc.distanceSquaredTo(robots[i].location)) {
                dist = loc.distanceSquaredTo(robots[i].location);
                nearest = robots[i];
            }
        }
        return nearest;
    }

    static MapLocation nearestLocation(MapLocation loc, MapLocation[] locations) {
        int dist = 1_000_000;
        MapLocation nearest = null;
        for (int i = locations.length;  i --> 0;) {
            if (dist > loc.distanceSquaredTo(locations[i])) {
                dist = loc.distanceSquaredTo(locations[i]);
                nearest = locations[i];
            }
        }
        return nearest;
    }

    static int chebyshevDistance(MapLocation loc1, MapLocation loc2) {
        return Math.max(Math.abs(loc1.x - loc2.x), Math.abs(loc1.y - loc2.y));
    }
}
