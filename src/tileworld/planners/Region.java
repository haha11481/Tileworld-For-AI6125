package tileworld.planners;

import sim.util.Int2D;
import tileworld.Parameters;
import tileworld.agent.TWAgent;

public class Region {

  int top, bot, left, right;
  double[][] scannedMatrix;
  int range = Parameters.defaultSensorRange;
  public Region(int top, int bot, int left, int right) {
    this.top = top;
    this.bot = bot;
    this.left = left;
    this.right = right;
    this.scannedMatrix = new double[bot - top + 1][right - left + 1];
    for (int i = 0; i < scannedMatrix.length; i ++) {
      for (int j = 0; j < scannedMatrix[0].length; j ++) {
        scannedMatrix[i][j] = -1;
      }
    }
  }

  // return whether pos(x,y) is inside this region
  public boolean contains(int x, int y) {
    return top <= x && left <= y && bot >= x && right >= y;
  }

  // update the last sensed time of pos(x,y)
  public void update(int x, int y, double currentTime) {
    x = x - top;
    y = y - left;
    for (int i = x - range; i <= x + range; i++) {
      for (int j = y - range; j <= y + range; j++) {
        if (i >= 0 && i < scannedMatrix.length && j >= 0 && j < scannedMatrix[0].length) {
          scannedMatrix[i][j] = currentTime;
        }
      }
    }
  }

  /**
   * return the direction for agent to explore this region：
   *
   * let current time step = n，last sensed time of pos(i,j) = t(i,j)
   * d(i,j) = n - t(i,j) represents the period from last sensed time of pos(i,j) till now，larger d indicates higher
   * possibility of spawning tiles or holes.
   * for each direction，calculate the sum of d(i,j)s if moving toward that direction.
   * return the direction with the largest sum.
   *
   * @param vision the total number of steps we want to calculate, we take the average of these steps as final sum.
   * @return  string: left, right, up, down
   */
  public String getExploreDirection(TWAgent me, double currentTime, int vision) {
    assert this.contains(me.getX(), me.getY());
    double leftTime = 0;
    double rightTime = 0;
    double upTime = 0;
    double downTime = 0;
    int x = me.getX() - top;
    int y = me.getY() - left;
    int count = 0;
    for (int i = x - range; i <= x + range; i ++) {
      for (int v = 1; v <= vision; v ++) {
        if (validPos(i, y - range - v)) {
          leftTime += currentTime - scannedMatrix[i][y - range - v];
          count ++;
        }
      }
    }
    if (count != 0) {
      leftTime /= count;
      count = 0;
    }
    for (int i = x - range; i <= x + range; i ++) {
      for (int v = 1; v <= vision; v ++) {
        if (validPos(i, y + range + v)) {
          rightTime += currentTime - scannedMatrix[i][y + range + v];
          count++;
        }
      }
    }
    if (count != 0) {
      rightTime /= count;
      count = 0;
    }
    for (int i = y - range; i <= y + range; i ++) {
      for (int v = 1; v <= vision; v ++) {
        if (validPos(x - range - v, i)) {
          upTime += currentTime - scannedMatrix[x - range - v][i];
          count++;
        }
      }
    }
    if (count != 0) {
      upTime /= count;
      count = 0;
    }
    for (int i = y - range; i <= y + range; i ++) {
      for (int v = 1; v <= vision; v ++) {
        if (validPos(x + range + v, i)) {
          downTime += currentTime - scannedMatrix[x + range + v][i];
          count++;
        }
      }
    }
    if (count != 0) {
      downTime /= count;
    }

    double max1 = Math.max(leftTime, rightTime);
    double max2 = Math.max(upTime, downTime);
    double max = Math.max(max1, max2);
    if (max == 0) {
      return againstBorder(me.getX(), me.getY());
    } else if (max == leftTime){
      return "left";
    } else if (max == rightTime){
      return "right";
    } else if (max == upTime){
      return "up";
    } else {
      return "down";
    }
  }

  // move towards the middle of the region
  private String againstBorder(int x, int y) {
    int l = y - left;
    int r = right - y;
    int u = x - top;
    int d = bot - x;
    double max1 = Math.max(l, r);
    double max2 = Math.max(u, d);
    double max = Math.max(max1, max2);
    if (max == l){
      return "left";
    } else if (max == r){
      return "right";
    } else if (max == u){
      return "up";
    } else {
      return "down";
    }
  }

