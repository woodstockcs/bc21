package fanandflag;
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
    static MapLocation enemyEnglightenmentCenter;
    static MapLocation spawnLocation;
    static MapLocation dest;
    static Direction marchingDirection = Direction.WEST;
    static Direction myDirection;
    static int myFlag;
    static String state;
    static int nextZoneDeltaX;
    static int nextZoneDeltaY;

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
        RobotType toBuild = RobotType.MUCKRAKER;
        int influence = 3;
        for (Direction dir : directions) {
            if (rc.canBuildRobot(toBuild, dir, influence)) {
                rc.buildRobot(toBuild, dir, influence);
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
        if (spawnLocation == null) {
          spawnLocation = new MapLocation(rc.getLocation().x, rc.getLocation().y);
          dest = new MapLocation(
            spawnLocation.x - 3,
            spawnLocation.y + (int)(Math.random() * 10) - 5
          );
        }

        if (state == "forming") {
          myDirection = rc.getLocation().directionTo(dest);
          if (rc.getRoundNum() % 50 == 0) {
            state = "marching";
          } else if (myDirection == Direction.CENTER) {
            state = "waiting";
          } else {
            tryMove(myDirection);
          }
        } else if (state == "waiting") {
          if (rc.getRoundNum() % 50 == 0) {
            state = "marching";
          }
        } else if (state == "marching") {
          myDirection = marchingDirection;
          tryMove(myDirection);
        }


    }

    static void runSlanderer() throws GameActionException {

      // create memory when born
      if (spawnLocation == null) {
        spawnLocation = new MapLocation(rc.getLocation().x, rc.getLocation().y);
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
            dest = new MapLocation(spawnLocation.x + dx, spawnLocation.y + dy);
          }
        }
      }
      if (dest != null) {
        myDirection = rc.getLocation().directionTo(dest);
        tryMove(myDirection);
      } else {
        tryMove(randomDirection());
      }

    }

    static void runMuckraker() throws GameActionException {

      // when I'm born, pick a direction
      if (myDirection == null) {
        myDirection = randomDirection();
      }

      // if I haven't seen anything yet, move and look
      if (myFlag == 0) {
        // move in one direction until I can't, then pick a new direction
        if (rc.isReady() && !tryMove(myDirection)) {
          myDirection = randomDirection();
        }

        // if I see an enemyEnglightenmentCenter, raise flag 100
        Team myTeam = rc.getTeam();
        Team enemy = myTeam.opponent();
        for (RobotInfo robot : rc.senseNearbyRobots()) {
          if (robot.type == RobotType.ENLIGHTENMENT_CENTER && robot.team == enemy) {
            rc.setFlag(100);
            myFlag = 100;
          }
        }

        // if I see a friendly flag 100, raise flag 200
        for (RobotInfo robot : rc.senseNearbyRobots()) {
          if (robot.team == myTeam && rc.getFlag(robot.ID) == 100) {
            rc.setFlag(200);
            myFlag = 200;
          }
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
