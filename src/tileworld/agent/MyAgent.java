package tileworld.agent;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.planners.*;

public class MyAgent extends TWAgent {
  private final String name;
  private final MyPlanner planner;

  public MyAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
    super(xpos, ypos, env, fuelLevel);
    this.name = name;
    // TODO maxSearchDistance设置成物体的存活时间，是否合理？
    this.planner = new MyPlanner(this, env);
    this.memory = new MyMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
  }

  @Override
  protected TWThought think() {
    if (planner.hasPlan()) {
      return planner.generatePlan();
    } else {
      // 别别别
      return planner.voidPlan();
    }
  }

  // TODO decide when shall we navigate to fuelStation, need better criteria
  public boolean needRefuel() {
    TWFuelStation fuelStation = ((MyMemory) getMemory()).getFuelStation();
    return fuelStation != null && (fuelStation.getDistanceTo(this) + 50 >= this.getFuelLevel() || getFuelLevel() <= 50);
  }

  // 返回tiles是否已经拿满
  public boolean tilesFull() {
    return this.carriedTiles.size() == 3;
  }

  @Override
  protected void act(TWThought thought) {
    if (thought == null) {
      System.out.println("不可能");
      return;
    }

    if (getFuelLevel() <= 0 && ((MyMemory) getMemory()).getFuelStation() == null) {
      System.out.println("ran oof without finding fuel station!");
    }
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
        TWTile tile = (TWTile) getMemory().getMemoryGrid().get(x, y);
        if (tile == null) {
          System.out.println("impossible");
        }
        this.pickUpTile(tile);
        this.memory.removeObject(tile);
        this.planner.removeObject(tile);
      }
      case PUTDOWN -> {
        TWHole hole = (TWHole) getMemory().getMemoryGrid().get(x, y);
        this.putTileInHole(hole);
        this.memory.removeObject(hole);
        this.planner.removeObject(hole);
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
    Bag entities = ((MyMemory) this.memory).getSensedObjects();
    IntBag x = ((MyMemory) this.memory).getSensedX();
    IntBag y = ((MyMemory) this.memory).getSensedY();
    // 时刻共享fuelStation的信息
    Message message = new MyMessage(entities, x, y,
            ((MyMemory) this.getMemory()).getFuelStation(), planner.getCurrentGoal(), new Int2D(this.x, this.y));
    this.getEnvironment().receiveMessage(message);
  }
}