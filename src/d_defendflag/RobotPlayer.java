package d_defendflag;

import battlecode.common.*;

import java.util.Random;

import static d_defendflag.Util.*;

public strictfp class RobotPlayer {
    static Random rng;
    static Communications comms;

//    final static int MOVE_FLAGS = 10;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        comms = new Communications(rc);
        rng = new Random(rc.getID());

        final MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // funny shuffle thing
        for (int i = spawnLocs.length; i --> 0; ) {
            final int j = rng.nextInt(i + 1);
            MapLocation tmp = spawnLocs[i];
            spawnLocs[i] = spawnLocs[j];
            spawnLocs[j] = tmp;
        }

        MapLocation[] allyFlagSpawns = new MapLocation[GameConstants.NUMBER_FLAGS];
//        MapLocation[] spawnZoneCenters = new MapLocation[GameConstants.NUMBER_FLAGS];

        while (true) {
            try {
//                int rnd = rc.getRoundNum();
                if (rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
                    rc.buyGlobal(GlobalUpgrade.ACTION);
                } else if (rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                    rc.buyGlobal(GlobalUpgrade.HEALING);
                }

                if (!rc.isSpawned()) {
                    spawn(rc, spawnLocs);
                }
                comms.readBroadcasts();

//                debugBytecode(rc, "after readBroadcasts");

                if (rc.getRoundNum() <= 202) {  // save flag locations during setup
                    allyFlagSpawns = new MapLocation[]{Communications.allyFlags[0], Communications.allyFlags[1], Communications.allyFlags[2]};
                }
//                if (rc.getRoundNum() < MOVE_FLAGS) {
//                    spawnZoneCenters = new MapLocation[]{Communications.allyFlags[0], Communications.allyFlags[1], Communications.allyFlags[2]};
//                }

                if (rc.isSpawned()) {
                    final MapInfo[] mapInfos = rc.senseNearbyMapInfos();
                    comms.addMapInfo(mapInfos);

                    final FlagInfo[] flags = rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED);
                    comms.addFlags(flags);

                    // recover in case moving the flags didn't work and the positions got reset
                    // if a flag gets stolen and dropped in exactly the spawn zone we'll have issues but hopefully that doesn't happen
//                    for (int i = flags.length; i --> 0;) {
//                        if (flags[i].getID() == Communications.allyFlagId[0] && !flags[i].isPickedUp() && flags[i].getLocation().equals(spawnZoneCenters[0])) {
//                            allyFlagSpawns[0] = spawnZoneCenters[0];
//                        } else if (flags[i].getID() == Communications.allyFlagId[1] && !flags[i].isPickedUp() && flags[i].getLocation().equals(spawnZoneCenters[1])) {
//                            allyFlagSpawns[1] = spawnZoneCenters[1];
//                        } else if (flags[i].getID() == Communications.allyFlagId[2] && !flags[i].isPickedUp() && flags[i].getLocation().equals(spawnZoneCenters[2])) {
//                            allyFlagSpawns[2] = spawnZoneCenters[2];
//                        }
//                    }
//                    System.out.println(Arrays.toString(allyFlagSpawns));
//                    System.out.println(Arrays.toString(spawnZoneCenters));

                    final RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
                    comms.addEnemies(enemies);

                    comms.broadcast();

//                    debugBytecode(rc, "after broadcast");

                    if (rc.getRoundNum() <= GameConstants.SETUP_ROUNDS - Math.max(rc.getMapWidth(), rc.getMapHeight())) {
                        setup(rc);
                    } else {
                        play(rc, enemies, allyFlagSpawns);
                    }
                }
//                if (rc.getRoundNum() != rnd) {
//                    System.out.println("uh oh");
//                }
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

    static void setup(RobotController rc) throws GameActionException {
//        for (int i = flags.length; i --> 0 && rc.getRoundNum() > MOVE_FLAGS;) {
//            if (rc.canPickupFlag(flags[i].getLocation())) {
//                rc.pickupFlag(flags[i].getLocation());
//                break;
//            }
//        }
//
//        if (rc.hasFlag()) {
//            if (rc.getRoundNum() >= 50) {
//                final int nearestDam0 = comms.nearestDam(spawnZoneCenters[0]);
//                final int nearestDam1 = comms.nearestDam(spawnZoneCenters[1]);
//                final int nearestDam2 = comms.nearestDam(spawnZoneCenters[2]);
//
//                final int bestIndex = nearestDam0 >= nearestDam1 && nearestDam0 >= nearestDam2 ? 0 : (nearestDam1 >= nearestDam2 ? 1 : 2);
//                tryMove(rc, rc.getLocation().directionTo(spawnZoneCenters[bestIndex]));
//            }
//        } else
        if (!getCrumbs(rc)) {
            fill(rc);
            moveRandom(rc, rng);
        }
    }

    static void play(RobotController rc, RobotInfo[] enemies, MapLocation[] allyFlagSpawns) throws GameActionException {
        final int[] enemyReachCount = {  // order matches Direction.values()
                rc.canMove(Direction.NORTH) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.NORTH)) : 1_000_000,
                rc.canMove(Direction.NORTHEAST) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.NORTHEAST)) : 1_000_000,
                rc.canMove(Direction.EAST) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.EAST)) : 1_000_000,
                rc.canMove(Direction.SOUTHEAST) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.SOUTHEAST)) : 1_000_000,
                rc.canMove(Direction.SOUTH) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.SOUTH)) : 1_000_000,
                rc.canMove(Direction.SOUTHWEST) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.SOUTHWEST)) : 1_000_000,
                rc.canMove(Direction.WEST) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.WEST)) : 1_000_000,
                rc.canMove(Direction.NORTHWEST) ? countEnemiesCanReach(rc, rc.getLocation().add(Direction.NORTHWEST)) : 1_000_000,
                countEnemiesCanReach(rc, rc.getLocation()),
        };

