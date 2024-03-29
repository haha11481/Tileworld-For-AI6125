package tileworld.agent;

import sim.util.Bag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWEnvironment;

import java.util.ArrayList;

public class MySensor extends TWAgentSensor{

  private final TWEnvironment environment;
  private final ArrayList<Message> messages;
  private final Bag sensedObjects = new Bag();
  MySensor(TWAgent moi, int defaultSensorRange, TWEnvironment environment) {
    super(moi, defaultSensorRange);
    this.environment = environment;
    messages = environment.getMessages();
  }

  @Override
  public void sense() {
    sensedObjects.clear();
    me.getMemory().decayMemory();
    me.getEnvironment().getObjectGrid().getNeighborsMaxDistance(me.getX(), me.getY(), sensorRange, false, sensedObjects, null, null);
    assert me.getMemory() instanceof MyMemory;
    for (Object entity : sensedObjects) {
      if (entity != null) {
        ((MyMemory) me.getMemory()).updateMemory((TWEntity) entity);
      }
    }
    for (Message m : messages) {
      assert m instanceof MyMessage;
      for (Object entity : ((MyMessage) m).getEntities()) {
        if (entity != null) {
          ((MyMemory) me.getMemory()).updateMemory((TWEntity) entity);
        }
      }
    }
  }

  public Bag getSensedObjects() {
    return this.sensedObjects;
  }
}

