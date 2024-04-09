package tileworld.planners;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.Parameters;
import tileworld.agent.*;
import tileworld.environment.*;

import java.util.ArrayList;
import java.util.Random;


public class MyPlanner implements TWPlanner{

  private final MyAgent me;
  private final TWEnvironment environment;
  private final AstarPathGenerator pathGenerator;
  private final Bag allSensedObjects = new Bag();
  private final IntBag allX = new IntBag();
  private final IntBag allY = new IntBag();
  private Int2D currentGoal = null;
  private final TWObject[][] globalVision;
  public TWHole closestHole = null;
  public TWTile closestTile = null;
  private Strategy curStrategy = null;
  private Region region;

  // positions of other agents
  private final ArrayList<Int2D> othersPos = new ArrayList<>();
  // current goals of other agents
  private final ArrayList<Int2D> othersGoal = new ArrayList<>();
  // strategies of other agents
  private final ArrayList<Strategy> othersStrategy = new ArrayList<>();
  // objects that are removed by other agents through pick up / put down
  private final ArrayList<TWEntity> othersRemove = new ArrayList<>();
  // unique number of other agents, same number indicates same agent
  private final ArrayList<Integer> othersSerNum = new ArrayList<>();
  // current regions of other agents, used for region swapping
  private final ArrayList<Region> othersRegion = new ArrayList<>();
  private int distanceThreshold;
  private int sight;
  private final Region[] regions = new Region[5];
  private boolean hasRedistributed = false;

  public MyPlanner(MyAgent me, TWEnvironment environment) {
    this.me = me;
    this.environment = environment;
    // we always want a path no matter how long it is
    this.pathGenerator = new AstarPathGenerator(environment, me, Integer.MAX_VALUE);
    this.globalVision = new TWObject[environment.getxDimension()][environment.getyDimension()];
    // currently, use the number in agent name as unique number
    int serNum = me.getSerNum();
    buildRegions(PlannerParams.region_overlap);

    // set the 5th agent as explorer
    if (serNum != 5) {
      normalSetup();
    } else {
      explorerSetup();
    }
    this.region = regions[serNum - 1];
  }

  // set up for normal agents
  private void normalSetup() {
    this.distanceThreshold = PlannerParams.normal_distanceThreshold;
    this.sight = PlannerParams.normal_sight;
    //this.sight = me.getSerNum() + 2;
  }

  // set up for explorer agents
  private void explorerSetup() {
    this.distanceThreshold = PlannerParams.explorer_distanceThreshold;
    this.sight = PlannerParams.explorer_sight;
  }

  /**
   * split the map into 4 small squares + 1 explorer region
   * @param overlap size of overlapping area between different regions
   */
  private void buildRegions(int overlap) {
    int border = PlannerParams.explorer_border;
    int length = environment.getxDimension() / 2;
    int width = environment.getyDimension() / 2;
    regions[0] = new Region(0, length - 1 + overlap, 0, width - 1 + overlap);
    regions[1] = new Region(0, length - 1 + overlap, width - overlap, 2 * width - 1);
    regions[2] = new Region(length - overlap, 2 * length - 1, 0, width - 1 + overlap);
    regions[3] = new Region(length - overlap, 2 * length - 1, width - overlap, 2 * width - 1);
    regions[4] = new Region(0 + border, 2 * length - 1 - border, 0 + border, 2 * width - 1 - border);
  }

  // another region distribution
  private void buildRegions2(int overlap) {
    int border = 0;
    int length = environment.getxDimension();
    int width = environment.getyDimension() / 2;
    regions[0] = new Region(0, length - 1 + overlap, 0, width - 1 + overlap);
    regions[1] = new Region(0, length - 1 + overlap, width - overlap, 2 * width - 1);
    regions[2] = new Region(0 + border, length/3 - 1 - border, 0 + border, 2 * width - 1 - border);
    regions[3] = new Region(0 + length/3 - border, length*2/3 - 1 - border, 0 + border, 2 * width - 1 - border);
    regions[4] = new Region(0 + length*2/3 - border, length - 1 - border, 0 + border, 2 * width - 1 - border);
  }

  // yet another region distribution, evenly split the map into 5 regions
  private void buildRegions1(int overlap) {
    int length = environment.getxDimension() / 5;
    int width = environment.getyDimension();
    for (int i = 0; i < 5;  i ++) {
      regions[i] = new Region(Math.max(0, i * length - overlap), Math.min((i + 1) * length - 1 + overlap, environment.getxDimension() - 1), 0, width - 1);
    }
  }

