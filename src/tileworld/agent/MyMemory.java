package tileworld.agent;

import sim.engine.Schedule;
import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;
import tileworld.environment.TWObject;

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
    getMemoryGrid().set(entity.getX(), entity.getY(), null);
  }

  @Override
  public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {
    for (Object e : sensedObjects) {
      if (e instanceof TWFuelStation) {
        this.setFuelStation((TWFuelStation) e);
      }
    }
    this.sensedObjects.clear();
    this.sensedX.clear();
    this.sensedY.clear();
    this.sensedObjects.addAll(sensedObjects);
    this.sensedX.addAll(objectXCoords);
    this.sensedY.addAll(objectYCoords);
    super.updateMemory(sensedObjects, objectXCoords, objectYCoords, sensedAgents, agentXCoords, agentYCoords);
    decayMemory();
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

  // 删除已经死亡的tile/hole/obstacle
  @Override
  public void decayMemory() {
    for (int x = 0; x < getMemoryGrid().getHeight(); x++) {
      for (int y = 0; y < getMemoryGrid().getWidth(); y++) {
        Object o = getMemoryGrid().get(x, y);
        if (o instanceof TWObject) {
          if (((TWObject) o).getTimeLeft(getSimulationTime()) <= 0) {
            getMemoryGrid().set(x, y, null);
            objects[x][y] = null;
          }
        }
      }
    }
  }
}