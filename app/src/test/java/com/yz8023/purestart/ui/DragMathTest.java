package com.yz8023.purestart.ui;
import org.junit.Test;
import static org.junit.Assert.*;
/**
 * 底栏几何/方向的回归测试（v15.1.7）。
 *
 * 约定只有一条：**手指右移 = tab 坐标变大 = 胶囊右移**（对照 yz8023/UI
 * LiquidBottomTabs 的 `updateValue(targetValue + dragAmount.x / tabWidth)`），
 * 与 HorizontalPager 在同一方向上（右移 = 回到上一页，左移 = 前往下一页）。
 */
public class DragMathTest {
 private static final float W = 400f;      // 底栏总宽
 private static final float INSET = 8f;    // 两侧内缩合计 8px

 /** 槽宽 = (总宽 - 两侧内缩) / 4，且退化输入安全。 */
 @Test public void tabWidthMatchesReference() {
  assertEquals(98f, DragMath.tabWidth(W, INSET / 2f), .001f);   // (400 - 4*2)/4
  assertEquals(0f, DragMath.tabWidth(0f, 4f), .001f);
  assertEquals(0f, DragMath.tabWidth(4f, 4f), .001f);           // 内缩吃掉全部宽度
 }

 /** 手指位移 → tab 位移：右正，一槽宽 = 1 个 tab。 */
 @Test public void dragDirectionMatchesFinger() {
  assertEquals(1f, DragMath.tabDelta(96f, 96f), .001f);          // 右移一槽
  assertEquals(-1f, DragMath.tabDelta(-96f, 96f), .001f);        // 左移一槽
  assertEquals(0f, DragMath.tabDelta(0f, 96f), .001f);
  assertEquals(0f, DragMath.tabDelta(50f, 0f), .001f);           // 零宽安全
 }

 /** 拖动中的胶囊坐标：以按下点为锚，手指往哪走胶囊就往哪走。 */
 @Test public void capsuleFollowsFinger() {
  assertEquals(1f, DragMath.dragTab(0f, 96f, 96f), .001f);       // 首页按下右移一槽 → 应用
  assertEquals(0f, DragMath.dragTab(1f, -96f, 96f), .001f);      // 应用按下左移一槽 → 首页
  assertEquals(3f, DragMath.dragTab(0f, 900f, 96f), .001f);      // 越界钳位
  assertEquals(0f, DragMath.dragTab(3f, -900f, 96f), .001f);
  assertEquals(1.5f, DragMath.dragTab(1f, 48f, 96f), .001f);     // 半个槽
 }

 /** 胶囊像素位置向右为正，且整体不出底栏。 */
 @Test public void capsuleOffsetGrowsRightwards() {
  assertEquals(4f, DragMath.capsuleOffset(0f, 96f, 4f), .001f);
  assertEquals(100f, DragMath.capsuleOffset(1f, 96f, 4f), .001f);
  assertEquals(292f, DragMath.capsuleOffset(3f, 96f, 4f), .001f);
  assertEquals(4f, DragMath.capsuleOffset(-2f, 96f, 4f), .001f);   // 钳位
  assertEquals(292f, DragMath.capsuleOffset(9f, 96f, 4f), .001f);
 }

 /** 松手落点吸附最近槽位；越过半槽才换页。 */
 @Test public void landingSnapsToNearestSlot() {
  assertEquals(0, DragMath.landingTab(0.2f, 0f, 96f));
  assertEquals(1, DragMath.landingTab(0.8f, 0f, 96f));
  assertEquals(2, DragMath.landingTab(2f, 0f, 96f));
  assertEquals(3, DragMath.landingTab(3f, 0f, 96f));
  assertEquals(1, DragMath.landingTab(1f, 0f, 96f));
 }

