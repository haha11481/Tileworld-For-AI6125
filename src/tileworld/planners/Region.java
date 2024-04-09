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
   * 获取agent下一步需要explore的方向，具体策略为：
   *
   * 记当前时间为n，位置(i,j)最后一次被sense到的时间为t(i,j)
   * d(i,j) = n - t(i,j) 表示该位置距离最后一次被sense所经过的时间，d越大则越有可能生成tile或hole
   * 对于上下左右每个方向，记录向该方向移动后能sense到的d(i,j)之和S，向S最大的方向移动
   * vision代表向外求几行，最终比较这些行的平均值
   *
   * @param me
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

  // 向离边界最远的反方向走。。
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

  // 很低效，判断区域的每一个位置都是否被扫过了
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

  // 获取该点到区域的距离
  public int getDistance(int x, int y) {
    if (this.contains(x, y)) {
      return 0;
    }

    int d = 0;
    if (x < top) {
      d += top - x;
    } else if (x > bot) {
      d += x - bot;
    }

    if (y < left) {
      d += left - y;
    } else if (y > right) {
      d += y - right;
    }

    return d;
  }

  // 返回最久没有被sense过的区域的中心点
  // 屌用没有目前
  public Int2D coldestArea(double curTime) {
    int maxD = 0;
    // default direction
    Int2D res = new Int2D(scannedMatrix.length / 2, scannedMatrix[0].length / 2);
    for (int i = 0; i < scannedMatrix.length; i ++) {
      for (int j = 0; j < scannedMatrix[0].length; j++) {
        int d = 0;
        for (int x = i - range; x <= i + range; x ++) {
          for (int y = j - range; y <= j + range; y ++) {
            if (validPos(x, y)) {
              d += curTime - scannedMatrix[x][y];
            }
          }
        }
        if (d > maxD) {
          maxD = d;
          res = new Int2D(i, j);
        }
      }
    }
    return new Int2D(res.x + top, res.y + left);
  }

  // 返回点到区域中心的距离
  public int getDistanceToCentre(int x, int y) {
    Int2D centre = getCenter();
    return Math.abs(x - centre.x) + Math.abs(y - centre.y);
  }

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
          y = y - r1.left;
          r2.scannedMatrix[x][y] = ts;
        }
      }
    }
  }
}
