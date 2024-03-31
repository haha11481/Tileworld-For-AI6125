package tileworld.agent;

import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWEnvironment;
import tileworld.environment.TWFuelStation;

import java.util.ArrayList;

public class MySensor extends TWAgentSensor{

  private final TWEnvironment environment;
  private final ArrayList<Message> messages;
  private final Bag sensedObjects = new Bag();
  private final IntBag sensedX = new IntBag();
  private final IntBag sensedY = new IntBag();
  MySensor(TWAgent moi, int defaultSensorRange, TWEnvironment environment) {
    super(moi, defaultSensorRange);
    this.environment = environment;
    messages = environment.getMessages();
  }

  @Override
  public void sense() {
    // 每个time step刷新一次memory
    ((MyMemory) me.getMemory()).refreshMemory();
    // 每个time step重置一次自己观察到的环境，用于和其他agent共享
    sensedObjects.clear();
    sensedX.clear();
    sensedY.clear();
    // 删除已经超过存活时间的object
    me.getMemory().decayMemory();

    // 用agent自己sense到的环境更新memory
    me.getEnvironment().getObjectGrid().getNeighborsMaxDistance(me.getX(), me.getY(), sensorRange, false, sensedObjects, sensedX, sensedY);
    me.getMemory().updateMemory(sensedObjects, sensedX, sensedY, null, null, null);

    // 用其他agent的message更新memory
    for (Message m : messages) {
      assert m instanceof MyMessage;
      MyMessage mm = (MyMessage) m;
      me.getMemory().updateMemory(mm.getEntities(), mm.getX(), mm.getY(), null, null, null);

      if (mm.getFuelStation() != null && ((MyMemory) me.getMemory()).getFuelStation() == null) {
        ((MyMemory) me.getMemory()).setFuelStation(mm.getFuelStation());
      }
    }

    // 如果找到了加油站，就保存在memory中
    TWFuelStation fuelStation = environment.findFuelStation(me, this.sensorRange);
    if (fuelStation != null && ((MyMemory) me.getMemory()).getFuelStation() == null) {
      System.out.println("Saw fuel station!!!");
      ((MyMemory) me.getMemory()).setFuelStation(fuelStation);
    }
  }

  public Bag getSensedObjects() {
    return this.sensedObjects;
  }

  public IntBag getSensedX() {
    return this.sensedX;
  }

  public IntBag getSensedY() {
    return this.sensedY;
  }
}

