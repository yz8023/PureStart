package com.yz8023.purestart;

/**
 * Hook 开关闸门：Hook 侧在拦截回调触发时按 key 查询设置页状态。
 * key ∈ {"enabled", "splash", "feed", "popup", "dexfinder"}。
 * 由 Main 实现（Provider 跨进程读取 + 2s 缓存），Hook 类只管调用。
 */
public interface HookGate {
    boolean on(String key);
}
