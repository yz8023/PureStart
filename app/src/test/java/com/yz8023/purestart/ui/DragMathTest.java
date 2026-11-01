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

 /**
  * v15.1.9 绝对定位：手指在哪，胶囊中心就在哪 —— 与胶囊按下的原位置无关。
  *
  * 回归 v15.1.7/15.1.8 的「相对模型」问题：胶囊在最左边、手指按在最右边时，
  * 旧实现里胶囊留在原地只跟着手指晃动，必须攒够一格位移才挪过去。
  */
 @Test public void capsuleCentersOnFinger() {
  final float w = 96f, inset = 4f;
  // 1) 任意手指位置上，胶囊中心都必须与手指像素重合（未钳位区间）
  for (float x = inset + w / 2f; x <= inset + 3f * w + w / 2f; x += 7f) {
   float tab = DragMath.tabAtCenter(x, w, inset);
   float center = DragMath.capsuleOffset(tab, w, inset) + w / 2f;
   // 用 assertTrue 避免 JUnit4 的 float/double 重载歧义
   assertTrue("胶囊中心应压住手指 x=" + x, Math.abs(x - center) <= .01f);
  }
  // 2) 与「胶囊原来在哪一格」无关：手指在右半边 → 直接是右边那一格
  assertEquals(2.5833f, DragMath.tabAtCenter(300f, w, inset), .01f);
  assertEquals(3, DragMath.nearestTab(DragMath.tabAtCenter(300f, w, inset)));
  // 3) 越界钳位 + 零宽安全
  assertEquals(0f, DragMath.tabAtCenter(-500f, w, inset), .001f);
  assertEquals(3f, DragMath.tabAtCenter(5000f, w, inset), .001f);
  assertEquals(0f, DragMath.tabAtCenter(10f, 0f, 0f), .001f);
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
  final float w = 96f, inset = 4f;
  // 手指停在「tab N 中心 + delta」处（绝对定位），再叠速度前瞻
  // 右移 20px + 向右快扫（+1200px/s）→ 前进（往右）一槽
  assertEquals(1, DragMath.landingTab(DragMath.tabAtCenter(inset + 0 * w + w / 2f + 20f, w, inset), 1200f, w));
  // 左移 20px + 向左快扫 → 后退一槽
  assertEquals(0, DragMath.landingTab(DragMath.tabAtCenter(inset + 1 * w + w / 2f - 20f, w, inset), -1200f, w));
  // 速度再大也不会越界
  assertEquals(3, DragMath.landingTab(DragMath.tabAtCenter(inset + 2 * w + w / 2f + 40f, w, inset), 9000f, w));
  assertEquals(0, DragMath.landingTab(DragMath.tabAtCenter(inset + 1 * w + w / 2f - 40f, w, inset), -9000f, w));
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
  *
  * v15.1.9 起用绝对模型验证同一件事：手指往右挪一槽，胶囊也往右挪一槽。
  */
 @Test public void legacyOppositeConventionIsGone() {
  final float w = 96f, inset = 4f;
  float x0 = inset + w + w / 2f;                 // 胶囊正好在 tab 1 中心时的手指位置
  float before = DragMath.capsuleOffset(DragMath.tabAtCenter(x0, w, inset), w, inset);
  float after = DragMath.capsuleOffset(DragMath.tabAtCenter(x0 + w, w, inset), w, inset);
  assertTrue("右移后胶囊必须更靠右", after > before);
  assertEquals(w, after - before, .001f);
  float back = DragMath.capsuleOffset(DragMath.tabAtCenter(x0 - w, w, inset), w, inset);
  assertTrue("左移后胶囊必须更靠左", back < before);
  assertEquals(w, before - back, .001f);
 }
}
