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

  public MyPlanner(MyAgent me, TWEnvironment environment) {
    this.me = me;
    this.environment = environment;
    this.pathGenerator = new AstarPathGenerator(environment, me, Parameters.lifeTime * 3);
    this.globalVision = new TWObject[environment.getxDimension()][environment.getyDimension()];
  }

  @Override
  public TWThought generatePlan() {
    try {
      if (me.needRefuel()) {
        TWFuelStation fuelStation = ((MyMemory) me.getMemory()).getFuelStation();

        // 如果已经在加油站了，就加油，否则生成去加油站的最短路径然后移动
        if (environment.inFuelStation(me)) {
          System.out.println("Arrive at fuel Station!");
          return new TWThought(TWAction.REFUEL, TWDirection.Z);
        } else {
          TWPath path = pathGenerator.findPath(me.getX(), me.getY(), fuelStation.getX(), fuelStation.getY());
          TWPathStep step = path.popNext();
          return new TWThought(TWAction.MOVE, step.getDirection());
        }
      } else {
        TWEntity hole = closestHole;
        TWEntity tile = closestTile;

        // 处理视野里没有tile或hole的情况
        if (tile == null && hole == null) {
          return randomMove();
        } else if (tile == null) {
          if (me.hasTile()) {
            return moveTo(hole);
          } else {
            return randomMove();
          }
        } else if (hole == null) {
          if (me.tilesFull()) {
            return randomMove();
          } else if (me.getX() == tile.getX() && me.getY() == tile.getY()) {
            return new TWThought(TWAction.PICKUP, TWDirection.Z);
          } else {
            return moveTo(tile);
          }
        }

        // 简单规则: 手上有tile且在hole中就put，手上tile不满且在tile中就pickup，否则寻找最近的tile/hole
        if (me.getX() == tile.getX() && me.getY() == tile.getY() && !me.tilesFull()) {
          return new TWThought(TWAction.PICKUP, TWDirection.Z);
        } else if (me.getX() == hole.getX() && me.getY() == hole.getY() && me.hasTile()) {
          return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
        } else {
          // 离hole更近且手里有tile，或者手里tile满了，就去hole，否则去拿tile
          if ((me.getDistanceTo(hole) <= me.getDistanceTo(tile) && me.hasTile()) || me.tilesFull()) {
            return moveTo(hole);
          } else {
            return moveTo(tile);
          }
        }
      }
    } catch (Exception e) {
      // TODO 除了路径生成失败会报NPE，还可能有哪些报错，应该如何处理？全放在这里catch显然不行
      return randomMove();
    }
  }

  @Override
  public boolean hasPlan() {
    refresh();
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
    TWPath path = pathGenerator.findPath(me.getX(), me.getY(), entity.getX(), entity.getY());
    if (path == null) {
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
}