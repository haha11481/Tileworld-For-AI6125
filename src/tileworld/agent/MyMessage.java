package tileworld.agent;

import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;
import tileworld.planners.Region;
import tileworld.planners.Strategy;

public class MyMessage extends Message {
  private final Bag entities;
  private final IntBag x;
  private final IntBag y;
  private final TWFuelStation fuelStation;
  private final Int2D currentGoal;
  private final Int2D agentPos;
  private final Strategy strategy;
  private final int serNum;
  private final TWEntity removedObj;
  private final Region region;

  public MyMessage(Bag entities, IntBag x, IntBag y, TWFuelStation fuelStation, Int2D currentGoal, Int2D agentPos, Strategy strategy, int serNum, TWEntity removedObj, Region region) {
    super("", "", "");
    this.entities = entities;
    this.x = x;
    this.y = y;
    this.fuelStation = fuelStation;
    this.currentGoal = currentGoal;
    this.agentPos = agentPos;
    this.strategy = strategy;
    this.serNum = serNum;
    this.removedObj = removedObj;
    this.region = region;
  }

  public Bag getEntities() {
    return this.entities;
  }

  public IntBag getX() {
    return x;
  }

  public IntBag getY() {
    return y;
  }

  public TWFuelStation getFuelStation() {
    return this.fuelStation;
  }

  public Int2D getCurrentGoal() {
    return this.currentGoal;
  }

  public Int2D getAgentPos() {
    return this.agentPos;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public int getSerNum() {
    return serNum;
  }

  public TWEntity getRemovedObj() {
    return this.removedObj;
  }

  public Region getRegion() {
    return region;
  }
}