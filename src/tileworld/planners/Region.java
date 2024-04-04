package tileworld.planners;

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
   * 获取agent下一步需要explore的方向，具体策略为：
   *
   * 记当前时间为n，位置(i,j)最后一次被sense到的时间为t(i,j)
   * d(i,j) = n - t(i,j) 表示该位置距离最后一次被sense所经过的时间，d越大则越有可能生成tile或hole
   * 对于上下左右每个方向，记录向该方向移动后能sense到的d(i,j)之和S，向S最大的方向移动
   *
   * @param me
   * @return  string: left, right, up, down
   */
  public String getExploreDirection(TWAgent me, double currentTime) {
    double leftTime = 0;
    double rightTime = 0;
    double upTime = 0;
    double downTime = 0;
    int x = me.getX() - top;
    int y = me.getY() - left;
    for (int i = x - range; i <= x + range; i ++) {
      if (validPos(i, y - range - 1)) {
        leftTime += currentTime - scannedMatrix[i][y - range - 1];
      }
    }
    for (int i = x - range; i <= x + range; i ++) {
      if (validPos(i, y + range + 1)) {
        rightTime += currentTime - scannedMatrix[i][y + range + 1];
      }
    }
    for (int i = y - range; i <= y + range; i ++) {
      if (validPos(x - range - 1, i)) {
        upTime += currentTime - scannedMatrix[x - range - 1][i];
      }
    }
    for (int i = y - range; i <= y + range; i ++) {
      if (validPos(x + range + 1, i)) {
        downTime += currentTime - scannedMatrix[x + range + 1][i];
      }
    }

    double max1 = Math.max(leftTime, rightTime);
    double max2 = Math.max(upTime, downTime);
    double max = Math.max(max1, max2);
    if (max == leftTime){
      return "left";
    } else if (max == rightTime){
      return "right";
    } else if (max == upTime){
      return "up";
    } else {
      return "down";
    }
  }

  private boolean validPos(int x, int y) {
    return x >= 0 && x < scannedMatrix.length && y >= 0 && y < scannedMatrix[0].length;
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
