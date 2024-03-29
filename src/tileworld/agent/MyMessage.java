package tileworld.agent;

import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;

import java.util.ArrayList;

public class MyMessage extends Message{
  private final Bag entities;
  private final IntBag X;
  private final IntBag Y;
  private final TWFuelStation fuelStation;

  // TODO 需要加入，如本agent的状态或者位置信息， 预期将执行的action等
  public MyMessage(String from, String to, String message, Bag entities, IntBag X, IntBag Y, TWFuelStation fuelStation) {
    super(from, to, message);
    this.entities = entities;
    this.X = X;
    this.Y = Y;
    this.fuelStation = fuelStation;
  }

  public Bag getEntities() {
    return this.entities;
  }

  public IntBag getX() {
    return this.X;
  }

  public IntBag getY() {
    return this.Y;
  }

  public TWFuelStation getFuelStation() {
    return this.fuelStation;
  }
}
