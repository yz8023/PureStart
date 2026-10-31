package com.yz8023.purestart.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public class DragMathTest {
 @Test public void ltrSupportsAllTabsAndBounds() {
  assertEquals(0, DragMath.nearestTab(DragMath.touchToPosition(0f, 400f, false)));
  assertEquals(3, DragMath.nearestTab(DragMath.touchToPosition(400f, 400f, false)));
  assertEquals(2, DragMath.nearestTab(DragMath.touchToPosition(250f, 400f, false)));
  assertEquals(0f, DragMath.dragBy(0f, -1000f, 400f, false), .001f);
  assertEquals(3f, DragMath.dragBy(3f, 1000f, 400f, false), .001f);
 }
 @Test public void rtlAndZeroWidthAreSafe() {
  assertEquals(3, DragMath.nearestTab(DragMath.touchToPosition(0f, 400f, true)));
  assertEquals(0, DragMath.nearestTab(DragMath.touchToPosition(400f, 400f, true)));
  assertEquals(1f, DragMath.dragBy(2f, 100f, 400f, true), .001f);
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

 @Test public void velocityConversionIsRtlAware() {
  assertEquals(4f, DragMath.slotsPerSecond(400f, 400f, false), .001f);
  assertEquals(-4f, DragMath.slotsPerSecond(400f, 400f, true), .001f);
  assertEquals(0f, DragMath.slotsPerSecond(400f, 0f, false), .001f);
 }
}
