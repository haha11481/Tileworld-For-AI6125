package tileworld.planners;

import sim.util.Int2D;
import tileworld.Parameters;
import tileworld.agent.TWAgent;

public class Region {

  // 左上，右上，左下，右下
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

  // pos是否在region中
  public boolean contains(int x, int y) {
    return top <= x && left <= y && bot >= x && right >= y;
  }

  // 更新哪些位置被agent sense过了
  public void update(TWAgent agent) {
    int x = agent.getX() - top;
    int y = agent.getY();
    for (int i = x - range; i <= x + range; i++) {
      for (int j = y - range; j <= y + range; j++) {
        if (i >= 0 && i < scannedMatrix.length && j >= 0 && j < scannedMatrix[0].length) {
          scannedMatrix[i][j] =  agent.getEnvironment().schedule.getTime();
        }
      }
    }
  }

  /**
   * 获取agent scan的方向，具体策略为：
   *
   *左->下->右->上，螺旋扫描
   *
   * @param agent
   * @return  string: left, right, up, down
   */
  public String getScanDirection(TWAgent agent) {
    int x = agent.getX() - top;
    int y = agent.getY();
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

  private boolean hasUndiscoveredPos(int x, int y, String direction) {
    boolean res = false;
    switch (direction) {
      case "left" -> {
        for (int i = 0; i < scannedMatrix.length; i++) {
          for (int j = 0; j < y - range; j++) {
            if (j < scannedMatrix[0].length) {
              res = scannedMatrix[i][j] == -1;
            }
          }
        }
      }
      case "right" -> {
        for (int i = 0; i < scannedMatrix.length; i++) {
          for (int j = y + range + 1; j < scannedMatrix[0].length; j++) {
              res = scannedMatrix[i][j] == -1;
          }
        }
      }
      case "up" -> {
        for (int i = 0; i < x - range; i++) {
          for (int j = y - range; j <= y - range; j++) {
            if (j >= 0 && j < scannedMatrix[0].length) {
              res = scannedMatrix[i][j] == -1;
            }
          }
          // 已经到达了右边界
          if (y + range >= scannedMatrix[0].length - 1) {
            for (int j = y - range + 1; j <= y + range; j++) {
              if (j >= 0 && j < scannedMatrix[0].length) {
                res = scannedMatrix[i][j] == -1;
              }
            }
          }
        }
      }
      case "down" -> {
        for (int i = x + range + 1; i < scannedMatrix.length; i++) {
          for (int j = y - range; j <= y - range; j++) {
            if (j >= 0 && j < scannedMatrix[0].length) {
              res = scannedMatrix[i][j] == -1;
            }
          }
          // 已经到达了右边界
          if (y + range >= scannedMatrix[0].length - 1) {
            for (int j = y - range + 1; j <= y + range; j++) {
              if (j >= 0 && j < scannedMatrix[0].length) {
                res = scannedMatrix[i][j] == -1;
              }
            }
          }
        }
      }
    }
    return res;
  }

  // 很低效，判断区域的每一个位置都是否被扫过了
  public boolean exploited() {
    boolean exploited = true;
    for (int i = 0; i < scannedMatrix.length; i ++) {
      for (int j = 0; j < scannedMatrix[0].length; j ++) {
        exploited = scannedMatrix[i][j] > -1;
      }
    }
    return exploited;
  }
}