 /** 轻扫：位移不足半槽但速度快，也沿**同一方向**多走一槽（不反向）。 */
 @Test public void flingFollowsSameDirection() {
  // 右移 20px + 向右快扫（+1200px/s）→ 前进（往右）一槽
  assertEquals(1, DragMath.landingTab(DragMath.dragTab(0f, 20f, 96f), 1200f, 96f));
  // 左移 20px + 向左快扫 → 后退一槽
  assertEquals(0, DragMath.landingTab(DragMath.dragTab(1f, -20f, 96f), -1200f, 96f));
  // 速度再大也不会越界
  assertEquals(3, DragMath.landingTab(DragMath.dragTab(2f, 40f, 96f), 9000f, 96f));
  assertEquals(0, DragMath.landingTab(DragMath.dragTab(1f, -40f, 96f), -9000f, 96f));
 }

 /** 触摸点 → 该点所在 tab（仅用于点击定位与高亮）。 */
 @Test public void touchPositionMapsToTab() {
  assertEquals(0, DragMath.nearestTab(DragMath.tabAtPosition(0f, 96f, 4f)));
  assertEquals(1, DragMath.nearestTab(DragMath.tabAtPosition(120f, 96f, 4f)));
  assertEquals(3, DragMath.nearestTab(DragMath.tabAtPosition(399f, 96f, 4f)));
  assertEquals(0f, DragMath.tabAtPosition(10f, 0f, 0f), .001f);   // 零宽安全
 }

 /**
  * v15.2 第三阶段：轻点落点 = 手指按下那一格（点到哪里，胶囊就滑到哪里）。
  */
 @Test public void tapLandsOnPointedSlot() {
  assertEquals(0, DragMath.tapTab(4f, 96f, 4f));       // 最左
  assertEquals(1, DragMath.tapTab(120f, 96f, 4f));
  assertEquals(2, DragMath.tapTab(220f, 96f, 4f));
  assertEquals(3, DragMath.tapTab(392f, 96f, 4f));     // 最右
  assertEquals(0, DragMath.tapTab(999f, 0f, 0f));      // 零宽安全
 }

 /**
  * 图标「放大液态」：胶囊正下方放大最多，隔一格回到 1.00，且永远不缩小。
  */
 @Test public void iconScaleGrowsNearCapsule() {
  assertEquals(1.28f, DragMath.proximityScale(2f, 2), .001f);      // 正下方：最大
  assertEquals(1.0933f, DragMath.proximityScale(2f, 1), .001f);    // 隔一格：微微放大
  assertEquals(1.00f, DragMath.proximityScale(2f, 0), .001f);      // 隔两格：不变
  assertEquals(1.0933f, DragMath.proximityScale(2f, 3), .001f);
  assertEquals(1.1867f, DragMath.proximityScale(1.5f, 1), .001f);  // 拖动到两格之间：两格同样放大
  assertEquals(1.1867f, DragMath.proximityScale(1.5f, 2), .001f);
  assertEquals(1.14f, DragMath.proximityScale(1.25f, 2), .001f);   // 差 0.75 格
  assertEquals(0f, DragMath.proximity(0f, 3), .001f);
  assertEquals(1f, DragMath.proximity(0f, 0), .001f);
  // 任何输入都不允许出现「缩小」或越界放大
  for (int i = 0; i < 4; i++) {
   for (float t = 0f; t <= 3f; t += .25f) {
    float s = DragMath.proximityScale(t, i);
    assertTrue("缩放必须在 1.00~1.28 之间", s >= 1f && s <= DragMath.IconMaxScaleDelta + 1f + .001f);
   }
  }
 }

 /**
  * v15.1.6 的「手指左移 = 槽位 +1」换算已彻底删除：
  * 现在手指右移一槽，胶囊像素坐标必须**增大**（而不是减小）。
  */
 @Test public void legacyOppositeConventionIsGone() {
  float before = DragMath.capsuleOffset(1f, 96f, 4f);
  float after = DragMath.capsuleOffset(DragMath.dragTab(1f, 96f, 96f), 96f, 4f);
  assertTrue("右移后胶囊必须更靠右", after > before);
  assertEquals(96f, after - before, .001f);
  float back = DragMath.capsuleOffset(DragMath.dragTab(1f, -96f, 96f), 96f, 4f);
  assertTrue("左移后胶囊必须更靠左", back < before);
 }
}
