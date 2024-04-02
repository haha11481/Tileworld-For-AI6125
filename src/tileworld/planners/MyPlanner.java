package tileworld.planners;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.Parameters;
import tileworld.agent.*;
import tileworld.environment.*;

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
  private final Region region;

  public MyPlanner(MyAgent me, TWEnvironment environment) {
    this.me = me;
    this.environment = environment;
    this.pathGenerator = new AstarPathGenerator(environment, me, Parameters.lifeTime * 3);
    this.globalVision = new TWObject[environment.getxDimension()][environment.getyDimension()];
    //暂时用名字里的数字表示序号
    int serNum = Integer.parseInt(String.valueOf(me.getName().charAt(me.getName().length())));
    //先hardcode，默认能被整除
    int length = environment.getxDimension() / 5;
    int width = environment.getyDimension();
    int top = (serNum - 1) * length;
    int bot = top + length - 1;
    this.region = new Region(top, bot, 0, width - 1);
  }

  @Override
  public TWThought generatePlan() {
    switch (curStrategy) {
      case FIND_FUEL -> {

      }
      case SCORE -> {
        if (inCurrentGoal()) {
          if (closestTile != null && closestTile.getX() == currentGoal.x && closestTile.getY() == currentGoal.y) {
            return new TWThought(TWAction.PICKUP, TWDirection.Z);
          } else if (closestHole != null && closestHole.getX() == currentGoal.x && closestHole.getY() == currentGoal.y) {
            return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
          } else {
            System.out.println("不可能！");
          }
        } else {
          return moveTo(currentGoal.x, currentGoal.y);
        }
      }
      case REFUEL -> {
        TWFuelStation fuelStation = ((MyMemory) me.getMemory()).getFuelStation();
        if (environment.inFuelStation(me)) {
          return new TWThought(TWAction.REFUEL, TWDirection.Z);
        } else {
          TWPath path = pathGenerator.findPath(me.getX(), me.getY(), fuelStation.getX(), fuelStation.getY());
          if (path == null) {
            System.out.println(me.getName() + " failed to find a path to fuel station!");
            return randomMove();
          }
          TWPathStep step = path.popNext();
          return new TWThought(TWAction.MOVE, step.getDirection());
        }
      }
      case EXPLORE -> {

      }
      case TO_REGION -> {

      }
    }
  }

  @Override
  public boolean hasPlan() {
    refresh();
    if (curStrategy != Strategy.REFUEL && !region.contains(me.getX(), me.getY())) {
      curStrategy = Strategy.TO_REGION;
      // TODO set curGoal
    } else if (((MyMemory) me.getMemory()).getFuelStation() == null) {
      curStrategy = Strategy.FIND_FUEL;
    } else if (me.needRefuel()) {
      curStrategy = Strategy.REFUEL;
      setCurrentGoal(((MyMemory) me.getMemory()).getFuelStation());
    } else {
      if (me.hasTile()) {
        if (me.tilesFull()) {
          // 拿不了更多tile时，有hole就去put，没有就explore
          if (closestHole == null) {
            curStrategy = Strategy.EXPLORE;
          } else {
            curStrategy = Strategy.SCORE;
            setCurrentGoal(closestHole);
          }
        } else {
          // 周围没tile也没hole就去explore，否则去score
          if (closestHole == null && closestTile == null) {
            curStrategy = Strategy.EXPLORE;
          } else {
            curStrategy = Strategy.SCORE;
            if (closestTile == null) {
              setCurrentGoal(closestHole);
            } else if (closestHole == null) {
              setCurrentGoal(closestTile);
            } else if (me.closerTo(closestHole, closestTile)) {
              setCurrentGoal(closestHole);
            } else {
              setCurrentGoal(closestTile);
            }
          }
        }
      } else {
        // 手里没tile，周围也没tile时，就去explore，周围有tile就去拿tile
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

  // 生成向目标Object移动的想法
  private TWThought moveTo(TWEntity entity) {
    assert entity != null;
      return moveTo(entity.getX(), entity.getY());
  }

  private TWThought moveTo(int x, int y) {
    TWPath path = pathGenerator.findPath(me.getX(), me.getY(), x, y);
    if (path == null) {
      System.out.println(me.getName() + " failed to find a path to some entity!");
      return randomMove();
    } else {
      TWPathStep step = path.popNext();
      return new TWThought(TWAction.MOVE, step.getDirection());
    }
  }

  // clear old info and receive new messages from the environment, called at the beginning of each time step
  private void refresh() {
    closestHole = null;
    closestTile = null;

    allSensedObjects.clear();
    allX.clear();
    allY.clear();
    allSensedObjects.addAll(((MyMemory) me.getMemory()).getSensedObjects());
    allX.addAll(((MyMemory) me.getMemory()).getSensedX());
    allY.addAll(((MyMemory) me.getMemory()).getSensedY());

    for (Message m : environment.getMessages()) {
      assert m instanceof MyMessage;
      MyMessage mm = (MyMessage) m;
      allSensedObjects.addAll(mm.getEntities());
      allX.addAll(mm.getX());
      allY.addAll(mm.getY());
      if (mm.getFuelStation() != null && ((MyMemory) me.getMemory()).getFuelStation() == null) {
        ((MyMemory) me.getMemory()).setFuelStation(mm.getFuelStation());
      }
    }
    updateVision(allSensedObjects, allX, allY);
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

  // remove objects that are already gone
  private void decayVision() {
    for (int x = 0; x < this.globalVision.length; x++) {
      for (int y = 0; y < this.globalVision[x].length; y++) {
        TWObject v = globalVision[x][y];
        if (v != null) {
          if (v.getTimeLeft(environment.schedule.getTime()) <= 0) {
            globalVision[x][y] = null;
          } else {
            // update the closest tile & hole
            if (v instanceof TWTile && (closestTile == null || me.closerTo(v, closestTile))) {
              closestTile = (TWTile) v;
            }
            if (v instanceof TWHole && (closestHole == null || me.closerTo(v, closestHole))) {
              closestHole = (TWHole) v;
            }
          }
        }
      }
    }
  }

  private void setCurrentGoal(TWEntity entity) {
    currentGoal = new Int2D(entity.getX(), entity.getY());
  }

  private boolean inCurrentGoal() {
    return me.getX() == currentGoal.x && me.getY() == currentGoal.y;
  }
}