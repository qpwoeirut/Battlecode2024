package d_defendflag;

import battlecode.common.*;

import java.util.Arrays;

/**
 * Since all bots are "alive" (in the sense that they're running code) all the time, we can use a broadcast-based system.
 * Whenever a bot needs to communicate something, we put it into the shared array. Then we can free up that spot on the
 * next turn, since all the other bots will have seen it and copied it into their local memory.
 * The most likely complication is that a bot runs out of bytecode and doesn't read the shared array. In this case, that
 * bot won't have the information for the rest of the game, if we're de-duping the broadcasts correctly.
 * TODO: We could keep a broadcast around for multiple turns, or randomly resend existing information if we have space.
 * Some of our values are a little too large to fit in 2^16. So let's use the index of the shared array for extra
 * information. For now, the first 3 indexes always refer to ally flags and the next 6 always refer to enemy flags.
 */
public class Communications {
    final RobotController rc;

    // Both UNUSED and MAP_INFO can be 0 since any mapInfo broadcast will have a positive map value.
    final static int UNUSED = 0;


    // BROADCAST TYPES
    final static int MAP_INFO = 0;
    final static int ENEMY = 1;
    final static int FLAG = 2;
    final static int ID_MAPPING = 3;

    final static int TYPES = 4;

    // MAP VALUES. (4 * 60 * 60) << 2 = 57600
    final static int UNKNOWN = 0;
    final static int WALL_TILE = 1;
    final static int DAM_TILE = 2;
    final static int OPEN_TILE = 3;
    final static int TO_SEND = 4;  // Temporary value that marks that we are responsible for broadcasting this location.


    // SYMMETRY TYPES
    final static int HORIZONTAL = 1;
    final static int VERTICAL = 2;
    final static int ROTATIONAL = 3;


    // ATTRIBUTES TO ACCESS
    static int[][] map = new int[GameConstants.MAP_MAX_HEIGHT][GameConstants.MAP_MAX_WIDTH];
    //    static int symmetry = HORIZONTAL | VERTICAL | ROTATIONAL;  // TODO: symmetry calculations

    final static int ALLY_FLAG_LIFETIME = 3;
    static MapLocation[] allyFlags = new MapLocation[GameConstants.NUMBER_FLAGS];
    static int[] allyFlagId = {-1, -1, -1};
    static int[] allyFlagRound = {-1, -1, -1};

    final static int ENEMY_FLAG_LIFETIME = 8;
    static MapLocation[] enemyFlags = new MapLocation[GameConstants.NUMBER_FLAGS];
    static int[] enemyFlagId = {-1, -1, -1};
    static int[] enemyFlagRound = {-1, -1, -1};

    // TODO: is there a better sightings system? right now we just maintain a list of spots with enemies and expire them
    // Bots will end up with different lists, but it should be okay
    static EnemySighting[] enemySightings = new EnemySighting[300];
    static int nSightings = 0;


    // QUEUE OF VALUES TO BROADCAST

    // Highest flag ID is 60 * 60 - 1?
    // 3600 * 6 * 4 = 86400, which is too big. Use index to indicate whether broadcast is about ally or enemy flag.
    final static int ALLY_FLAG_SPACES = 3;
    final static int ENEMY_FLAG_SPACES = 6;
    final static int FLAG_SPACES = ALLY_FLAG_SPACES + ENEMY_FLAG_SPACES;
    final static int FLAG_MAPPING_BUFFER = 3;

    static MapLocation[] tbMapLocation = new MapLocation[500];  // TODO: find max # of broadcasts
    static int[] tbMapValue = new int[500];
    static int nMap = 0;
    static MapLocation[] dams = new MapLocation[3600];
    static int nDams = 0;