  // no region splitting, all agents work on the whole map
  private void buildRegions0(int overlap) {
    int length = environment.getxDimension();
    int width = environment.getyDimension();
    for (int i = 0; i < 5;  i ++) {
      regions[i] = new Region(0, length - 1, 0, width - 1);
    }
  }

  @Override
  public TWThought generatePlan() {
    switch (curStrategy) {
      case FIND_FUEL -> {
        if (!region.contains(me.getX(), me.getY())) {
          // move back to your own region first
          curStrategy = Strategy.TO_REGION;
          return returnToRegion();
        }
        if (!inCurrentGoal() && currentGoal != null && moveTo(currentGoal.x, currentGoal.y) != null) {
          return moveTo(currentGoal.x, currentGoal.y);
        } else {
          String direction = region.getScanDirection(me);
          return moveTowards(direction);
        }
      }
      case SCORE -> {
        if (inCurrentGoal()) {
          curStrategy = null;
          if (closestTile != null && closestTile.getX() == currentGoal.x && closestTile.getY() == currentGoal.y) {
            return new TWThought(TWAction.PICKUP, TWDirection.Z);
          } else if (closestHole != null && closestHole.getX() == currentGoal.x && closestHole.getY() == currentGoal.y) {
            return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
          } else {
            System.out.println("Impossible!");
            return randomMove();
          }
        } else {
          TWThought thought = moveTo(currentGoal.x, currentGoal.y);
          if (thought == null) {
            System.out.println("NO! This should not happen!");
            return randomMove();
          } else {
            return thought;
          }
        }
      }
      case REFUEL -> {
        if (inCurrentGoal()) {
          curStrategy = null;
          return new TWThought(TWAction.REFUEL, TWDirection.Z);
        } else {
          TWThought thought = moveTo(currentGoal.x, currentGoal.y);
          if (thought == null) {
            System.out.println(me.getName() + " failed to find a path to fuel station!");
            return randomMove();
          } else {
            return thought;
          }
        }
      }
      case EXPLORE -> {
        if (!region.contains(me.getX(), me.getY())) {
          // move back to your own region first, should not explore other's region
          curStrategy = Strategy.TO_REGION;
          return returnToRegion();
        }
        String direction = region.getExploreDirection(me, environment.schedule.getTime(), sight);
        return moveTowards(direction);
      }
      case TO_REGION -> {
        return returnToRegion();
      }
      default -> {
        System.out.println("No such strategy: " + curStrategy);
        return randomMove();
      }
    }
  }

  @Override
  public boolean hasPlan() {
    refresh();
    TWFuelStation fuelStation = ((MyMemory) me.getMemory()).getFuelStation();
    if (fuelStation != null) {
      // might need reassign regions after we find the fuel station
      if (!hasRedistributed) {
        buildRegions(PlannerParams.region_overlap + (int) (environment.getxDimension() * 0.25));
        Region r = regions[me.getSerNum() - 1];
        Region.copyScannedMatrix(region, r);
        hasRedistributed = true;
        this.region = r;
      }
    }

    if (needRefuel() && fuelStation != null) {
      curStrategy = Strategy.REFUEL;
      setCurrentGoal(fuelStation);
    } else if (fuelStation == null && !region.exploited() && me.getSerNum() != 5) {
      // if fuel station not found, you should scan your region until it is exploited
      // since we only split the map into 4 regions at the beginning, the 5th agent does not need to find fuel
      curStrategy = Strategy.FIND_FUEL;
    } else {
      // explore the map or try to get score
      if (me.hasTile()) {
        if (me.countTiles() >= 3) {
          // if you cannot take more tiles，go put down if there are nearby holes，else explore
          if (closestHole == null) {
            curStrategy = Strategy.EXPLORE;
          } else {
            curStrategy = Strategy.SCORE;
            setCurrentGoal(closestHole);
          }
        } else {
          //  if there are no nearby holes or tiles, go explore，else go get scores
          if (closestHole == null && closestTile == null) {
            curStrategy = Strategy.EXPLORE;
          } else {
            curStrategy = Strategy.SCORE;
            if (closestTile == null) {
              setCurrentGoal(closestHole);
            } else if (closestHole == null) {
              setCurrentGoal(closestTile);
            } else if (me.closerTo(closestTile, closestHole)) {
              setCurrentGoal(closestTile);
            } else {
              setCurrentGoal(closestHole);
            }
          }
        }
      } else {
        // you have no tile in hand，if there are no tiles nearby，go explore，else go pick up tiles
        if (closestTile == null) {
          curStrategy = Strategy.EXPLORE;
        } else {
          curStrategy = Strategy.SCORE;
          setCurrentGoal(closestTile);
        }
      }
    }
    return true;
  }

