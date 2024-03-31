package tileworld.agent;

import sim.util.Bag;
import sim.util.IntBag;
import tileworld.Parameters;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.planners.AstarPathGenerator;
import tileworld.planners.TWPath;
import tileworld.planners.TWPathStep;

public class MyAgent extends TWAgent {
  private final String name;
  private final AstarPathGenerator pathGenerator;

  public MyAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
    super(xpos, ypos, env, fuelLevel);
    this.name = name;
    // TODO maxSearchDistance设置成物体的存活时间，是否合理？
    this.pathGenerator = new AstarPathGenerator(env, this, Parameters.lifeTime * 3);
    this.memory = new MyMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
    this.sensor = new MySensor(this, Parameters.defaultSensorRange, env);
  }

  // 根据目前所掌握的信息计划下一步的行动
  // TODO: 目前看来能否及时找到fuelStation对结果影响很大，是否要指定相应策略，优先寻找fuelStation
  // TODO: 需要考虑其他agent的位置和行动，避免冲突
  protected TWThought think() {
    try {
      if (needRefuel()) {
        TWFuelStation fuelStation = ((MyMemory) getMemory()).getFuelStation();

        // 如果已经在加油站了，就加油，否则生成去加油站的最短路径然后移动
        if (this.getEnvironment().inFuelStation(this)) {
          System.out.println("Arrive at fuel Station!");
          return new TWThought(TWAction.REFUEL, TWDirection.Z);
        } else {
          TWPath path = pathGenerator.findPath(this.x, this.y, fuelStation.getX(), fuelStation.getY());
          if (getFuelLevel() != 0) {
            System.out.println("Path to fuel Station = " + path.getpath().size() + " Distance = " + fuelStation.getDistanceTo(this) + "Current fuel level = " + getFuelLevel());
          }
          TWPathStep step = path.popNext();
          return new TWThought(TWAction.MOVE, step.getDirection());
        }
      } else {
        TWEntity hole = getMemory().getClosestObjectInSensorRange(TWHole.class);
        TWEntity tile = getMemory().getClosestObjectInSensorRange(TWTile.class);

        // 处理视野里没有tile或hole的情况
        if (tile == null && hole == null) {
          return randomMove();
        } else if (tile == null) {
          if (this.hasTile()) {
            return moveTo(hole);
          } else {
            return randomMove();
          }
        } else if (hole == null) {
          if (this.carriedTiles.size() == 3) {
            return randomMove();
          } else if (this.x == tile.getX() && this.y == tile.getY()) {
            return new TWThought(TWAction.PICKUP, TWDirection.Z);
          } else {
            return moveTo(tile);
          }
        }

        // 简单规则: 手上有tile且在hole中就put，手上tile不满且在tile中就pickup，否则寻找最近的tile/hole
        if (this.x == tile.getX() && this.y == tile.getY() && this.carriedTiles.size() < 3) {
          return new TWThought(TWAction.PICKUP, TWDirection.Z);
        } else if (this.x == hole.getX() && this.y == hole.getY() && this.hasTile()) {
          return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
        } else {
          // 离hole更近且手里有tile，或者手里tile满了，就去hole，否则去拿tile
          if ((this.getDistanceTo(hole) <= this.getDistanceTo(tile) && this.hasTile()) || this.carriedTiles.size() == 3) {
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

  // TODO decide when shall we navigate to fuelStation, need better criteria
  private boolean needRefuel() {
    TWFuelStation fuelStation = ((MyMemory) getMemory()).getFuelStation();
    return fuelStation != null && (fuelStation.getDistanceTo(this) + 5 >= this.getFuelLevel() || getFuelLevel() <= 5);
  }

  @Override
  protected void act(TWThought thought) {

    //You can do:
    //move(thought.getDirection())
    //pickUpTile(Tile)
    //putTileInHole(Hole)
    //refuel()
    switch (thought.getAction()) {
      case MOVE -> {
        try {
          this.move(thought.getDirection());
        } catch (CellBlockedException ex) {

          // Cell is blocked, replan?
        }
      }
      case PICKUP -> {
        TWTile tile = (TWTile) getMemory().getClosestObjectInSensorRange(TWTile.class);
        this.pickUpTile(tile);
        this.memory.removeObject(tile);
      }
      case PUTDOWN -> {
        TWHole hole = (TWHole) getMemory().getClosestObjectInSensorRange(TWHole.class);
        this.putTileInHole(hole);
        this.memory.removeObject(hole);
      }
      case REFUEL -> {
        System.out.println("Agent Refuel!");
        this.refuel();
      }
    }
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public void communicate() {
    assert this.sensor instanceof MySensor;
    Bag entities = ((MySensor) this.sensor).getSensedObjects();
    IntBag X = ((MySensor) this.sensor).getSensedX();
    IntBag Y = ((MySensor) this.sensor).getSensedY();
    // 时刻共享fuelStation的信息
    Message message = new MyMessage("", "", "", entities, X, Y, ((MyMemory)this.getMemory()).getFuelStation());
    this.getEnvironment().receiveMessage(message);
  }

  private TWDirection getRandomDirection() {
    TWDirection randomDir = TWDirection.values()[this.getEnvironment().random.nextInt(5)];

    if(this.getX()>=this.getEnvironment().getxDimension() ){
      randomDir = TWDirection.W;
    }else if(this.getX()<=1 ){
      randomDir = TWDirection.E;
    }else if(this.getY()<=1 ){
      randomDir = TWDirection.S;
    }else if(this.getY()>=this.getEnvironment().getxDimension() ){
      randomDir = TWDirection.N;
    }

    return randomDir;
  }
  
  private TWThought randomMove() {
    return new TWThought(TWAction.MOVE, getRandomDirection());
  }

  // 生成向目标Object移动的想法
  public TWThought moveTo(TWEntity entity) {
	    assert entity != null;
	    TWPath path = pathGenerator.findPath(this.x, this.y, entity.getX(), entity.getY());
	    if (path == null) {
	      return randomMove();
	    } else {
	      TWPathStep step = path.popNext();
	      return new TWThought(TWAction.MOVE, step.getDirection());
	    }
	  }
}