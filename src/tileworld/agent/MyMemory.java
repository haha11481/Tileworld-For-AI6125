package tileworld.agent;

import sim.engine.Schedule;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;

import java.util.Map;

public class MyMemory extends TWAgentWorkingMemory{

  private TWFuelStation fuelStation = null;
  public MyMemory(TWAgent moi, Schedule schedule, int x, int y) {
    super(moi, schedule, x, y);
  }

  public void setFuelStation(TWFuelStation fuelStation) throws IllegalArgumentException{
    if (this.fuelStation == null) {
      this.fuelStation = fuelStation;
    } else {
      throw new IllegalArgumentException("FuelStation should not be initialized twice!");
    }
  }

  public boolean knowsFuelStation() {
    return fuelStation != null;
  }

  public TWFuelStation getFuelStation() {
    return this.fuelStation;
  }

  public void updateMemory(TWEntity entity) {
    this.objects[entity.getX()][entity.getY()] = new TWAgentPercept(entity, this.getSimulationTime());
    updateClosest(entity);
    if (!knowsFuelStation() && entity instanceof TWFuelStation) {
      setFuelStation((TWFuelStation) entity);
    }
  }

  @Override
  public void removeObject(TWEntity entity) {
    super.removeObject(entity);
    closestInSensorRange.entrySet().removeIf(entry ->
            entry.getValue().getX() == entity.getX() && entry.getValue().getY() == entity.getY());

  }
}