/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tileworld.planners;

import sim.util.Int2D;
import tileworld.agent.TWThought;
import tileworld.environment.TWDirection;

/**
 *
 * @author michaellees
 */
public interface TWPlanner {

    TWThought generatePlan();
    boolean hasPlan();
    TWThought voidPlan();
    Int2D getCurrentGoal();
    TWDirection execute();

}
