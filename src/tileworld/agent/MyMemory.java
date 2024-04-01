package tileworld.agent;

import sim.engine.Schedule;
import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;

public class MyMemory extends TWAgentWorkingMemory {

  // 记录自己感知到的Objects,用于在每个time step向其他agent发送消息
  private final Bag sensedObjects = new Bag();
  private final IntBag sensedX = new IntBag();
  private final IntBag sensedY = new IntBag();

  public MyMemory(TWAgent moi, Schedule schedule, int x, int y) {
    super(moi, schedule, x, y);
  }

  private TWFuelStation fuelStation = null;

  public TWFuelStation getFuelStation() {
    return this.fuelStation;
  }

  public void setFuelStation(TWFuelStation fuelStation) {
    this.fuelStation = fuelStation;
  }

  @Override
  public void removeObject(TWEntity entity) {
    super.removeObject(entity);

  }

  @Override
  public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {
    for (Object e : sensedObjects) {
      if (e instanceof TWFuelStation) {
        this.setFuelStation((TWFuelStation) e);
      }
    }
    this.sensedObjects.clear();
    this.sensedObjects.addAll(sensedObjects);
    this.sensedX.addAll(objectXCoords);
    this.sensedY.addAll(objectYCoords);
    super.updateMemory(sensedObjects, objectXCoords, objectYCoords, sensedAgents, agentXCoords, agentYCoords);
  }

  public Bag getSensedObjects() {
    return this.sensedObjects;
  }

  public IntBag getSensedX() {
    return sensedX;
  }

  public IntBag getSensedY() {
    return sensedY;
  }
}