  // TODO decide when shall we navigate to fuelStation, need better criteria
  /**
   * @return do we want to refuel now
   */
  public boolean needRefuel() {
    TWFuelStation fuelStation = ((MyMemory) me.getMemory()).getFuelStation();
    return fuelStation != null && me.getFuelLevel() < Parameters.endTime - me.getEnvironment().schedule.getTime() &&
            (fuelStation.getDistanceTo(me) + PlannerParams.force_refuel_threshold >= me.getFuelLevel() ||
                    (me.getDistanceTo(fuelStation) <= PlannerParams.refuel_distanceThreshold &&
                    me.getFuelLevel() < PlannerParams.refuel_fuelLevelThreshold));
  }

  @Override
  public TWThought voidPlan() {
    return null;
  }

  @Override
  public Int2D getCurrentGoal() {
    return this.currentGoal;
  }

  @Override
  public TWDirection execute() {
    return null;
  }

  // called only if we don't know what to do
  private TWThought randomMove() {
    return new TWThought(TWAction.MOVE, getRandomDirection());
  }

  private TWDirection getRandomDirection() {
    TWDirection randomDir = TWDirection.values()[environment.random.nextInt(5)];

    if (me.getX() >= environment.getxDimension()) {
      randomDir = TWDirection.W;
    } else if (me.getX() <= 1) {
      randomDir = TWDirection.E;
    } else if (me.getY() <= 1) {
      randomDir = TWDirection.S;
    } else if (me.getY() >= environment.getxDimension()) {
      randomDir = TWDirection.N;
    }

    return randomDir;
  }

  // move to some entity
  private TWThought moveTo(TWEntity entity) {
    assert entity != null;
      return moveTo(entity.getX(), entity.getY());
  }

  // move towards certain direction, if the cell is blocked, move a direction further
  // TODO theoretically there is chance for this method to throw IndexOutOfBoundException, need fix
  private TWThought moveTowards(String direction) {
    TWThought thought = null;
    int x = me.getX();
    int y = me.getY();
    switch (direction) {
      case "left" -> {
        while (thought == null) {
          y -= 1;
          thought = moveTo(x, y);
        }
      }
      case "right" -> {
        while (thought == null) {
          y += 1;
          thought = moveTo(x, y);
        }
      }
      case "up" -> {
        while (thought == null) {
          x -= 1;
          thought = moveTo(x, y);
        }
      }
      case "down" -> {
        while (thought == null) {
          x += 1;
          thought = moveTo(x, y);
        }
      }
      case "all_done" -> {
        System.out.println(me.getName() + " has scanned its whole region!");
        return randomMove();
      }
    }
    currentGoal = new Int2D(x, y);
    return thought;
  }

  private TWThought moveTo(int x, int y) {
    TWPath path = pathGenerator.findPath(me.getX(), me.getY(), x, y);
    if (path == null) {
      return null;
    } else {
      TWPathStep step = path.popNext();
      return new TWThought(TWAction.MOVE, step.getDirection());
    }
  }

  private void clearOldInfo() {
    closestHole = null;
    closestTile = null;

    allSensedObjects.clear();
    allX.clear();
    allY.clear();
    othersPos.clear();
    othersGoal.clear();
    othersStrategy.clear();
    othersRemove.clear();
    othersSerNum.clear();
    othersRegion.clear();
  }

  // clear old info and receive new messages from the environment, called at the beginning of each time step
  private void refresh() {
    clearOldInfo();

    for (Message m : environment.getMessages()) {
      assert m instanceof MyMessage;
      MyMessage mm = (MyMessage) m;
      region.update(mm.getAgentPos().x, mm.getAgentPos().y, me.getEnvironment().schedule.getTime());
      allSensedObjects.addAll(mm.getEntities());
      allX.addAll(mm.getX());
      allY.addAll(mm.getY());
      othersPos.add(mm.getAgentPos());
      othersGoal.add(mm.getCurrentGoal());
      othersStrategy.add(mm.getStrategy());
      othersRemove.add(mm.getRemovedObj());
      othersSerNum.add(mm.getSerNum());
      othersRegion.add(mm.getRegion());
      if (mm.getFuelStation() != null && ((MyMemory) me.getMemory()).getFuelStation() == null) {
        ((MyMemory) me.getMemory()).setFuelStation(mm.getFuelStation());
      }
    }
    if (PlannerParams.enable_region_swapping) {
      swapRegion();
    }
    updateVision(allSensedObjects, allX, allY);
  }

