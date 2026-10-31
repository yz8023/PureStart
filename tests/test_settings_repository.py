#!/usr/bin/env python3
"""JVM contract smoke test with tiny Android preference stubs (not a device test)."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 boolean getBoolean(String k, boolean d); int getInt(String k, int d); String getString(String k, String d);
 Editor edit(); void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
 void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
 interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences p, String k); }
 interface Editor { Editor putBoolean(String k, boolean v); Editor putInt(String k,int v); Editor putString(String k,String v); void apply(); }
}''',
'android/content/Context.java': '''package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){ return this; }
 public abstract SharedPreferences getSharedPreferences(String n,int m);
}''',
'Test.java': '''import android.content.*;
import java.util.*;
import com.yz8023.purestart.Prefs;
import com.yz8023.purestart.ui.SettingsRepository;
public class Test {
 static class C extends Context {
   Map<String,P> files=new HashMap<>();
   public SharedPreferences getSharedPreferences(String n,int m){ return files.computeIfAbsent(n,k->new P()); }
 }
 static class P implements SharedPreferences {
   Map<String,Object> data=new HashMap<>(); List<OnSharedPreferenceChangeListener> ls=new ArrayList<>();
   public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
   public int getInt(String k,int d){return data.containsKey(k)?(Integer)data.get(k):d;}
   public String getString(String k,String d){return data.containsKey(k)?(String)data.get(k):d;}
   public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){ls.add(l);}
   public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){ls.remove(l);}
   public Editor edit(){return new Editor(){Map<String,Object> pending=new LinkedHashMap<>();
     public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
     public Editor putInt(String k,int v){pending.put(k,v);return this;}
     public Editor putString(String k,String v){pending.put(k,v);return this;}
     public void apply(){data.putAll(pending); for(String k:pending.keySet()) for(OnSharedPreferenceChangeListener l:new ArrayList<>(ls)) l.onSharedPreferenceChanged(P.this,k);}
   };}
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
   C c=new C(); SettingsRepository r=new SettingsRepository(c); String pkg="example.app";
   List<String> events=new ArrayList<>(); r.setObserver(events::add);
   check(!r.isHookEnabled(pkg)); r.setHookEnabled(pkg,true);
   check(Prefs.hookEnabled(c,pkg)); check(events.contains(Prefs.PREFIX_HOOK+pkg));
   for(SettingsRepository.Rule rule: SettingsRepository.Rule.values()){
     check(!r.isRuleEnabled(pkg,rule)); r.setRuleEnabled(pkg,rule,true); check(r.isRuleEnabled(pkg,rule));
   }
   int beforeReport=events.size(); Prefs.writeSelfCheck(c,pkg,"report");
   check("report".equals(r.selfCheck(pkg))); check(events.size()==beforeReport+1); check(events.contains(pkg));
   r.setThemeMode(2); check(Prefs.themeMode(c)==2); check(r.appBadgeEnabled());
   int n=events.size(); r.close(); r.close(); Prefs.setHookEnabled(c,pkg,false); Prefs.writeSelfCheck(c,pkg,"later"); check(events.size()==n);
   System.out.println("PASS: repository contract with JVM stubs");
 }
}'''
}
with tempfile.TemporaryDirectory() as d:
    d = Path(d)
    for name, content in STUBS.items():
        file = d / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding='utf-8')
    files = [str(d / name) for name in STUBS]
    files += [str(ROOT / 'src/com/yz8023/purestart/Prefs.java'),
              str(ROOT / 'src/com/yz8023/purestart/ui/SettingsRepository.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(d), *files], check=True)
    subprocess.run(['java', '-cp', str(d), 'Test'], check=True)
