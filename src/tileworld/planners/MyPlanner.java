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
  // 其他agent的区域，是否可以两两交换达到最高效率
  private final ArrayList<Region> othersRegion = new ArrayList<>();
  private int distanceThreshold;
  private boolean explorer = false;
  private int sight;
  private final Region[] regions = new Region[5];

  public MyPlanner(MyAgent me, TWEnvironment environment) {
    this.me = me;
    this.environment = environment;
    this.pathGenerator = new AstarPathGenerator(environment, me, Integer.MAX_VALUE);
    this.globalVision = new TWObject[environment.getxDimension()][environment.getyDimension()];
    //暂时用名字里的数字表示序号
    int serNum = me.getSerNum();
    buildRegions(PlannerParams.region_overlap);

    // 决定agent的类型
    switch (serNum) {
      case 5 -> {
        this.region = regions[serNum - 1];
        explorerSetup();
      }
      default -> {
        normalSetup();
        this.region = regions[serNum - 1];
      }
    }
  }

  // 普通agent的参数配置
  private void normalSetup() {
    this.explorer = false;
    this.distanceThreshold = PlannerParams.normal_distanceThreshold;
    this.sight = PlannerParams.normal_sight;
  }

  // explorer的参数配置
  private void explorerSetup() {
    this.explorer = true;
    this.distanceThreshold = PlannerParams.explorer_distanceThreshold;
    this.sight = PlannerParams.explorer_sight;
  }

  // 田字格分区 + 一个explorer，overlap代表每个分区向外延申多少格
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

  // 瞎几把写的，横两刀竖一刀切，b用没有
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

  // 横着均分成五份，overlap代表每个区域向外延申几格
  private void buildRegions1(int overlap) {
    int length = environment.getxDimension() / 5;
    int width = environment.getyDimension();
    for (int i = 0; i < 5;  i ++) {
      regions[i] = new Region(Math.max(0, i * length - overlap), Math.min((i + 1) * length - 1 + overlap, environment.getxDimension() - 1), 0, width - 1);
    }
    /*regions[0].bot += overlap;
    regions[4].top -= overlap;*/
  }

  @Override
  public TWThought generatePlan() {
    // System.out.println(me.getName() + " strategy: " + curStrategy + " " + region.contains(me.getX(), me.getY()));
    switch (curStrategy) {
      case FIND_FUEL -> {
        if (!region.contains(me.getX(), me.getY())) {
          // 先回到自己的区域
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
          // 别人的区域就别explore了
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

    if (needRefuel() && fuelStation != null) {
      // 需要加油且找到了加油站就去加油
      curStrategy = Strategy.REFUEL;
      setCurrentGoal(fuelStation);
    } else if (fuelStation == null && !region.exploited() && !explorer) {
      // 刚开始，如果没找到加油站且自己的区域还没探索完，就先找加油站
      curStrategy = Strategy.FIND_FUEL;
    } else {
      // 探索地图 or 尝试得分
      if (me.hasTile()) {
        if (me.countTiles() >= 3) {
          // 拿不了更多tile时，有hole就去put，没有就explore
          if (closestHole == null) {
            // 如果前一个状态不是explore，就重设一下目的地，防止卡住
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
            } else if (me.closerTo(closestTile, closestHole)) {
              setCurrentGoal(closestTile);
            } else {
              setCurrentGoal(closestHole);
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

  // TODO decide when shall we navigate to fuelStation, need better criteria
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
    // 删除其他agent移出的obj
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

  // 如果先去a会导致b消失，先去b则不会，说明b优先级更高
  // 可能有点用
  public TWObject priority(TWObject a, TWObject b) {
    if (a == null && b == null) {
      return null;
    } else if (a == null) {
      return b;
    } else if (b == null) {
      return a;
    }

    // 这里的2是因为到达之后还要花一个time step执行put或者pick
    double dab = me.getDistanceTo(a) + a.getDistanceTo(b) + 2;
    double dba = me.getDistanceTo(b) + b.getDistanceTo(a) + 2;
    double ta = a.getTimeLeft(environment.schedule.getTime());
    double tb = b.getTimeLeft(environment.schedule.getTime());

    // 无论先去哪个都会导致另一个消失 或者先去哪个都不会导致另一个消失 就去最近的
    if ((ta < dba && tb < dab) || (ta >= dba && tb >= dab)) {
      return me.closerTo(a, b) ? a : b;
    } else {
      return ta < dba ? a : b;
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
            // 目标距离相同，那就让编号大的来
            conflict = me.getSerNum() < othersSerNum.get(i);
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

  public Region getRegion() {
    return region;
  }

  // 交换othersRegion的区域，直到每个agent到其区域中心的距离之和不能更小
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

  // 计算每个agent到其区域中心的距离之和，和越小说明区域分配越合理
  private int totalDistanceToCentre() {
    int res = 0;
    for (int i = 0; i < othersRegion.size(); i ++) {
      res += othersRegion.get(i).getDistanceToCentre(othersPos.get(i).x, othersPos.get(i).y);
    }
    return res;
  }

  private boolean isExplorerRegion(Region region) {
    return environment.getxDimension() / 2 == (region.bot - region.top) / 2 && environment.getyDimension() / 2 == (region.right - region.left) / 2;
  }

  private boolean canForget(TWObject object) {
    if (object instanceof TWObstacle) {
      return object.getTimeLeft(me.getEnvironment().schedule.getTime()) < me.getDistanceTo(object.getX(), object.getY());
    } else {
      return object.getTimeLeft(me.getEnvironment().schedule.getTime()) <= me.getDistanceTo(object.getX(), object.getY());
    }
  }
}