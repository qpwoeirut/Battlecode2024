package b_wallcomms;

import battlecode.common.*;

import java.util.Random;

import static b_wallcomms.Util.*;

public strictfp class RobotPlayer {
    static Random rng;
    static Communications comms;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        comms = new Communications(rc);
        rng = new Random(rc.getID());

        final MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // funny shuffle thing
        for (int i = spawnLocs.length; i-- > 0; ) {
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

                if (!rc.isSpawned()) {
                    spawn(rc, spawnLocs);
                }
                comms.readBroadcasts();

                if (rc.isSpawned()) {
                    final MapInfo[] mapInfos = rc.senseNearbyMapInfos();
                    comms.addMapInfo(mapInfos);

                    final FlagInfo[] allyFlags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
                    comms.addAllyFlags(allyFlags);

                    final FlagInfo[] enemyFlags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
                    comms.addEnemyFlags(enemyFlags);

                    final RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
                    comms.addEnemies(enemies);

                    comms.broadcast();

                    if (rc.getRoundNum() <= GameConstants.SETUP_ROUNDS) {
                        setup(rc);
                    } else {
                        play(rc, enemies);
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
        for (int i = spawnLocs.length; i-- > 0; ) {
            if (rc.canSpawn(spawnLocs[i])) {
                rc.spawn(spawnLocs[i]);
                for (int d = 8; d-- > 0; ) {
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
        for (int i = haystack.length; i-- > 0; ) {
            if (needle.equals(haystack[i])) return true;
        }
        return false;
    }

    static void setup(RobotController rc) throws GameActionException {
        if (!getCrumbs(rc)) {
            fill(rc);
            moveRandom(rc, rng);
        }
    }

    static void play(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        final RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
        if (enemies.length > 0) {
            final RobotInfo nearestEnemy = nearestRobot(rc.getLocation(), enemies);
            if (runAway(rc, enemies, nearestEnemy, allies)) {
                rc.setIndicatorString("running away");
            }
            if (attack(rc, enemies, enemies.length * 2 <= allies.length + 1)) {
                rc.setIndicatorString("attacked");
            }
        }
        if (heal(rc, allies)) {
            rc.setIndicatorString("healed");
        }

        if (enemies.length == 0) {
            final EnemySighting nearestEnemySighting = nearestSighting(rc.getLocation(), Communications.enemySightings);
            if (nearestEnemySighting != null) {
                tryMove(rc, rc.getLocation().directionTo(nearestEnemySighting.location));
            }
        }
        moveRandom(rc, rng);
    }

    static boolean runAway(RobotController rc, RobotInfo[] enemies, RobotInfo nearestEnemy, RobotInfo[] allies) throws GameActionException {
        if (rc.getHealth() <= 450 || enemies.length > allies.length) {
            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation()) && enemies.length >= 4) {
                // TODO: track where traps are, and assume they go off when they disappear, then switch to stun
                rc.build(TrapType.EXPLOSIVE, rc.getLocation());
            }
            tryMove(rc, nearestEnemy.location.directionTo(rc.getLocation()));
            return true;
        }
        return false;
    }

    static boolean attack(RobotController rc, RobotInfo[] enemies, boolean chase) throws GameActionException {
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
            } else if (chase) {
                tryMove(rc, rc.getLocation().directionTo(enemies[bestIndex].location));
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

    static boolean interact(RobotController rc) throws GameActionException {
        if (rng.nextInt(10) < 4) {
            return fill(rc);
        } else {
            return dig(rc);
        }
    }

    static boolean fill(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
        for (int i = nearbyMapInfos.length; i-- > 0; ) {
            if (nearbyMapInfos[i].isWater() && rc.canFill(nearbyMapInfos[i].getMapLocation())) {
                rc.fill(nearbyMapInfos[i].getMapLocation());
                return true;
            }
        }
        return false;
    }

    static boolean dig(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
        for (int i = nearbyMapInfos.length; i-- > 0; ) {
            if (nearbyMapInfos[i].isPassable() && rc.canDig(nearbyMapInfos[i].getMapLocation())) {
                rc.dig(nearbyMapInfos[i].getMapLocation());
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
}
