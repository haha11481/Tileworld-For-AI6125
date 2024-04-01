package tileworld.environment;

import sim.portrayal.Portrayal;
import sim.portrayal.simple.RectanglePortrayal2D;
import sim.util.Int2D;

import java.awt.*;

/**
 * TWHole
 *
 * @author michaellees
 * Created: Apr 15, 2010
 * <p>
 * Copyright michaellees 2010
 * <p>
 * <p>
 * Description:
 * <p>
 * Holes in Tileworld
 */
public class TWHole extends TWObject {

  /**
   * @param creationTime
   * @param deathTime
   */
  public TWHole(int x, int y, TWEnvironment env, double creationTime, double deathTime) {
    super(x, y, env, creationTime, deathTime);

  }

  public TWHole(Int2D pos, TWEnvironment env, Double creationTime, Double deathTime) {
    super(pos, env, creationTime, deathTime);

  }

  public static Portrayal getPortrayal() {
    //brown filled box.
    return new RectanglePortrayal2D(new Color(188, 143, 143), true);


  }

  public static Portrayal getMemoryPortrayal() {

    return new RectanglePortrayal2D(new Color(150, 143, 143, 125), false);

  }

  public TWHole() {
  }
}