    static MapLocation[] tbEnemyLocation = new MapLocation[100];
    static int nEnemyLocation = 0;

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
                final int type = value % TYPES;
                value /= TYPES;
                switch (type) {
                    case MAP_INFO:
                        final int info = (value / GameConstants.MAP_MAX_HEIGHT) / GameConstants.MAP_MAX_WIDTH;
                        final int tileX = (value / GameConstants.MAP_MAX_HEIGHT) % GameConstants.MAP_MAX_WIDTH;
                        final int tileY = value % GameConstants.MAP_MAX_HEIGHT;
                        map[tileX][tileY] = info;
                        if (info == DAM_TILE) dams[nDams++] = new MapLocation(tileX, tileY);
                        break;
                    case ENEMY:
                        final int enemyX = (value / GameConstants.MAP_MAX_HEIGHT) % GameConstants.MAP_MAX_WIDTH;
                        final int enemyY = value % GameConstants.MAP_MAX_HEIGHT;
                        final MapLocation enemyLoc = new MapLocation(enemyX, enemyY);
                        boolean merged = false;
                        for (int j = nSightings; j --> 0; ) {
                            if (enemySightings[j].mergeIn(enemyLoc, rc.getRoundNum())) {
                                merged = true;
                                break;
                            }
                        }
                        if (!merged) enemySightings[nSightings++] = new EnemySighting(enemyLoc, rc.getRoundNum());
                        break;
                    case FLAG:
                        final int flagIdx = (value / GameConstants.MAP_MAX_HEIGHT) / GameConstants.MAP_MAX_WIDTH;
                        final int flagX = (value / GameConstants.MAP_MAX_HEIGHT) % GameConstants.MAP_MAX_WIDTH;
                        final int flagY = value % GameConstants.MAP_MAX_HEIGHT;

                        if (i < ALLY_FLAG_SPACES) {
                            allyFlags[flagIdx] = new MapLocation(flagX, flagY);
                            allyFlagRound[flagIdx] = rc.getRoundNum();
                        } else {
                            enemyFlags[flagIdx] = new MapLocation(flagX, flagY);
                            enemyFlagRound[flagIdx] = rc.getRoundNum();
                        }
                        break;
                    case ID_MAPPING:
                        final int id = value / GameConstants.NUMBER_FLAGS;
                        final int idx = value % GameConstants.NUMBER_FLAGS;
                        if (i < ALLY_FLAG_SPACES) {
                            allyFlagId[idx] = id;
                        } else {
                            enemyFlagId[idx] = id;
                        }
                        break;
                }
            }
        }

        if (allyFlags[0] != null && allyFlagRound[0] + ALLY_FLAG_LIFETIME < rc.getRoundNum()) allyFlags[0] = null;
        if (allyFlags[1] != null && allyFlagRound[1] + ALLY_FLAG_LIFETIME < rc.getRoundNum()) allyFlags[1] = null;
        if (allyFlags[2] != null && allyFlagRound[2] + ALLY_FLAG_LIFETIME < rc.getRoundNum()) allyFlags[2] = null;
        if (enemyFlags[0] != null && enemyFlagRound[0] + ENEMY_FLAG_LIFETIME < rc.getRoundNum()) enemyFlags[0] = null;
        if (enemyFlags[1] != null && enemyFlagRound[1] + ENEMY_FLAG_LIFETIME < rc.getRoundNum()) enemyFlags[1] = null;
        if (enemyFlags[2] != null && enemyFlagRound[2] + ENEMY_FLAG_LIFETIME < rc.getRoundNum()) enemyFlags[2] = null;
    }

    public void addMapInfo(MapInfo[] info) {
        for (int i = info.length; i --> 0; ) {
            if (map[info[i].getMapLocation().x][info[i].getMapLocation().y] == UNKNOWN) {
                map[info[i].getMapLocation().x][info[i].getMapLocation().y] = TO_SEND;
                tbMapLocation[nMap] = info[i].getMapLocation();
                tbMapValue[nMap++] = info[i].isWall() ? WALL_TILE : (info[i].isDam() ? DAM_TILE : OPEN_TILE);
            }
        }
    }

    public void addFlags(FlagInfo[] info) throws GameActionException {
        // Mark nearby flags as lost. If they're not, we can see them, and we'll update position again.
        if (allyFlags[0] != null && rc.getLocation().isWithinDistanceSquared(allyFlags[0], 2)) {
            allyFlags[0] = null;
            rc.writeSharedArray(0, 0);
        }
        if (allyFlags[1] != null && rc.getLocation().isWithinDistanceSquared(allyFlags[1], 2)) {
            allyFlags[1] = null;
            rc.writeSharedArray(1, 0);
        }
        if (allyFlags[2] != null && rc.getLocation().isWithinDistanceSquared(allyFlags[2], 2)) {
            allyFlags[2] = null;
            rc.writeSharedArray(2, 0);
        }
        if (enemyFlags[0] != null && rc.getLocation().isWithinDistanceSquared(enemyFlags[0], 2)) {
            enemyFlags[0] = null;
            rc.writeSharedArray(3, 0);
        }
        if (enemyFlags[1] != null && rc.getLocation().isWithinDistanceSquared(enemyFlags[1], 2)) {
            enemyFlags[1] = null;
            rc.writeSharedArray(4, 0);
        }
        if (enemyFlags[2] != null && rc.getLocation().isWithinDistanceSquared(enemyFlags[2], 2)) {
            enemyFlags[2] = null;
            rc.writeSharedArray(5, 0);
        }
        for (int i = info.length; i --> 0; ) {
            if (info[i].getTeam() == rc.getTeam()) {
                if (info[i].getID() == allyFlagId[0]) {
                    if (!info[i].getLocation().equals(allyFlags[0]) && rc.getRoundNum() >= FLAG_MAPPING_BUFFER) {
                        allyFlags[0] = info[i].getLocation();
                        allyFlagRound[0] = rc.getRoundNum();
                        final int packed = pack(FLAG, 0, info[i].getLocation());
                        if (rc.canWriteSharedArray(0, packed)) {
                            rc.writeSharedArray(0, packed);
                        }
                    }
                } else if (info[i].getID() == allyFlagId[1]) {
                    if (!info[i].getLocation().equals(allyFlags[1]) && rc.getRoundNum() >= FLAG_MAPPING_BUFFER) {
                        allyFlags[1] = info[i].getLocation();
                        allyFlagRound[1] = rc.getRoundNum();
                        final int packed = pack(FLAG, 1, info[i].getLocation());
                        if (rc.canWriteSharedArray(1, packed)) {
                            rc.writeSharedArray(1, packed);
                        }
                    }
                } else if (info[i].getID() == allyFlagId[2]) {
                    if (!info[i].getLocation().equals(allyFlags[2]) && rc.getRoundNum() >= FLAG_MAPPING_BUFFER) {
                        allyFlags[2] = info[i].getLocation();
                        allyFlagRound[2] = rc.getRoundNum();
                        final int packed = pack(FLAG, 2, info[i].getLocation());
                        if (rc.canWriteSharedArray(2, packed)) {
                            rc.writeSharedArray(2, packed);
                        }
                    }
                } else {
                    // Don't send flag location yet. Make sure the ID_MAPPING is processed first.
                    if (allyFlagId[0] == -1) {
                        allyFlagId[0] = info[i].getID();
                        final int packed = packFlagMapping(info[i].getID(), 0);
                        if (rc.canWriteSharedArray(0, packed)) {
                            rc.writeSharedArray(0, packed);
                        }
                    } else if (allyFlagId[1] == -1) {
                        allyFlagId[1] = info[i].getID();
                        final int packed = packFlagMapping(info[i].getID(), 1);
                        if (rc.canWriteSharedArray(1, packed)) {
                            rc.writeSharedArray(1, packed);
                        }
                    } else if (allyFlagId[2] == -1) {
                        allyFlagId[2] = info[i].getID();
                        final int packed = packFlagMapping(info[i].getID(), 2);
                        if (rc.canWriteSharedArray(2, packed)) {
                            rc.writeSharedArray(2, packed);
                        }
                    } else throw new IllegalStateException("ally flag id " + info[i].getID() + " doesn't match " + Arrays.toString(allyFlagId));
                }
            } else {
                if (info[i].getID() == enemyFlagId[0]) {
                    if (!info[i].getLocation().equals(enemyFlags[0]) && rc.getRoundNum() >= FLAG_MAPPING_BUFFER) {
                        enemyFlags[0] = info[i].getLocation();
                        final int packed = pack(FLAG, 0, info[i].getLocation());
                        if (rc.canWriteSharedArray(3, packed)) {
                            rc.writeSharedArray(3, packed);
                        }
                    }
                } else if (info[i].getID() == enemyFlagId[1]) {
                    if (!info[i].getLocation().equals(enemyFlags[1]) && rc.getRoundNum() >= FLAG_MAPPING_BUFFER) {
                        enemyFlags[1] = info[i].getLocation();
                        final int packed = pack(FLAG, 1, info[i].getLocation());
                        if (rc.canWriteSharedArray(4, packed)) {
                            rc.writeSharedArray(4, packed);
                        }
                    }
                } else if (info[i].getID() == enemyFlagId[2]) {
                    if (!info[i].getLocation().equals(enemyFlags[2]) && rc.getRoundNum() >= FLAG_MAPPING_BUFFER) {
                        enemyFlags[2] = info[i].getLocation();
                        final int packed = pack(FLAG, 2, info[i].getLocation());
                        if (rc.canWriteSharedArray(5, packed)) {
                            rc.writeSharedArray(5, packed);
                        }
                    }
                } else {
                    if (enemyFlagId[0] == -1) {
                        enemyFlagId[0] = info[i].getID();
                        final int packed = packFlagMapping(info[i].getID(), 0);
                        if (rc.canWriteSharedArray(6, packed)) {
                            rc.writeSharedArray(6, packed);
                        }
                    } else if (enemyFlagId[1] == -1) {
                        enemyFlagId[1] = info[i].getID();
                        final int packed = packFlagMapping(info[i].getID(), 1);
                        if (rc.canWriteSharedArray(7, packed)) {
                            rc.writeSharedArray(7, packed);
                        }
                    } else if (enemyFlagId[2] == -1) {
                        enemyFlagId[2] = info[i].getID();
                        final int packed = packFlagMapping(info[i].getID(), 2);
                        if (rc.canWriteSharedArray(8, packed)) {
                            rc.writeSharedArray(8, packed);
                        }
                    } else throw new IllegalStateException("enemy flag id " + info[i].getID() + " doesn't match " + Arrays.toString(enemyFlagId));
                }
            }
        }
    }

    public void addEnemies(RobotInfo[] info) {
        for (int i = info.length; i --> 0; ) {
            boolean handled = false;
            for (int j = nSightings; j --> 0; ) {
                if (enemySightings[j].near(info[i].location)) {
                    handled = true;
                    if (enemySightings[j].stale(rc.getRoundNum())) {
                        enemySightings[j].mergeIn(info[i].location, rc.getRoundNum());
                        tbEnemyLocation[nEnemyLocation++] = info[i].location;
                    }
                    break;
                }
            }
            if (!handled) {
                enemySightings[nSightings++] = new EnemySighting(info[i].location, rc.getRoundNum());
                tbEnemyLocation[nEnemyLocation++] = info[i].location;
            }
        }
    }

    public void broadcast() throws GameActionException {
        // NOTE THAT FLAG BROADCASTS (INDEXES 0-8) ARE DONE IN add_flag

        while (nClear > 0) {
            rc.writeSharedArray(toClear[--nClear], UNKNOWN);
        }

        int i = 64;
        while (i --> FLAG_SPACES && nMap > 0) {
            if (map[tbMapLocation[nMap - 1].x][tbMapLocation[nMap - 1].y] == TO_SEND) {
                // This value was already sent by another bot. Don't send it again.
                --nMap;
                continue;
            }
            if (rc.readSharedArray(i) == UNUSED) {
                --nMap;
                rc.writeSharedArray(i, pack(MAP_INFO, tbMapValue[nMap], tbMapLocation[nMap]));
                toClear[nClear++] = i;
            }
        }
        while (i --> FLAG_SPACES && nEnemyLocation > 0) {
            if (rc.readSharedArray(i) == UNUSED) {
                rc.writeSharedArray(i, pack(ENEMY, 0, tbEnemyLocation[--nEnemyLocation]));
                toClear[nClear++] = i;
            }
        }
    }

    private int pack(int type, int value, MapLocation loc) {
        return (((value * GameConstants.MAP_MAX_WIDTH + loc.x) * GameConstants.MAP_MAX_HEIGHT + loc.y) * TYPES) + type;
    }
    private int packFlagMapping(int id, int idx) {
        return (((id * GameConstants.NUMBER_FLAGS) + idx) * TYPES) + ID_MAPPING;
    }

    public MapLocation prioritySighting(MapLocation loc) {
        int bestScore = 1_000_000;
        MapLocation priority = null;
        for (int i = nSightings; i --> 0; ) {
            if (bestScore > loc.distanceSquaredTo(enemySightings[i].location) + distanceSquaredToNearestAllyFlag(enemySightings[i].location) * 10 && !enemySightings[i].stale(rc.getRoundNum())) {
                bestScore = loc.distanceSquaredTo(enemySightings[i].location) + distanceSquaredToNearestAllyFlag(enemySightings[i].location) * 10;
                priority = enemySightings[i].location;
            }
        }
        return priority;
    }

    public int nearestDam(MapLocation loc) {
        int dist = ((loc.x - rc.getMapWidth()) * (loc.x - rc.getMapWidth()) + (loc.y - rc.getMapHeight()) * (loc.y - rc.getMapHeight())) * 10;  // tiebreak by dist to center
        for (int i = nDams; i --> 0; ) {
            dist = Math.min(dist, loc.distanceSquaredTo(dams[i]));
        }
        return dist;
    }

    public int distanceSquaredToNearestAllyFlag(MapLocation loc) {
        final int dist0 = allyFlags[0] == null ? 1_000_000 : loc.distanceSquaredTo(allyFlags[0]);
        final int dist1 = allyFlags[1] == null ? 1_000_000 : loc.distanceSquaredTo(allyFlags[1]);
        final int dist2 = allyFlags[2] == null ? 1_000_000 : loc.distanceSquaredTo(allyFlags[2]);
        return dist0 <= dist1 && dist0 <= dist2 ? dist0 : Math.min(dist1, dist2);
    }
}