//        debugBytecode(rc, "after enemyReachCount");

        final RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
        if (enemies.length > 0) {
            fight(rc, enemies, allies, enemyReachCount);
//            debugBytecode(rc, "after fight");
        }
        boolean guarding = false;
        if (recoverFlag(rc, allyFlagSpawns)) {
            heal(rc, allies);  // we can still try healing as we move to the flag
            rc.setIndicatorString("recovering stolen flag");
        } else if (heal(rc, allies)) {
            rc.setIndicatorString("healed");
        } else if (guardFlag(rc, allyFlagSpawns)) {
            rc.setIndicatorString("guarding flag");
            guarding = true;
        } else if (enemies.length == 0) {
            final MapLocation nearestEnemySighting = comms.prioritySighting(rc.getLocation());
            if (nearestEnemySighting != null) {
                final Direction dir = rc.getLocation().directionTo(nearestEnemySighting);
                tryMove(rc, dir);
                if (rc.isMovementReady() && rc.canFill(rc.getLocation().add(dir))) {
                    rc.fill(rc.getLocation().add(dir));
                }
                rc.setIndicatorString("moving to " + nearestEnemySighting);
            }
        }

        if (!guarding) {
            if (enemies.length > 0) {
                moveSafe(rc, enemyReachCount);
            } else if (getCrumbs(rc)) ;
            else spreadOut(rc, allies);
        }

