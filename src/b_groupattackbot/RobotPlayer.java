package b_groupattackbot;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    static final Random rng = new Random();

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    //get average base location
    static MapLocation getAverageBaseLocation(RobotController rc) throws GameActionException {
        MapLocation[] baseLocations = rc.getAllySpawnLocations();
        int x = 0;
        int y = 0;
        for (MapLocation baseLocation : baseLocations) {
            x += baseLocation.x;
            y += baseLocation.y;
        }
        return new MapLocation(x / baseLocations.length, y / baseLocations.length);
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        rng.setSeed(rc.getID());

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
            moveRandom(rc);
        }
    }

    static void play(RobotController rc) throws GameActionException {
        final RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent());
        final RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
        if (enemies.length > 0) {
            final RobotInfo nearestEnemy = nearestRobot(rc.getLocation(), enemies);
            if (runAway(rc, enemies, nearestEnemy, allies)) {
                rc.setIndicatorString("running away");
            }
            if (attack(rc, enemies)) {
                rc.setIndicatorString("attacked");
                moveRandom(rc);
            }
        }
        if (heal(rc, allies)) {
            rc.setIndicatorString("healed");
            moveRandom(rc);
        }
        if(agro(rc) && rc.getRoundNum() < 700){
            rc.setIndicatorString("agro-ed");
        }
        else moveRandom(rc);
    }

    static boolean runAway(RobotController rc, RobotInfo[] enemies, RobotInfo nearestEnemy, RobotInfo[] allies) throws GameActionException {
        if (rc.getHealth() <= 450 || enemies.length > allies.length) {
            if (rc.canBuild(TrapType.STUN, rc.getLocation())) {
                // TODO: track where stun traps are, and assume they go off when they disappear
                rc.build(TrapType.STUN, rc.getLocation());
            }
            tryMove(rc, nearestEnemy.location.directionTo(rc.getLocation()));
            return true;
        }
        return false;
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

    static boolean agro(RobotController rc) throws GameActionException{
        final RobotInfo[] nearbyAllies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
        // If there are more than 3 allies nearby, we can attack!
        if (nearbyAllies.length > 3){
            // move towards enemy side
            MapLocation averageBaseLocation = getAverageBaseLocation(rc);
            MapLocation averageEnemyBaseLocation = new MapLocation(rc.getMapWidth() - averageBaseLocation.x, rc.getMapHeight() - averageBaseLocation.y);
            Direction dir = rc.getLocation().directionTo(averageEnemyBaseLocation);
            //check all 8 directions to see which ones is closest to enemy base
            Direction bestDir = dir;
            for (Direction direction : directions) {
                if (rc.getLocation().add(direction).distanceSquaredTo(averageEnemyBaseLocation) < rc.getLocation().add(bestDir).distanceSquaredTo(averageEnemyBaseLocation)) {
                    bestDir = direction;
                }
            }
            if (rc.canMove(bestDir)) {
                rc.move(bestDir);
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
            final int distPenalty = rc.getLocation().isWithinDistanceSquared(allies[i].location, GameConstants.HEAL_RADIUS_SQUARED) ? 1 : chebyshevDistance(rc.getLocation(), allies[i].location) + 1;
            if (healScore < score / distPenalty) {
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

    static void moveRandom(RobotController rc) throws GameActionException {
        //Find nearby team robots
        RobotInfo[] nearbyTeamRobots = rc.senseNearbyRobots(25, rc.getTeam());
        //Find nearby enemy robots
        RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(25, rc.getTeam().opponent());
        //If there are sufficient nearby team robots, move towards the enemy and pack together
        if (nearbyTeamRobots.length > 3) {
            //Find the closest enemy robot if exists
            if (nearbyEnemyRobots.length > 0){
                RobotInfo closestEnemy = nearbyEnemyRobots[0];
                for (RobotInfo enemy : nearbyEnemyRobots) {
                    if (rc.getLocation().distanceSquaredTo(enemy.getLocation()) < rc.getLocation().distanceSquaredTo(closestEnemy.getLocation())) {
                        closestEnemy = enemy;
                    }
                }
                //Move towards the closest enemy robot
                Direction dir = rc.getLocation().directionTo(closestEnemy.getLocation());
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }
            //Clump together and move towards center of mass of nearby team robots + center of map
            int x = 0;
            int y = 0;
            int numRobots = 0;
            for (RobotInfo robot : nearbyTeamRobots) {
                x += robot.getLocation().x;
                y += robot.getLocation().y;
                numRobots++;
            }
            x += rc.getMapWidth() / 2;
            y += rc.getMapHeight() / 2;
            numRobots++;
            MapLocation centerOfMass = new MapLocation(x / numRobots, y / numRobots);
            // move towards center of mass
            Direction dir = rc.getLocation().directionTo(centerOfMass);
            //check all 8 directions to see which ones is closest to center of mass
            Direction bestDir = dir;
            for (Direction direction : directions) {
                if (rc.getLocation().add(direction).distanceSquaredTo(centerOfMass) < rc.getLocation().add(bestDir).distanceSquaredTo(centerOfMass)) {
                    bestDir = direction;
                }
            }
            if (rc.canMove(bestDir)) {
                rc.move(bestDir);
                return;
            }

        }
        //If there are not sufficient nearby team robots, move away from the enemy and spread out
        else {
            //Find the closest enemy robot
            if(nearbyEnemyRobots.length > 0){
                RobotInfo closestEnemy = nearbyEnemyRobots[0];
                for (RobotInfo enemy : nearbyEnemyRobots) {
                    if (rc.getLocation().distanceSquaredTo(enemy.getLocation()) < rc.getLocation().distanceSquaredTo(closestEnemy.getLocation())) {
                        closestEnemy = enemy;
                    }
                }
                //Move away from the closest enemy robot
                Direction dir = rc.getLocation().directionTo(closestEnemy.getLocation()).opposite();
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }
            else{
                //move towards center of map
                int x = rc.getMapWidth() / 2;
                int y = rc.getMapHeight() / 2;
                MapLocation centerOfMap = new MapLocation(x, y);
                // move towards center of map
                Direction dir = rc.getLocation().directionTo(centerOfMap);
                //check all 8 directions to see which ones is closest to center of map
                Direction bestDir = dir;
                for (Direction direction : directions) {
                    if (rc.getLocation().add(direction).distanceSquaredTo(centerOfMap) < rc.getLocation().add(bestDir).distanceSquaredTo(centerOfMap)) {
                        bestDir = direction;
                    }
                }
                if (rc.canMove(bestDir)) {
                    rc.move(bestDir);
                    return;
                }
            }
        }
        final Direction dir = Direction.values()[rng.nextInt(8)];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
        else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
        else if (rc.canMove(dir.rotateLeft().rotateLeft())) rc.move(dir.rotateLeft().rotateLeft());
        else if (rc.canMove(dir.rotateRight().rotateRight())) rc.move(dir.rotateRight().rotateRight());
        else if (rc.canMove(dir.opposite().rotateLeft())) rc.move(dir.opposite().rotateLeft());
        else if (rc.canMove(dir.opposite().rotateRight())) rc.move(dir.opposite().rotateRight());
        else if (rc.canMove(dir.opposite())) rc.move(dir.opposite());
        return;
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

    static int chebyshevDistance(MapLocation loc1, MapLocation loc2) {
        return Math.max(Math.abs(loc1.x - loc2.x), Math.abs(loc1.y - loc2.y));
    }
}
