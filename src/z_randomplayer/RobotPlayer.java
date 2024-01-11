package z_randomplayer;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    static final Random rng = new Random();

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        rng.setSeed(rc.getID());

        while (true) {
            try {
                if (!rc.isSpawned()) {
                    spawn(rc);
                }
                if (rc.isSpawned()) {
                    if (rc.getRoundNum() <= 10) {
                        setup(rc);
                    } else if (rc.getRoundNum() <= GameConstants.SETUP_ROUNDS){
                        optimalGetCrumbs(rc);
                    } else{
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

    static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // Pick a random spawn location to attempt spawning in.
        MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
        if (rc.canSpawn(randomLoc)) rc.spawn(randomLoc);
    }

    static void setup(RobotController rc) throws GameActionException {
        fill(rc);
        moveRandom(rc);
    }

    static void play(RobotController rc) throws GameActionException {
        if (attack(rc)) {
            System.out.println("attacked");
        } else if (heal(rc)) {
            System.out.println("healed");
        } else if (interact(rc)) {
            System.out.println("interacted");
        }
        moveRandom(rc);
    }

    static boolean attack(RobotController rc) throws GameActionException {
        final RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(GameConstants.ATTACK_RADIUS_SQUARED, rc.getTeam().opponent());
        if (nearbyEnemies.length > 0) {
            final MapLocation target = nearbyEnemies[0].getLocation();
            if (rc.canAttack(target)) {
                rc.attack(target);
                return true;
            }
        }
        return false;
    }

    static boolean heal(RobotController rc) throws GameActionException {
        final RobotInfo[] nearbyAllies = rc.senseNearbyRobots(GameConstants.HEAL_RADIUS_SQUARED, rc.getTeam());
        if (nearbyAllies.length > 0) {
            final MapLocation target = nearbyAllies[0].getLocation();
            if (rc.canAttack(target)) {
                rc.attack(target);
                return true;
            }
        }
        return false;
    }

    static boolean interact(RobotController rc) throws GameActionException {
        if (rng.nextInt(10) < 4) {
            return fill(rc);
        } else {
            return dig(rc);
        }
    }

    static boolean fill(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
        for (int i = nearbyMapInfos.length - 1; i --> 0;) {
            if (nearbyMapInfos[i].isWater() && rc.canFill(nearbyMapInfos[i].getMapLocation())) {
                rc.fill(nearbyMapInfos[i].getMapLocation());
                return true;
            }
        }
        return false;
    }

    static boolean dig(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
        for (int i = nearbyMapInfos.length - 1; i --> 0;) {
            if (nearbyMapInfos[i].isPassable() && rc.canDig(nearbyMapInfos[i].getMapLocation())) {
                rc.dig(nearbyMapInfos[i].getMapLocation());
                return true;
            }
        }
        return false;
    }

    public static void optimalGetCrumbs(RobotController rc) throws GameActionException {
        System.out.println("I'm getting crumbs");
        MapLocation[] crumbs = rc.senseNearbyCrumbs(25);
        //find nearby crumbs
        if (crumbs.length > 0) {
            //if there are crumbs, find the closest one
            MapLocation closestCrumb = crumbs[0];
            for (MapLocation crumb : crumbs) {
                if (rc.getLocation().distanceSquaredTo(crumb) < rc.getLocation().distanceSquaredTo(closestCrumb)) {
                    closestCrumb = crumb;
                }
            }
            //move towards the closest crumb
            Direction dir = rc.getLocation().directionTo(closestCrumb);
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        }

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
}