//        debugBytecode(rc, "end of play");
    }

    // ideally we'd know enemies' movement cooldowns by tracking their moves but that is hard and scary to implement
    // so for now just assume they can always move
    static int countEnemiesCanReach(RobotController rc, MapLocation location) throws GameActionException {
        final RobotInfo[] enemies = rc.senseNearbyRobots(location, 10, rc.getTeam().opponent());
        int count = 0;
        for (int i = enemies.length; i --> 0; ) {
            count += (enemies[i].location.isWithinDistanceSquared(location, GameConstants.ATTACK_RADIUS_SQUARED) ||
                    canMoveAndAct(rc, enemies[i].location.add(enemies[i].location.directionTo(location)), location) ||
                    canMoveAndAct(rc, enemies[i].location.add(enemies[i].location.directionTo(location).rotateLeft()), location) ||
                    canMoveAndAct(rc, enemies[i].location.add(enemies[i].location.directionTo(location).rotateRight()), location)
            ) ? 1 : 0;
        }
        return count;
    }

    /**
     * Handles situations where at least one enemy is visible.
     * If our action is cooling down, move to whatever spot is in range of the least enemies or chase if we clearly outnumber the enemy.
     * Otherwise:
     * If we can kill an enemy, always do it.
     * If we're outnumbered or at low health, move away and place a trap if there are enough enemies.
     * If we clearly outnumber the enemy or are near the border of enemy territory, attack and move toward an enemy.
     * Otherwise, attack and move to whatever spot is in range of the least enemies.
     *
     * @param rc              the RobotController
     * @param enemies         list of enemies
     * @param allies          list of allies
     * @param enemyReachCount array describing how many enemies can attack our adjacent locations
     */
    static void fight(RobotController rc, RobotInfo[] enemies, RobotInfo[] allies, int[] enemyReachCount) throws GameActionException {
        int allyHealth = 0; for (int i = allies.length; i --> 0; ) allyHealth += allies[i].health;
        int enemyHealth = 0; for (int i = enemies.length; i --> 0; ) enemyHealth += enemies[i].health;
        if (rc.isActionReady()) {
            RobotInfo[] enemiesInRange = rc.senseNearbyRobots(10, rc.getTeam().opponent());
            final RobotInfo nearestEnemy = nearestRobot(rc.getLocation(), enemiesInRange.length > 0 ? enemiesInRange : enemies);

            if (killEnemy(rc, enemiesInRange, enemyReachCount));
            else if (runAway(rc, enemies, nearestEnemy, enemyReachCount, allyHealth, enemyHealth));
            else if (beAggressive(rc, enemies, allies, allyHealth, enemyHealth));
            else attackAndMoveAway(rc, enemiesInRange, enemyReachCount);
        } else if (beAggressive(rc, enemies, allies, allyHealth, enemyHealth));
        else moveSafe(rc, enemyReachCount);
    }

    static boolean killEnemy(RobotController rc, RobotInfo[] enemiesInRange, int[] enemyReachCount) throws GameActionException {
        int bestIndex = -1;
        Direction bestDir = null;
        int bestScore = -1;

        final float damage = attackDmg(rc.getLevel(SkillType.ATTACK));
        for (int i = enemiesInRange.length; i --> 0; ) {
            if (enemiesInRange[i].health <= damage) {
                for (int d = 9; d --> 0; ) {
                    final Direction dir = Direction.values()[d];
                    if (rc.canMove(dir) && rc.getLocation().add(dir).isWithinDistanceSquared(enemiesInRange[i].location, GameConstants.ATTACK_RADIUS_SQUARED)) {
                        final int score = (dir == Direction.CENTER ? 100 : 10) +
                                (rc.senseMapInfo(rc.getLocation().add(dir)).getTeamTerritory() == rc.getTeam().opponent() ? 1000 : 10) -
                                enemyReachCount[d];
                        if (bestScore < score) {
                            bestScore = score;
                            bestIndex = i;
                            bestDir = dir;
                        }
                    }
                }
            }
        }

        if (bestIndex != -1) {
            if (bestDir != Direction.CENTER && rc.canMove(bestDir)) {
                rc.move(bestDir);
            }
            if (rc.canAttack(enemiesInRange[bestIndex].location)) {
                rc.attack(enemiesInRange[bestIndex].location);
                return true;
            }
        }
        return false;
    }

    static boolean runAway(RobotController rc, RobotInfo[] enemies, RobotInfo nearestEnemy, int[] enemyReachCount, int allyHealth, int enemyHealth) throws GameActionException {
        if (rc.getHealth() < GameConstants.DEFAULT_HEALTH && (rc.getHealth() <= 450 || allyHealth < enemyHealth)) {
            if (enemies.length * rc.getCrumbs() >= 4000) {
                if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation().add(rc.getLocation().directionTo(nearestEnemy.location)))) {
                    // TODO: track where traps are, and assume they go off when they disappear, then switch to stun
                    rc.build(TrapType.EXPLOSIVE, rc.getLocation().add(rc.getLocation().directionTo(nearestEnemy.location)));
                } else if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                    rc.build(TrapType.EXPLOSIVE, rc.getLocation());
                }
            }
            tryMove(rc, Direction.values()[minIndex(enemyReachCount)]);
            if (enemies.length * rc.getCrumbs() >= 4000) {
                if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                    rc.build(TrapType.EXPLOSIVE, rc.getLocation());
                }
            }
            return true;
        }
        return false;
    }

    static boolean beAggressive(RobotController rc, RobotInfo[] enemies, RobotInfo[] allies, int allyHealth, int enemyHealth) throws GameActionException {
        if (allyHealth >= enemyHealth * 2 && allyHealth >= enemyHealth + GameConstants.DEFAULT_HEALTH * 2 && allies.length * 2 >= enemies.length * 3) {
            int attackScore = -1;
            int bestIndex = -1;
            Direction bestDir = Direction.CENTER;
            for (int i = enemies.length; i --> 0;) {
                final Direction dir = directionToReach(rc, enemies[i].location, GameConstants.ATTACK_RADIUS_SQUARED);
                final int score = 1000 - enemies[i].health +
                        enemies[i].healLevel + enemies[i].attackLevel + enemies[i].buildLevel +
                        (dir != null ? 1000 : 0);
                if (attackScore < score) {
                    attackScore = score;
                    bestIndex = i;
                    bestDir = dir;
                }
            }

            if (bestIndex != -1) {
                if (bestDir == null) {
                    // TODO: do some sort of pathfinding
                    bestDir = rc.getLocation().directionTo(enemies[bestIndex].location);
                }
                if (bestDir != Direction.CENTER && rc.canMove(bestDir)) {
                    rc.move(bestDir);
                }
                if (rc.canAttack(enemies[bestIndex].location)) {
                    rc.attack(enemies[bestIndex].location);
                }
            }
            return true;
        }
        return false;
    }

    static void attackAndMoveAway(RobotController rc, RobotInfo[] enemiesInRange, int[] enemyReachCount) throws GameActionException {
        int bestScore = -1;
        int bestIndex = -1;
        Direction bestDir = Direction.values()[minIndex(enemyReachCount)];
        for (int i = enemiesInRange.length; i --> 0;) {
            for (int d = 9; d --> 0;) {
                final Direction dir = Direction.values()[d];
                if (rc.canMove(dir) && rc.getLocation().add(dir).isWithinDistanceSquared(enemiesInRange[i].location, GameConstants.ATTACK_RADIUS_SQUARED)) {
                    final int score = 1000 - enemiesInRange[i].health + enemiesInRange[i].attackLevel + enemiesInRange[i].buildLevel + enemiesInRange[i].healLevel +
                            100 - enemyReachCount[d];
                    if (bestScore < score) {
                        bestScore = score;
                        bestIndex = i;
                        bestDir = dir;
                    }
                }
            }
        }

        if (bestDir != Direction.CENTER && rc.canMove(bestDir)) {
            rc.move(bestDir);
        }
        if (bestIndex != -1) {
            if (rc.canAttack(enemiesInRange[bestIndex].location)) {
                rc.attack(enemiesInRange[bestIndex].location);
            }
        }
    }

    static void moveSafe(RobotController rc, int[] enemyReachCount) throws GameActionException {
        Direction bestDir = Direction.values()[minIndex(enemyReachCount)];
        if (bestDir != Direction.CENTER && rc.canMove(bestDir)) {
            rc.move(bestDir);
        }
    }

    static boolean recoverFlag(RobotController rc, MapLocation[] allyFlagSpawns) throws GameActionException {
        if (allyFlagSpawns[0] != null && !Communications.allyFlags[0].equals(allyFlagSpawns[0])) {
            tryMove(rc, rc.getLocation().directionTo(Communications.allyFlags[0]));
            return true;
        }
        if (allyFlagSpawns[1] != null && !Communications.allyFlags[1].equals(allyFlagSpawns[1])) {
            tryMove(rc, rc.getLocation().directionTo(Communications.allyFlags[1]));
            return true;
        }
        if (allyFlagSpawns[2] != null && !Communications.allyFlags[2].equals(allyFlagSpawns[2])) {
            tryMove(rc, rc.getLocation().directionTo(Communications.allyFlags[2]));
            return true;
        }
        return false;
    }

    static boolean guardFlag(RobotController rc, MapLocation[] allyFlagSpawns) throws GameActionException {
        if (allyFlagSpawns[0] != null && ((rc.canSenseLocation(allyFlagSpawns[0]) && rc.senseRobotAtLocation(allyFlagSpawns[0]) == null) || rc.getLocation().equals(allyFlagSpawns[0]))) {
            tryMove(rc, rc.getLocation().directionTo(allyFlagSpawns[0]));
            return true;
        }
        if (allyFlagSpawns[1] != null && ((rc.canSenseLocation(allyFlagSpawns[1]) && rc.senseRobotAtLocation(allyFlagSpawns[1]) == null) || rc.getLocation().equals(allyFlagSpawns[1]))) {
            tryMove(rc, rc.getLocation().directionTo(allyFlagSpawns[1]));
            return true;
        }
        if (allyFlagSpawns[2] != null && ((rc.canSenseLocation(allyFlagSpawns[2]) && rc.senseRobotAtLocation(allyFlagSpawns[2]) == null) || rc.getLocation().equals(allyFlagSpawns[2]))) {
            tryMove(rc, rc.getLocation().directionTo(allyFlagSpawns[2]));
            return true;
        }
        return false;
    }

    static boolean heal(RobotController rc, RobotInfo[] allies) throws GameActionException {
        int healScore = 0;
        int bestIndex = -1;
        for (int i = allies.length; i --> 0; ) {
            final int score = 1000 - allies[i].health + allies[i].healLevel + allies[i].attackLevel + allies[i].buildLevel;
            final int distPenalty = rc.getLocation().isWithinDistanceSquared(allies[i].location, GameConstants.HEAL_RADIUS_SQUARED) ? 0 : 700;
            if (allies[i].health < GameConstants.DEFAULT_HEALTH && healScore < score - distPenalty) {
                healScore = score - distPenalty;
                bestIndex = i;
            }
        }

        if (bestIndex != -1) {
            if (rc.canHeal(allies[bestIndex].location)) {
                rc.heal(allies[bestIndex].location);
            } else {
                tryMove(rc, rc.getLocation().directionTo(allies[bestIndex].location));
            }
            return true;
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
        for (int i = nearbyMapInfos.length; i --> 0; ) {
            if (nearbyMapInfos[i].isWater() && rc.canFill(nearbyMapInfos[i].getMapLocation())) {
                rc.fill(nearbyMapInfos[i].getMapLocation());
                return true;
            }
        }
        return false;
    }

    static boolean dig(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(GameConstants.INTERACT_RADIUS_SQUARED);
        for (int i = nearbyMapInfos.length; i --> 0; ) {
            if (nearbyMapInfos[i].isPassable() && rc.canDig(nearbyMapInfos[i].getMapLocation())) {
                rc.dig(nearbyMapInfos[i].getMapLocation());
                return true;
            }
        }
        return false;
    }

    static boolean getCrumbs(RobotController rc) throws GameActionException {
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
            tryFill(rc, rc.getLocation().directionTo(closestCrumb));
            tryMove(rc, rc.getLocation().directionTo(closestCrumb));
        }
        return false;
    }

    static void spreadOut(RobotController rc, RobotInfo[] allies) throws GameActionException {
        int weightX = 0, weightY = 0;
        for (int i = allies.length; i --> 0;) {
            if (rc.getLocation().x != allies[i].location.x) {
                weightX += 1000 / (rc.getLocation().x - allies[i].location.x);
            }
            if (rc.getLocation().y != allies[i].location.y) {
                weightY += 1000 / (rc.getLocation().y - allies[i].location.y);
            }
        }
        final int dx = rng.nextInt(101) - 50 + weightX;
        final int dy = rng.nextInt(101) - 50 + weightY;
        final Direction dir = new MapLocation(0, 0).directionTo(new MapLocation(dx, dy));
        tryMove(rc, dir);
    }
}
