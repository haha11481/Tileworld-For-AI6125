package tileworld.agent;

import tileworld.environment.TWEntity;

/**
 * TWAgentPercept
 *
 * @author michaellees
 * Created: Apr 15, 2010
 * <p>
 * Copyright michaellees 2010
 * <p>
 * <p>
 * Description:
 * <p>
 * Stores a sensed object from the environment. Used in the Working Memory of
 * the agent. Has two main fields, TWEntity: a reference to the sensed object
 * and t: the time at which the object was seen
 */
public class TWAgentPercept {


  final int BEFORE = -1;
  final int EQUAL = 0;
  final int AFTER = 1;

  private TWEntity o;
  private double t;

  /**
   * @return the t
   */
  public double getT() {
    return t;
  }

  /**
   * @param t the t to set
   */
  public void setT(double t) {
    this.t = t;
  }

  /**
   * @return the o
   */
  public TWEntity getO() {
    return o;
  }

  /**
   * @param o the o to set
   */
  public void setO(TWEntity o) {
    this.o = o;
  }


  /**
   * @param t time at which the memory item was created
   * @param o the object which was observed
   */
  public TWAgentPercept(TWEntity o, double t) {
    super();
    this.t = t;
    this.o = o;
  }

  /**
   * true if fact is a newer version of the same memory (ie., see the same
   * tile twice)
   *
   * @param fact
   * @return
   */
  public boolean newerFact(Object fact) {

    if (!(fact instanceof TWAgentPercept)) {
      return false;
    }
    TWAgentPercept twf = (TWAgentPercept) fact;
    if (twf.o == this.o) {
      if (this.t <= twf.t) {
        return true;
      } else if (this.t > twf.t) {
        return false;
      }
    }
    return false;
  }

  /**
   * Facts are equal if they consider the same object, regardless of time.
   *
   * @param fact
   * @return
   */
  public boolean sameObject(Object fact) {
    if (this == fact) return true;
    if (!(fact instanceof TWAgentPercept)) return false;
    TWAgentPercept twf = (TWAgentPercept) fact;
    return (this.o == twf.o);
  }


  @Override
  public boolean equals(Object o) {
    return sameObject(o);
  }

}
