package tileworld.planners;

import sim.util.Int2D;

public class Region {

  // 左上，右上，左下，右下
  int top, bot, left, right;
  public Region(int top, int bot, int left, int right) {
    this.top = top;
    this.bot = bot;
    this.left = left;
    this.right = right;
  }

  // pos是否在region中
  public boolean contains(int x, int y) {
    return top <= x && left <= y && bot >= x && right >= y;
  }
}
