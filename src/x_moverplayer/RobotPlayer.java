package x_moverplayer;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public strictfp class RobotPlayer {
    static final Random rng = new Random();
    static final int MAX_RADIUS = 6;
    static ArrayList<Direction> path = new ArrayList<Direction>();
    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        rng.setSeed(rc.getID());

        while (true) {
            try {
                if (!rc.isSpawned()) {
                    spawn(rc);
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
        genBellmanFordPath(rc, rc.adjacentLocation(Direction.NORTH));
//        moveRandom(rc);
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

    static void genBellmanFordPath(RobotController rc, MapLocation finLoc) throws GameActionException {

        if (path.size() == 0) {
            int dist[][] = new int[MAX_RADIUS * 2][MAX_RADIUS * 2];
            int px[][] = new int[MAX_RADIUS * 2][MAX_RADIUS * 2];
            int py[][] = new int[MAX_RADIUS * 2][MAX_RADIUS * 2];
            final int INF_VALUE = 2 * MAX_RADIUS + 1;
            for (int i = 0; i < MAX_RADIUS; i++) {
                for (int j = 0; j < MAX_RADIUS; j++) {
                    dist[i][j] = INF_VALUE;
                }
            }

            int ref_x = MAX_RADIUS, ref_y = MAX_RADIUS;
            dist[ref_x][ref_y] = 0;

            MapLocation currentLoc = rc.getLocation();
            for (MapLocation mp : rc.getAllLocationsWithinRadiusSquared(currentLoc,GameConstants.VISION_RADIUS_SQUARED)){
                int x = mp.x-currentLoc.x + ref_x, y = mp.y - currentLoc.y + ref_x;
                if ((x - ref_x) * (x - ref_x) + (y - ref_y) * (y - ref_y) <= GameConstants.VISION_RADIUS_SQUARED) {
                    MapLocation newLoc = mp;
                    MapInfo newLocInfo = rc.senseMapInfo(newLoc);
                    if (newLocInfo.isPassable())
                        for (Direction d : Direction.values()) {
                            if (x + d.dx >= 0 && y + d.dx < 2 * MAX_RADIUS
                                    && y + d.dy >= 0 && y + d.dy < 2 * MAX_RADIUS) {
                                if (dist[x][y] + 1 < dist[x + d.dx][y + d.dy]) {
                                    dist[x + d.dx][y + d.dy] = dist[x][y] + 1;
                                    px[x + d.dx][y + d.dy] = x;
                                    py[x + d.dx][y + d.dy] = y;
                                }
                            }
                        }
                }
            }
            int currentX = finLoc.x - currentLoc.x + ref_y, currentY = finLoc.y - currentLoc.y + ref_y;
            while (currentX != ref_y && currentY != ref_y)
            {
                for (Direction dir : Direction.values())
                    if (dir.dx == px[currentX][currentY] - currentX && dir.dy == py[currentX][currentY] - currentY)
                    {
                        currentX = px[currentX][currentY];
                        currentY = py[currentX][currentY];
                        path.add(dir);
                    }
            }
        }



        final Direction dir = (path.isEmpty()? Direction.NORTH : path.get(0));
        if (!path.isEmpty()) {
            path.remove(path.get(0));
        }
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