  // return whether pos(x,y) is a valid index in the region
  private boolean validPos(int x, int y) {
    return x >= 0 && x < scannedMatrix.length && y >= 0 && y < scannedMatrix[0].length;
  }

  /**
   * return the next direction for the agent to scan this region：
   *
   * 1.move left until all pos on the left of the agent have been scanned
   * 2.move up/down until all pos in the upper or lower area of the agent have been scanned
   * 3.move right while all pos on the left of the agent have been scanned or agent has reached right border
   * 4.repeat previous step until all pos have been scanned
   *
   * @param agent
   * @return  string: left, right, up, down
   */
  public String getScanDirection(TWAgent agent) {
    int x = agent.getX() - top;
    int y = agent.getY() - left;
    if (hasUndiscoveredPos(x, y, "left")) {
      return "left";
    } else if (hasUndiscoveredPos(x, y, "down")) {
      return "down";
    } else if (hasUndiscoveredPos(x, y, "up")) {
      return "up";
    } else if (hasUndiscoveredPos(x, y, "right")) {
      return "right";
    } else {
      return "all_done";
    }
  }

  // return if there is any pos that has not been scanned by the agent in the provided direction
  private boolean hasUndiscoveredPos(int x, int y, String direction) {
    switch (direction) {
      case "left" -> {
        for (int i = 0; i < scannedMatrix.length; i++) {
          for (int j = 0; j < y - range; j++) {
            if (j < scannedMatrix[0].length) {
              if (scannedMatrix[i][j] == -1) {
                return true;
              }
            }
          }
        }
      }
      case "right" -> {
        for (int i = 0; i < scannedMatrix.length; i++) {
          for (int j = y + range + 1; j < scannedMatrix[0].length; j++) {
            if (scannedMatrix[i][j] == -1) {
              return true;
            }
          }
        }
      }
      case "up" -> {
        for (int i = 0; i < x - range; i++) {
          for (int j = y - range; j <= y - range; j++) {
            if (j >= 0 && j < scannedMatrix[0].length) {
              if (scannedMatrix[i][j] == -1) {
                return true;
              }
            }
          }
          // 已经到达了右边界
          if (y + range >= scannedMatrix[0].length - 1) {
            for (int j = y - range + 1; j <= y + range; j++) {
              if (j >= 0 && j < scannedMatrix[0].length) {
                if (scannedMatrix[i][j] == -1) {
                  return true;
                }
              }
            }
          }
        }
      }
      case "down" -> {
        for (int i = x + range + 1; i < scannedMatrix.length; i++) {
          for (int j = y - range; j <= y - range; j++) {
            if (j >= 0 && j < scannedMatrix[0].length) {
              if (scannedMatrix[i][j] == -1) {
                return true;
              }
            }
          }
          // 已经到达了右边界
          if (y + range >= scannedMatrix[0].length - 1) {
            for (int j = y - range + 1; j <= y + range; j++) {
              if (j >= 0 && j < scannedMatrix[0].length) {
                if (scannedMatrix[i][j] == -1) {
                  return true;
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  // not efficient method, return whether all pos of the region have been sensed by at least 1 agent
  public boolean exploited() {
    for (double[] row : scannedMatrix) {
      for (double v : row) {
        if (v <= -1) {
          return false;
        }
      }
    }
    return true;
  }

  // return the distance between region centre and pos(x,y)
  public int getDistanceToCentre(int x, int y) {
    Int2D centre = getCenter();
    return Math.abs(x - centre.x) + Math.abs(y - centre.y);
  }

  // return the center of the region
  public Int2D getCenter() {
    return new Int2D(top + scannedMatrix.length / 2, left + scannedMatrix[0].length / 2);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Region region) {
      return this.left == region.left && this.right == region.right && this.top == region.top && this.bot == region.bot;
    }
    return false;
  }

  // copy the scannedMatrix from r1 to r2, use it if we want to change the region distribution during the run
  public static void copyScannedMatrix(Region r1, Region r2) {
    for (int i = 0; i < r1.scannedMatrix.length; i ++) {
      for (int j = 0; j < r1.scannedMatrix[0].length; j ++) {
        double ts = r1.scannedMatrix[i][j];
        int x = i + r1.top;
        int y = i + r1.left;
        if (r2.contains(x, y)) {
          x = x - r2.top;
          y = y - r2.left;
          r2.scannedMatrix[x][y] = ts;
        }
      }
    }
  }
}
