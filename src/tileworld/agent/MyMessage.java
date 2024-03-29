package tileworld.agent;

import sim.util.Bag;
import tileworld.environment.TWEntity;

import java.util.ArrayList;

public class MyMessage extends Message{
  private final Bag entities;

  // TODO 可以加入更多信息，如本agent的状态或者位置信息等
  public MyMessage(String from, String to, String message, Bag entities) {
    super(from, to, message);
    this.entities = entities;
  }

  public Bag getEntities() {
    return this.entities;
  }
}
