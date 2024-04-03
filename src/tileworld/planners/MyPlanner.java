package tileworld.planners;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.Parameters;
import tileworld.agent.*;
import tileworld.environment.*;

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

  public MyPlanner(MyAgent me, TWEnvironment environment) {
    this.me = me;
    this.environment = environment;
    this.pathGenerator = new AstarPathGenerator(environment, me, Parameters.lifeTime * 3);
    this.globalVision = new TWObject[environment.getxDimension()][environment.getyDimension()];
    //暂时用名字里的数字表示序号
    int serNum = Integer.parseInt(String.valueOf(me.getName().charAt(me.getName().length() - 1)));
    //先hardcode，默认能被整除
    int length = environment.getxDimension() / 5;
    int width = environment.getyDimension();
    int top = (serNum - 1) * length;
    int bot = top + length - 1;
    this.region = new Region(top, bot, 0, width - 1);
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
        if (currentGoal == null || inCurrentGoal()) {
          setRandomGoal();
        }
        TWPath path = pathGenerator.findPath(me.getX(), me.getY(), currentGoal.x, currentGoal.y);;
        int maxIteration = 0;
        // 最多重试10次，如果找不到路径就算了
        while (path == null && maxIteration < 10) {
          setRandomGoal();
          path = pathGenerator.findPath(me.getX(), me.getY(), currentGoal.x, currentGoal.y);
          maxIteration ++;
        }
        if (path == null) {
          System.out.println(me.getName() + " failed to find a path to the random point!" + me.getX() + " " + me.getY() +
                  " " + currentGoal.x + " " + currentGoal.y);
          return randomMove();
        }
        TWPathStep step = path.popNext();
        return new TWThought(TWAction.MOVE, step.getDirection());
      }
      case TO_REGION -> {
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
      default -> {
        System.out.println("No such strategy: " + curStrategy);
        return randomMove();
      }
    }
  }

  @Override
  public boolean hasPlan() {
    refresh();
    // 如果你不是去加油的，也不在自己的区域里，就赶快回到自己的区域
    // 是否允许跨区域得分？目前允许的话结果更好
    if (curStrategy != Strategy.REFUEL && curStrategy != Strategy.SCORE && curStrategy != Strategy.FIND_FUEL && !region.contains(me.getX(), me.getY())) {
      curStrategy = Strategy.TO_REGION;
      //System.out.println(region.top + " " + region.bot + " " + region.left + " " + region.right + " " + me.getX() + " " + me.getY());
    } else if (((MyMemory) me.getMemory()).getFuelStation() == null && !region.exploited()) {
      // 刚开始，如果没找到加油站且自己的区域还没探索完，就先找加油站
      curStrategy = Strategy.FIND_FUEL;
    } else if (me.needRefuel() && ((MyMemory) me.getMemory()).getFuelStation() != null) {
      // 需要加油且找到了加油站就去加油
      curStrategy = Strategy.REFUEL;
      setCurrentGoal(((MyMemory) me.getMemory()).getFuelStation());
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

  // 向指定方向移动，因为该方法只会在scan时调用，所以应该不需要考虑移出地图的情况
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

  // clear old info and receive new messages from the environment, called at the beginning of each time step
  private void refresh() {
    region.update(me);
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
        if (region.contains(x.get(i), y.get(i))) {
          globalVision[x.get(i)][y.get(i)] = (TWObject) entities.get(i);
        }
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
  private void updateClosest() {
    closestTile = null;
    closestHole = null;
    for (int i = 0; i < globalVision.length; i ++) {
      for (int j = 0; j < globalVision[0].length; j ++) {
        if (globalVision[i][j] != null) {
          if (globalVision[i][j] instanceof TWTile) {
            if (closestTile == null || me.closerTo(globalVision[i][j], closestTile) && me.getDistanceTo(globalVision[i][j]) <= globalVision[i][j].getTimeLeft(environment.schedule.getTime())) {
              closestTile = (TWTile) globalVision[i][j];
            }
          }

          if (globalVision[i][j] instanceof TWHole) {
            if (closestHole == null || me.closerTo(globalVision[i][j], closestHole) && me.getDistanceTo(globalVision[i][j]) <= globalVision[i][j].getTimeLeft(environment.schedule.getTime())) {
              closestHole = (TWHole) globalVision[i][j];
            }
          }
        }
      }
    }
  }
}