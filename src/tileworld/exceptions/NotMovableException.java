package tileworld.exceptions;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


/**
 * NotMovableException
 *
 * @author michaellees
 * Created: Apr 21, 2010
 * <p>
 * Copyright michaellees 2010
 * <p>
 * <p>
 * Description:
 * <p>
 * Thrown when move is called on a TWEntity which is not movable (e.g., Hole)
 */
class NotMovableException extends Exception {

  public NotMovableException(String string) {
  }

}