  private void swapRegion() {
    improveRegionDistribute();
    for (int i = 0; i < othersSerNum.size(); i ++) {
      if (othersSerNum.get(i) == me.getSerNum()) {
        region = othersRegion.get(i);
      }
    }

  }

  // update the agent's recognition of the world based on all the information (from its memory + from other agents)
  private void updateVision(Bag entities, IntBag x, IntBag y) {
    assert entities.size() == x.size() && x.size() == y.size();
    for (int i = 0; i < entities.size(); i++) {
      if (entities.get(i) == null) {
        globalVision[x.get(i)][y.get(i)] = null;
      } else {
        if (!(entities.get(i) instanceof TWObject)) {
          continue;
        }
        globalVision[x.get(i)][y.get(i)] = (TWObject) entities.get(i);
      }
    }
    decayVision();
  }

  // remove objects that are already gone or will be gone when we reach them
  private void decayVision() {
    for (int x = 0; x < this.globalVision.length; x++) {
      for (int y = 0; y < this.globalVision[x].length; y++) {
        TWObject v = globalVision[x][y];
        if (v != null) {
          if (canForget(globalVision[x][y])) {
            globalVision[x][y] = null;
          }
        }
      }
    }
    // remove tiles/holes that are removed by other agents
    for (TWEntity entity : othersRemove) {
      if (entity != null) {
        globalVision[entity.getX()][entity.getY()] = null;
        me.getMemory().removeObject(entity);
      }
    }
    // renew the closest tile and hole
    updateClosest();
  }

  public void removeObject(TWEntity entity) {
    if (entity != null) {
      globalVision[entity.getX()][entity.getY()] = null;
    }
  }

  private void setCurrentGoal(TWEntity entity) {
    currentGoal = new Int2D(entity.getX(), entity.getY());
  }

  private void setCurrentGoalByLocation(int x, int y) {
    currentGoal = new Int2D(x, y);
  }

  // return true if current position is the same as current goal
  private boolean inCurrentGoal() {
    return currentGoal != null && me.getX() == currentGoal.x && me.getY() == currentGoal.y;
  }

  // only used in the early version of explore strategy
  @Deprecated
  private void setRandomGoal() {
    Random rand = new Random();
    int randomX = rand.nextInt(region.top, region.bot + 1);
    int randomY = rand.nextInt(region.left, region.right + 1);
    setCurrentGoalByLocation(randomX, randomY);
  }

  // not efficient，iterate through the whole map to set the closest tile/hole in each time step
  // a tile/hole is considered valid only if:
  // no conflict with others, and distance < threshold, and time left > distance
  private void updateClosest() {
    closestTile = null;
    closestHole = null;
    for (int i = 0; i < globalVision.length; i ++) {
      for (int j = 0; j < globalVision[0].length; j ++) {
        if (globalVision[i][j] != null && !hasConflict(globalVision[i][j])) {
          if (globalVision[i][j] instanceof TWTile) {
            if (me.getDistanceTo(i, j) <= distanceThreshold) {
              closestTile = (TWTile) priority(closestTile, globalVision[i][j]);
            }
          }

          if (globalVision[i][j] instanceof TWHole) {
            if (me.getDistanceTo(i, j) <= distanceThreshold) {
              closestHole = (TWHole) priority(closestHole, globalVision[i][j]);
            }
          }
        }
      }
    }
  }

  // for nearby objects a and b, if b will disappear if we go to a first, but a won't disappear if we go to b first,
  // then we assume b has a higher priority
  public TWObject priority(TWObject a, TWObject b) {
    if (a == null && b == null) {
      return null;
    } else if (a == null) {
      return b;
    } else if (b == null) {
      return a;
    }

    // 2 is the time cost for pickup / put down
    double dab = me.getDistanceTo(a) + a.getDistanceTo(b) + 2;
    double dba = me.getDistanceTo(b) + b.getDistanceTo(a) + 2;
    double ta = a.getTimeLeft(environment.schedule.getTime());
    double tb = b.getTimeLeft(environment.schedule.getTime());

    if ((ta < dba && tb < dab) || (ta >= dba && tb >= dab)) {
      return me.closerTo(a, b) ? a : b;
    } else {
      return ta < dba ? a : b;
    }
  }

