package b_wallcomms;

import battlecode.common.*;

/**
 * Since all bots are "alive" (in the sense that they're running code) all the time, we can use a broadcast-based system.
 * Whenever a bot needs to communicate something, we put it into the shared array. Then we can free up that spot on the
 * next turn, since all the other bots will have seen it and copied it into their local memory.
 * The most likely complication is that a bot runs out of bytecode and doesn't read the shared array. In this case, that
 * bot won't have the information for the rest of the game, if we're de-duping the broadcasts correctly.
 * TODO: We could keep a broadcast around for multiple turns, or randomly resend existing information if we have space.
 * Each broadcast with a location will be in the form (x * MAX_HEIGHT + y) << 3 | (broadcast type).
 */
public class Communications {
    final RobotController rc;

    // Both UNUSED and MAP_INFO can be 0 since any mapInfo broadcast will have a positive map value.
    final static int UNUSED = 0;


    // BROADCAST TYPES
    final static int MAP_INFO = 0;
    final static int ALLY_FLAG = 1;
    final static int ENEMY_FLAG = 2;
    final static int ENEMY = 3;

    final static int TYPE_SHIFT = 2;
    final static int TYPE_MASK = (1 << TYPE_SHIFT) - 1;


    // MAP VALUES
    final static int UNKNOWN = 0;
    final static int WALL_TILE = 1;
    final static int DAM_TILE = 2;
    final static int OPEN_TILE = 3;


    // SYMMETRY TYPES
    final static int HORIZONTAL = 1;
    final static int VERTICAL = 2;
    final static int ROTATIONAL = 3;


    // ATTRIBUTES TO ACCESS
    static int[][] map = new int[GameConstants.MAP_MAX_HEIGHT][GameConstants.MAP_MAX_WIDTH];
//    static int symmetry = HORIZONTAL | VERTICAL | ROTATIONAL;  // TODO: symmetry calculations
    static MapLocation[] allyFlags = new MapLocation[GameConstants.NUMBER_FLAGS];
    static MapLocation[] enemyFlags = new MapLocation[GameConstants.NUMBER_FLAGS];  // TODO: add something to record uncertainty/staleness

    // TODO: is there a better sightings system? right now we just maintain a list of spots with enemies and expire them
    // Bots will end up with different lists, but it should be okay
    static EnemySighting[] enemySightings = new EnemySighting[300];
    static int nSightings = 0;



    // QUEUE OF VALUES TO BROADCAST
    static int[] toBroadcast = new int[500];  // TODO: find max # of broadcasts
    static int nBroadcast = 0;

    // QUEUE OF OLD BROADCASTS TO CLEAR OUT
    static int[] toClear = new int[64];
    static int nClear = 0;


    public Communications(RobotController rc) {
        this.rc = rc;
    }

    public void readBroadcasts() throws GameActionException {
        for (int i = 64; i --> 0; ) {  // TODO: can unroll this
            int value = rc.readSharedArray(i);
            if (value != UNUSED) {
                final int type = value & TYPE_MASK;
                value >>= TYPE_SHIFT;
                switch (type) {
                    case MAP_INFO:
                        final int info = (value / GameConstants.MAP_MAX_HEIGHT) / GameConstants.MAP_MAX_HEIGHT;
                        final int wall_x = value / GameConstants.MAP_MAX_HEIGHT;
                        final int wall_y = value % GameConstants.MAP_MAX_HEIGHT;
                        map[wall_y][wall_x] = info;
                        break;
                    case ALLY_FLAG:
                        final int allyFlagId = (value / GameConstants.MAP_MAX_HEIGHT) / GameConstants.MAP_MAX_WIDTH;
                        final int allyFlagX = (value / GameConstants.MAP_MAX_HEIGHT) % GameConstants.MAP_MAX_WIDTH;
                        final int allyFlagY = value % GameConstants.MAP_MAX_HEIGHT;
                        // TODO: check flag ID range
                        allyFlags[allyFlagId] = new MapLocation(allyFlagX, allyFlagY);
                        break;
                    case ENEMY_FLAG:
                        final int enemyFlagId = (value / GameConstants.MAP_MAX_HEIGHT) / GameConstants.MAP_MAX_WIDTH;
                        final int enemyFlagX = (value / GameConstants.MAP_MAX_HEIGHT) % GameConstants.MAP_MAX_WIDTH;
                        final int enemyFlagY = value % GameConstants.MAP_MAX_HEIGHT;
                        // TODO: check flag ID range
                        enemyFlags[enemyFlagId] = new MapLocation(enemyFlagX, enemyFlagY);
                        break;
                    case ENEMY:
                        final int enemyX = (value / GameConstants.MAP_MAX_HEIGHT) % GameConstants.MAP_MAX_WIDTH;
                        final int enemyY = value % GameConstants.MAP_MAX_HEIGHT;
                        final MapLocation enemyLoc = new MapLocation(enemyX, enemyY);
                        boolean merged = false;
                        for (int j = nSightings; j --> 0;) {
                            if (enemySightings[j].mergeIn(enemyLoc, rc.getRoundNum())) {
                                merged = true;
                                break;
                            }
                        }
                        if (!merged) enemySightings[nSightings++] = new EnemySighting(enemyLoc, rc.getRoundNum());
                }
            }
        }
    }

