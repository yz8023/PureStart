package com.yz8023.purestart.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public class DragMathTest {
 @Test public void ltrSupportsAllTabsAndBounds() {
  assertEquals(0, DragMath.nearestTab(DragMath.touchToPosition(0f, 400f, false)));
  assertEquals(3, DragMath.nearestTab(DragMath.touchToPosition(400f, 400f, false)));
  assertEquals(2, DragMath.nearestTab(DragMath.touchToPosition(250f, 400f, false)));
  // v15.1.6 方向约定：手指左移（负 delta）= 槽位增大
  assertEquals(3f, DragMath.dragBy(0f, -1000f, 400f, false), .001f);
  assertEquals(0f, DragMath.dragBy(3f, 1000f, 400f, false), .001f);
  assertEquals(1f, DragMath.dragBy(0f, -100f, 400f, false), .001f);
 }
 @Test public void rtlAndZeroWidthAreSafe() {
  assertEquals(3, DragMath.nearestTab(DragMath.touchToPosition(0f, 400f, true)));
  assertEquals(0, DragMath.nearestTab(DragMath.touchToPosition(400f, 400f, true)));
  // RTL：手指右移（正 delta）= 槽位增大
  assertEquals(3f, DragMath.dragBy(2f, 100f, 400f, true), .001f);
  assertEquals(0f, DragMath.touchToPosition(5f, 0f, false), .001f);
  assertEquals(3f, DragMath.dragBy(10f, 1f, 0f, false), .001f);
 }

 /** 底栏拖拽：位移落在半槽以内吸附原页，越过则翻一页；反向同理。 */
 @Test public void dockSettlesToNearestSlot() {
  assertEquals(0, DragMath.settleDockPage(0.2f, 0f));
  assertEquals(1, DragMath.settleDockPage(0.8f, 0f));
  assertEquals(3, DragMath.settleDockPage(3f, 0f));
  assertEquals(0, DragMath.settleDockPage(0f, 0f));
 }

 /** 快速甩动：即使位移不足半槽也顺速度方向多走一槽，但不会越界。 */
 @Test public void dockFlingMovesOnePage() {
  assertEquals(1, DragMath.settleDockPage(0.1f, 5f));
  assertEquals(0, DragMath.settleDockPage(1f, -5f));
  assertEquals(3, DragMath.settleDockPage(2.9f, 5f));
  assertEquals(1, DragMath.settleDockPage(1f, 0f));   // 无速度时停在最近槽位
 }

 /** v15.1.6：手指左移（负位移）必须往「设置」方向走 = 槽位 +1，与页面滑动同向。 */
 @Test public void dragDirectionMatchesPager() {
  // 单槽宽度 = 400/4 = 100px；左移 100px → 前进一页
  assertEquals(1f, DragMath.slotDelta(-100f, 400f, false), .001f);
  assertEquals(-1f, DragMath.slotDelta(100f, 400f, false), .001f);
  assertEquals(0f, DragMath.slotDelta(0f, 400f, false), .001f);
  // 从第 1 页左移一槽 → 2；右移一槽 → 0
  assertEquals(2, DragMath.settleDockPage(1f + DragMath.slotDelta(-100f, 400f, false), 0f));
  assertEquals(0, DragMath.settleDockPage(1f + DragMath.slotDelta(100f, 400f, false), 0f));
  assertEquals(2, DragMath.nearestTab(1f + DragMath.slotDelta(-60f, 400f, false)), 0f);
  // RTL：手指右移才是前进
  assertEquals(1f, DragMath.slotDelta(100f, 400f, true), .001f);
  // 速度方向与位移方向一致（同一条约定）
  assertEquals(DragMath.slotDelta(500f, 400f, false), DragMath.slotsPerSecond(500f, 400f, false), .001f);
 }

 @Test public void velocityConversionIsRtlAware() {
  // v15.1.6 方向约定：右手方向（+400px）表示后退，所以是 -4 槽/秒
  assertEquals(-4f, DragMath.slotsPerSecond(400f, 400f, false), .001f);
  assertEquals(4f, DragMath.slotsPerSecond(400f, 400f, true), .001f);
  assertEquals(0f, DragMath.slotsPerSecond(400f, 0f, false), .001f);
 }
}