  public Strategy getCurStrategy() {
    return curStrategy;
  }

  // return if the current (tile/hole) has conflict with other agents
  private boolean hasConflict(TWEntity entity) {
    boolean conflict = false;
    for (int i = 0; i < othersGoal.size(); i ++) {
      if (othersGoal.get(i) != null && othersStrategy.get(i) == Strategy.SCORE && othersSerNum.get(i) != me.getSerNum()) {
        int ox = othersGoal.get(i).x;
        int oy = othersGoal.get(i).y;
        if (entity.getX() == ox && entity.getY() == oy) {
          int agentX = othersPos.get(i).x;
          int agentY = othersPos.get(i).y;
          if (entity.getDistanceTo(agentX, agentY) < entity.getDistanceTo(me.getX(), me.getY())) {
            conflict = true;
          } else if (entity.getDistanceTo(agentX, agentY) == entity.getDistanceTo(me.getX(), me.getY())) {
            // if distances are the same, the agent with larger unique number will take the goal
            conflict = me.getSerNum() < othersSerNum.get(i);
          }
        }
      }
    }
    return conflict;
  }

  // try moving back to the assigned region
  private TWThought returnToRegion() {
    if (me.getX() > region.bot) {
      int targetX = region.bot;
      TWThought thought = moveTo(targetX, me.getY());
      while (thought == null) {
        targetX -= 1;
        thought = moveTo(targetX, me.getY());
      }
      return thought;
    } else if (me.getX() < region.top) {
      int targetX = region.top;
      TWThought thought = moveTo(targetX, me.getY());
      while (thought == null) {
        targetX += 1;
        thought = moveTo(targetX, me.getY());
      }
      return thought;
    } else if (me.getY() < region.left) {
      int targetY = region.left;
      TWThought thought = moveTo(me.getX(), targetY);
      while (thought == null) {
        targetY += 1;
        thought = moveTo(me.getX(), targetY);
      }
      return thought;
    } else if (me.getY() > region.right) {
      int targetY = region.right;
      TWThought thought = moveTo(me.getX(), targetY);
      while (thought == null) {
        targetY -= 1;
        thought = moveTo(me.getX(), targetY);
      }
      return thought;
    } else {
      System.out.println("Impossible!");
      return randomMove();
    }
  }

  public Region getRegion() {
    return region;
  }

  // swap regions，until the sum of the distance of each agent to its assigned region centre is lowest
  private void improveRegionDistribute() {
    boolean improve = true;
    int originaltd = totalDistanceToCentre();
    while (improve) {
      improve = false;
      int td = totalDistanceToCentre();
      for (int i = 0; i < othersRegion.size(); i++) {
        for (int j = i + 1; j < othersRegion.size(); j++) {
          if (othersSerNum.get(i) == 5 || othersSerNum.get(j) == 5) {
            continue;
          }
          Region r1 = othersRegion.get(i);
          Region r2 = othersRegion.get(j);
          othersRegion.set(i, r2);
          othersRegion.set(j, r1);
          if (totalDistanceToCentre() < td && totalDistanceToCentre() < originaltd) {
            improve = true;
          } else {
            othersRegion.set(i, r1);
            othersRegion.set(j, r2);
          }
        }
      }
    }
  }

  // calculate the sum of distance of each agent to its assigned region centre，smaller sum indicates better assignment
  private int totalDistanceToCentre() {
    int res = 0;
    for (int i = 0; i < othersRegion.size(); i ++) {
      res += othersRegion.get(i).getDistanceToCentre(othersPos.get(i).x, othersPos.get(i).y);
    }
    return res;
  }

  // return if we can ignore such object in our vision
  private boolean canForget(TWObject object) {
    if (object instanceof TWObstacle) {
      return object.getTimeLeft(me.getEnvironment().schedule.getTime()) < me.getDistanceTo(object.getX(), object.getY());
    } else {
      return object.getTimeLeft(me.getEnvironment().schedule.getTime()) <= me.getDistanceTo(object.getX(), object.getY());
    }
  }
}