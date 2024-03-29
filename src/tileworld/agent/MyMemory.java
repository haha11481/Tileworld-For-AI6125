package tileworld.agent;

import sim.engine.Schedule;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;

import java.util.HashMap;
import java.util.Map;

public class MyMemory extends TWAgentWorkingMemory{

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
    closestInSensorRange.entrySet().removeIf(entry ->
            entry.getValue().getX() == entity.getX() && entry.getValue().getY() == entity.getY());

  }

  public void refreshMemory() {
    this.closestInSensorRange = new HashMap<>(4);
  }
}