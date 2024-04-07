package tileworld.planners;

/**
 * you can set any parameters related to the agent planner here
 */
public class PlannerParams {
  // 是否开启区域切换，开启后在每个time step都采用最优的区域分配策略
  // 有用，但没什么大用
  public static final boolean enable_region_swapping = true;

  // 代表每个分区向外延申多少格，默认区域不重叠
  public static final int region_overlap = 0;

  // distance threshold for whether we want early refuel, used together with fuel level threshold
  public static final int refuel_distanceThreshold = 10;
  // fuel level threshold for whether we want early refuel, used together with distance threshold
  public static final int refuel_fuelLevelThreshold = 250;

  // fuel < distanceTo(fuel station) + force_refuel_threshold => refuel
  public static final int force_refuel_threshold = 25;

  // the distance of the explorer region to the border of the map, maybe low influence on performance
  public static final int explorer_border = 5;

  // 当前目标的最大距离限制，超过该距离就不设为目标了
  public static final int normal_distanceThreshold = 10;
  public static final int explorer_distanceThreshold = 25;

  // 决定explore时的方向，越大表示越看重未来的探索方向，有点用
  public static final int normal_sight = 3;
  public static final int explorer_sight = 6;
}