    public void addMapInfo(MapInfo[] info) {
        for (int i = info.length; i --> 0;) {
            if (map[info[i].getMapLocation().x][info[i].getMapLocation().y] == UNKNOWN) {
                map[info[i].getMapLocation().x][info[i].getMapLocation().y] = info[i].isWall() ? WALL_TILE : (info[i].isDam() ? DAM_TILE : OPEN_TILE);
                toBroadcast[nBroadcast++] = pack(MAP_INFO, map[info[i].getMapLocation().x][info[i].getMapLocation().y], info[i].getMapLocation());
            }
        }
    }

    public void addAllyFlags(FlagInfo[] info) {
        for (int i = info.length; i --> 0;) {
            if (!allyFlags[info[i].getID()].equals(info[i].getLocation())) {
                allyFlags[info[i].getID()] = info[i].getLocation();
                toBroadcast[nBroadcast++] = pack(ALLY_FLAG, info[i].getID(), info[i].getLocation());
            }
        }
    }

    public void addEnemyFlags(FlagInfo[] info) {
        for (int i = info.length; i --> 0;) {
            if (!enemyFlags[info[i].getID()].equals(info[i].getLocation())) {
                enemyFlags[info[i].getID()] = info[i].getLocation();
                toBroadcast[nBroadcast++] = pack(ENEMY_FLAG, info[i].getID(), info[i].getLocation());
            }
        }
    }

    public void addEnemies(RobotInfo[] info) {
        for (int i = info.length; i --> 0;) {
            boolean handled = false;
            for (int j = nSightings; j --> 0;) {
                if (enemySightings[j].near(info[i].location)) {
                    handled = true;
                    if (enemySightings[j].stale(rc.getRoundNum() + 2)) {
                        enemySightings[j].mergeIn(info[i].location, rc.getRoundNum());
                        toBroadcast[nBroadcast++] = pack(ENEMY, info[i].getID(), info[i].location);
                    }
                    break;
                }
            }
            if (!handled) {
                enemySightings[nSightings++] = new EnemySighting(info[i].location, rc.getRoundNum());
                toBroadcast[nBroadcast++] = pack(ENEMY, info[i].getID(), info[i].location);
            }
        }
    }

    public void broadcast() throws GameActionException {
        while (nClear > 0) {
            rc.writeSharedArray(toClear[--nClear], UNKNOWN);
        }

        for (int i = 64; i --> 0 && nBroadcast > 0;) {
            if (rc.readSharedArray(i) == UNUSED) {
                rc.writeSharedArray(i, toBroadcast[--nBroadcast]);
                toClear[nClear++] = toBroadcast[nBroadcast];
            }
        }
    }

    private int pack(int type, int value, MapLocation loc) {
        return (((value * GameConstants.MAP_MAX_HEIGHT + loc.x) * GameConstants.MAP_MAX_HEIGHT + loc.y) << TYPE_SHIFT) + type;
    }
}
