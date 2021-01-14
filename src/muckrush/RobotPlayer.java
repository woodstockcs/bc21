package muckrush;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    static final RobotType[] spawnableRobot = {
        RobotType.POLITICIAN,
        RobotType.SLANDERER,
        RobotType.MUCKRAKER,
    };

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

    static int turnCount;
    static MapLocation[] enemyEnglightenmentCenters = new MapLocation[5];
    static int role; // 1 = scout, 2 = rando
    static Direction myDir = randomDirection();

    static int bottom = 23921;
    static int top = 23952;
    static int left = 10000;
    static int right = 10031;

    static MapLocation spawn;
    static MapLocation dest;

    static int zoneSpacing = 10;
    static int nextZoneDeltaX = 0;
    static int nextZoneDeltaY = 0;

    static String state = "forming"; // "forming", "waiting", "marching"
    static Direction marchingDirection = Direction.WEST;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        //System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You may rewrite this into your own control structure if you wish.
                // System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case ENLIGHTENMENT_CENTER: runEnlightenmentCenter(); break;
                    case POLITICIAN:           runPolitician();          break;
                    case SLANDERER:            runSlanderer();           break;
                    case MUCKRAKER:            runMuckraker();           break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    static void runEnlightenmentCenter() throws GameActionException {
        if (nextZoneDeltaX == 0) {
          nextZoneDeltaX = rc.getLocation().x - left + (zoneSpacing / 2);
          nextZoneDeltaY = rc.getLocation().y - bottom + (zoneSpacing / 2);
          updateFlag();
        }
        RobotType toBuild = RobotType.MUCKRAKER;
        int influence = 3;
        for (Direction dir : directions) {
            if (rc.canBuildRobot(toBuild, dir, influence)) {
                rc.buildRobot(toBuild, dir, influence);
                setNextZone();
                //updateFlag();
            } else {
                break;
            }
        }
    }

    static void runPolitician() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        int actionRadius = rc.getType().actionRadiusSquared;
        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemy);
        if (attackable.length != 0 && rc.canEmpower(actionRadius)) {
            //System.out.println("empowering...");
            rc.empower(actionRadius);
            //System.out.println("empowered");
            return;
        }

        // create memory when born
        if (spawn == null) {
          spawn = new MapLocation(rc.getLocation().x, rc.getLocation().y);
          dest = new MapLocation(
            spawn.x - 3,
            spawn.y + (int)(Math.random() * 10) - 5
          );
        }

        if (state == "forming") {
          myDir = rc.getLocation().directionTo(dest);
          if (rc.getRoundNum() % 50 == 0) {
            state = "marching";
          } else if (myDir == Direction.CENTER) {
            state = "waiting";
          } else {
            tryMove(myDir);
          }
        } else if (state == "waiting") {
          if (rc.getRoundNum() % 50 == 0) {
            state = "marching";
          }
        } else if (state == "marching") {
          myDir = marchingDirection;
          tryMove(myDir);
        }


    }

    static void runSlanderer() throws GameActionException {

      // create memory when born
      if (spawn == null) {
        spawn = new MapLocation(rc.getLocation().x, rc.getLocation().y);
      }

      if (dest == null) {
        RobotInfo[] friends = rc.senseNearbyRobots(5, rc.getTeam());
        for (RobotInfo friend : friends) {
          if (friend.getType() == RobotType.ENLIGHTENMENT_CENTER) {
            int id = friend.getID();
            int flag = rc.getFlag(id);
            String stringFlag = String.valueOf(flag);
            int dx = Integer.parseInt(stringFlag.substring(0, 2));
            int dy = Integer.parseInt(stringFlag.substring(2, 4));
            dest = new MapLocation(spawn.x + dx, spawn.y + dy);
          }
        }
      }
      if (dest != null) {
        myDir = rc.getLocation().directionTo(dest);
        tryMove(myDir);
      } else {
        tryMove(randomDirection());
      }

    }

    static void runMuckraker() throws GameActionException {
      // remember spawn location when born
      if (spawn == null) {
        spawn = new MapLocation(rc.getLocation().x, rc.getLocation().y);
      }
      // set destination when born
      if (dest == null) {
        dest = new MapLocation(spawn.x - 4, spawn.y + (int)(Math.random() * 10 - 5));
      }
      // create role when born
      if (role == 0) {
        role = 2;
        state = "forming";
        marchingDirection = Direction.WEST;
      }
      //System.out.println("I'm a muckraker with role " + role);
        Team enemy = rc.getTeam().opponent();
        int actionRadius = rc.getType().actionRadiusSquared;
        for (RobotInfo robot : rc.senseNearbyRobots(actionRadius, enemy)) {
            if (robot.type.canBeExposed()) {
                // It's a slanderer... go get them!
                if (rc.canExpose(robot.location)) {
                    //System.out.println("e x p o s e d");
                    rc.expose(robot.location);
                    return;
                }
            }
        }
        if (role == 1) {
          // move in one direction until you can't, then pick a new direction
          if (rc.isReady() && !tryMove(myDir)) {
            myDir = randomDirection();
          }
        }
        if (role == 2) {


                  if (state == "forming") {
                    myDir = rc.getLocation().directionTo(dest);
                    if (rc.getRoundNum() % 100 == 0) {
                      state = "marching";
                    } else if (myDir == Direction.CENTER) {
                      state = "waiting";
                    } else {
                      tryMove(myDir);
                    }
                  } else if (state == "waiting") {
                    if (rc.getRoundNum() % 100 == 0) {
                      state = "marching";
                    }
                  } else if (state == "marching") {
                    myDir = marchingDirection;
                    tryMove(myDir);
                  }

        }
    }

    /**
     * Updates the flag with new zone variables.
     *
     */
    static void updateFlag() throws GameActionException {
      String x = String.valueOf(nextZoneDeltaX);
      String y = String.valueOf(nextZoneDeltaY);
      int flag = Integer.parseInt(x + y);
      rc.setFlag(flag);
    }

    /**
     * Increments the nextZone variables.
     *
     */
    static void setNextZone() {
      nextZoneDeltaX += zoneSpacing;
      nextZoneDeltaY += zoneSpacing;
    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random spawnable RobotType
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnableRobotType() {
        return spawnableRobot[(int) (Math.random() * spawnableRobot.length)];
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        //System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

}
