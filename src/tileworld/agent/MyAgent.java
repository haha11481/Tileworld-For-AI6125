package tileworld.agent;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.Parameters;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.planners.*;

public class MyAgent extends TWAgent {
  private final String name;
  private final MyPlanner planner;

  public MyAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
    super(xpos, ypos, env, fuelLevel);
    this.name = name;
    this.planner = new MyPlanner(this, env);
    this.memory = new MyMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
  }

  @Override
  protected TWThought think() {
    if (planner.hasPlan()) {
      return planner.generatePlan();
    } else {
      // currently, hasPlan() will always return true in our implementation
      return planner.voidPlan();
    }
  }

  // 返回目前持有的tile数量
  public int countTiles() {
    return this.carriedTiles.size();
  }

  @Override
  protected void act(TWThought thought) {
    if (getEnvironment().schedule.getTime() == 4999) {
      System.out.println(getName() + " score: " + getScore());
    }

    if (getFuelLevel() <= 0 && ((MyMemory) getMemory()).getFuelStation() == null) {
      System.out.println(getName() + " ran oof without finding fuel station!");
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
    MyMemory mm = (MyMemory) this.memory;
    Bag entities = mm.getSensedObjects();
    IntBag x = mm.getSensedX();
    IntBag y = mm.getSensedY();
    Message message = new MyMessage(entities, x, y, mm.getFuelStation(), planner.getCurrentGoal(),
            new Int2D(this.x, this.y), planner.getCurStrategy(), getSerNum(), mm.getRemovedObj(), planner.getRegion());
    this.getEnvironment().receiveMessage(message);
  }

  // TODO better method to assign unique number to each agent?
  public int getSerNum() {
    return Integer.parseInt(String.valueOf(getName().charAt(getName().length() - 1)));
  }
}