package tileworld.planners;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.agent.*;
import tileworld.environment.*;

import java.util.ArrayList;
import java.util.Objects;
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
  private final Region region;

  // 其他agent的位置
  private final ArrayList<Int2D> othersPos = new ArrayList<>();
  // 其他agent的goal
  private final ArrayList<Int2D> othersGoal = new ArrayList<>();
  // 其他agent的strategy
  private final ArrayList<Strategy> othersStrategy = new ArrayList<>();
  // 其他agent remove掉的object
  private final ArrayList<TWEntity> othersRemove = new ArrayList<>();
  // 其他agent的序号，用来判断这个消息是不是自己发的
  private final ArrayList<Integer> othersSerNum = new ArrayList<>();

  // 当前目标的最大距离限制，超过该距离就不设为目标了
  private final int distanceThreshold = 12;

  public MyPlanner(MyAgent me, TWEnvironment environment) {
    this.me = me;
    this.environment = environment;
    this.pathGenerator = new AstarPathGenerator(environment, me, 10000000);
    this.globalVision = new TWObject[environment.getxDimension()][environment.getyDimension()];
    //暂时用名字里的数字表示序号
    int serNum = me.getSerNum();
//    int length = environment.getxDimension() / 5;
//    int width = environment.getyDimension();
//    int top = (serNum - 1) * length;
//    int bot = top + length - 1;
//    this.region = new Region(top, bot, 0, width - 1);
    //先hardcode，默认能被整除
    int length = environment.getxDimension() / 2;
    int width = environment.getyDimension() / 2;
    switch (serNum) {
      case 1 -> {
        this.region = new Region(0, length - 1, 0, width - 1);
      }
      case 2 -> {
        this.region = new Region(0, length - 1, width, 2 * width - 1);
      }
      case 3 -> {
        this.region = new Region(length, 2 * length - 1, 0, width - 1);
      }
      case 4 -> {
        this.region = new Region(length, 2 * length - 1, width, 2 * width - 1);
      }
      default -> {
        this.region = new Region(0, 2 * length - 1, 0, 2 * width - 1);
      }
    }
  }

  @Override
  public TWThought generatePlan() {
    //System.out.println(me.getName() + " strategy: " + curStrategy);
    switch (curStrategy) {
      case FIND_FUEL -> {
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
          return Objects.requireNonNullElseGet(thought, this::randomMove);
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
          // 别人的区域就别explore了
          curStrategy = Strategy.TO_REGION;
          return returnToRegion();
        }
        String direction = region.getExploreDirection(me, environment.schedule.getTime());
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

    if (me.needRefuel() && ((MyMemory) me.getMemory()).getFuelStation() != null) {
      // 需要加油且找到了加油站就去加油
      curStrategy = Strategy.REFUEL;
      setCurrentGoal(((MyMemory) me.getMemory()).getFuelStation());
    } else if (curStrategy != Strategy.SCORE && curStrategy != Strategy.FIND_FUEL && !region.contains(me.getX(), me.getY())) {
      // 如果你不是为了得分或者找加油站而跑到了自己的区域外，就赶紧回去
      curStrategy = Strategy.TO_REGION;
      //System.out.println(region.top + " " + region.bot + " " + region.left + " " + region.right + " " + me.getX() + " " + me.getY());
    } else if (((MyMemory) me.getMemory()).getFuelStation() == null && !region.exploited()) {
      // 刚开始，如果没找到加油站且自己的区域还没探索完，就先找加油站
      curStrategy = Strategy.FIND_FUEL;
    } else {
      // 探索地图 or 尝试得分
      if (me.hasTile()) {
        if (me.tilesFull()) {
          // 拿不了更多tile时，有hole就去put，没有就explore
          if (closestHole == null) {
            // 如果前一个状态不是explore，就重设一下目的地，防止卡住
            if (curStrategy != Strategy.EXPLORE) {
              setRandomGoal();
            }
            curStrategy = Strategy.EXPLORE;
          } else {
            curStrategy = Strategy.SCORE;
            setCurrentGoal(closestHole);
          }
        } else {
          // 周围没tile也没hole就去explore，否则去score
          if (closestHole == null && closestTile == null) {
            if (curStrategy != Strategy.EXPLORE) {
              setRandomGoal();
            }
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
          if (curStrategy != Strategy.EXPLORE) {
            setRandomGoal();
          }
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

  // 向指定方向移动，应该不需要考虑移出地图的情况
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
          }
        }
      }
    }
    // 删除其他agent移出的obj
    for (TWEntity entity : othersRemove) {
      if (entity != null) {
        globalVision[entity.getX()][entity.getY()] = null;
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

  private boolean inCurrentGoal() {
    return currentGoal != null && me.getX() == currentGoal.x && me.getY() == currentGoal.y;
  }

  private void setRandomGoal() {
    Random rand = new Random();
    int randomX = rand.nextInt(region.top, region.bot + 1);
    int randomY = rand.nextInt(region.left, region.right + 1);
    setCurrentGoalByLocation(randomX, randomY);
  }

  // 低效方法，每个time step都遍历整个地图来更新离自己最近的tile和hole
  // 如果和其他人目标有冲突，就不更新，超过最大距离限制也不更新，剩余存活时间过短也不更新
  private void updateClosest() {
    closestTile = null;
    closestHole = null;
    for (int i = 0; i < globalVision.length; i ++) {
      for (int j = 0; j < globalVision[0].length; j ++) {
        if (globalVision[i][j] != null && !hasConflict(globalVision[i][j])) {
          if (globalVision[i][j] instanceof TWTile) {
            if ((closestTile == null || me.closerTo(globalVision[i][j], closestTile)) &&
                    me.getDistanceTo(i, j) < globalVision[i][j].getTimeLeft(environment.schedule.getTime())) {
              if (me.getDistanceTo(i, j) <= distanceThreshold) {
                closestTile = (TWTile) globalVision[i][j];
              }
            }
          }

          if (globalVision[i][j] instanceof TWHole) {
            if ((closestHole == null || me.closerTo(globalVision[i][j], closestHole)) &&
                    me.getDistanceTo(i, j) < globalVision[i][j].getTimeLeft(environment.schedule.getTime())) {
              if (me.getDistanceTo(i, j) <= distanceThreshold) {
                closestHole = (TWHole) globalVision[i][j];
              }
            }
          }
        }
      }
    }
  }

  public Strategy getCurStrategy() {
    return curStrategy;
  }

  // 返回当前目标(tile/hole)是否会和其他agent冲突
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
            if (!region.contains(entity.getX(), entity.getY())) {
              conflict = true;
            }
          }
        }
      }
    }
    return conflict;
  }

  // 尝试回到自己所属的区域
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
}