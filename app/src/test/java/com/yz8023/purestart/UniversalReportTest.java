package com.yz8023.purestart;

import java.lang.reflect.Proxy;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import io.github.libxposed.api.XposedInterface;

/** Hook registration accounting; fake registration does not execute on Android. */
public class UniversalReportTest {
    private XposedInterface fake(String failName) {
        return (XposedInterface) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{XposedInterface.class}, (proxy, method, args) -> {
                if (!method.getName().equals("hook")) return null;
                Executable target = (Executable) args[0];
                if (target.getName().equals(failName)) throw new IllegalStateException("forced failure");
                return Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{XposedInterface.HookBuilder.class},
                    (p, m, a) -> m.getReturnType().isInstance(p) ? p : null);
            });
    }
    static class Report implements Main.Reporter {
        final List<String> ok = new ArrayList<>(), failed = new ArrayList<>();
        public void ok(String s) { ok.add(s); }
        public void miss(String s) {}
        public void fail(String s, Throwable t) { failed.add(s); }
        public void republish() {}
    }
    @Test public void reportsOnlySuccessfulRegistrations() {
        Report all = new Report();
        UniversalAdHook.install(fake(""), getClass().getClassLoader(), "test.pkg", key -> true, all);
        assertEquals(3, all.ok.size()); assertTrue(all.failed.isEmpty());
        Report partial = new Report();
        UniversalAdHook.install(fake("addView"), getClass().getClassLoader(), "test.pkg", key -> true, partial);
        assertEquals(2, partial.ok.size()); assertEquals(1, partial.failed.size());
        assertTrue(partial.failed.get(0).contains("addView"));
    }
}
