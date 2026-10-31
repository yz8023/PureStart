package com.yz8023.purestart;

import org.junit.After;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Tests the actual deferred XiaoCan gate dispatch, without an Android device. */
public class XiaoCanGateTest {
    private void gate(HookGate value) throws Exception {
        Field f = XiaoCanHook.class.getDeclaredField("gate");
        f.setAccessible(true);
        f.set(null, value);
    }
    private boolean on(String key) throws Exception {
        Method m = XiaoCanHook.class.getDeclaredMethod("on", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, key);
    }
    @After public void reset() throws Exception { gate(null); }
    @Test public void unavailableConfigIsClosed() throws Exception {
        gate(null); assertFalse(on("enabled")); assertFalse(on("splash"));
    }
    @Test public void masterSwitchControlsEverySubfeature() throws Exception {
        gate(key -> !key.equals("enabled"));
        assertFalse(on("enabled")); assertFalse(on("splash")); assertFalse(on("feed"));
        gate(key -> key.equals("enabled") || key.equals("splash"));
        assertTrue(on("enabled")); assertTrue(on("splash")); assertFalse(on("feed"));
    }
}
