#!/usr/bin/env python3
"""JVM smoke test using in-memory Android stubs and org.json (not a device test)."""
from pathlib import Path
import subprocess, tempfile, urllib.request

ROOT = Path(__file__).resolve().parents[1]
FILES = {
'android/net/Uri.java': '''package android.net; public class Uri {}''',
'android/content/ContentResolver.java': '''package android.content;
import java.io.*; import android.net.Uri;
public abstract class ContentResolver { public abstract InputStream openInputStream(Uri uri) throws IOException; public abstract OutputStream openOutputStream(Uri uri) throws IOException; }''',
'android/content/Context.java': '''package android.content;
public abstract class Context { public static final int MODE_PRIVATE=0;
public Context getApplicationContext(){return this;} public abstract SharedPreferences getSharedPreferences(String n,int m); public abstract ContentResolver getContentResolver(); }''',
'android/content/SharedPreferences.java': '''package android.content; import java.util.Map;
public interface SharedPreferences { boolean getBoolean(String k,boolean d); int getInt(String k,int d); String getString(String k,String d); Map<String,?> getAll(); Editor edit();
interface Editor { Editor putBoolean(String k,boolean v); Editor putInt(String k,int v); Editor putLong(String k,long v); Editor putFloat(String k,float v); Editor putString(String k,String v); void apply(); } }''',
'TestBackup.java': '''import android.content.*; import android.net.Uri; import java.io.*; import java.util.*;
import com.yz8023.purestart.Prefs; import com.yz8023.purestart.ui.BackupRepository;
public class TestBackup {
 static class C extends Context { Map<String,P> files=new HashMap<>(); R resolver=new R();
  public SharedPreferences getSharedPreferences(String n,int m){return files.computeIfAbsent(n,k->new P());}
  public ContentResolver getContentResolver(){return resolver;} }
 static class R extends ContentResolver { byte[] data; boolean nullRead, nullWrite;
  public InputStream openInputStream(Uri u){return nullRead?null:new ByteArrayInputStream(data);}
  public OutputStream openOutputStream(Uri u){if(nullWrite)return null; return new ByteArrayOutputStream(){public void close(){data=toByteArray();}};} }
 static class P implements SharedPreferences { Map<String,Object> values=new HashMap<>();
  public Map<String,?> getAll(){return values;} public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
  public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}
  public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
  public Editor edit(){return new Editor(){Map<String,Object> writes=new HashMap<>();
   public Editor putBoolean(String k,boolean v){writes.put(k,v);return this;} public Editor putInt(String k,int v){writes.put(k,v);return this;}
   public Editor putLong(String k,long v){writes.put(k,v);return this;} public Editor putFloat(String k,float v){writes.put(k,v);return this;}
   public Editor putString(String k,String v){writes.put(k,v);return this;} public void apply(){values.putAll(writes);} };} }
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args) throws Exception {
  C source=new C(); Uri uri=new Uri(); P sp=(P)Prefs.sp(source); sp.values.put("switch",true); sp.values.put("number",4); sp.values.put("text","中文"); sp.values.put("unsupported",Arrays.asList("x"));
  BackupRepository r=new BackupRepository(source); check(r.exportTo(uri)==3); check(new String(source.resolver.data,"UTF-8").contains("中文"));
  C dest=new C(); dest.resolver.data=source.resolver.data; P to=(P)Prefs.sp(dest); to.values.put("keep","untouched"); to.values.put("switch",false);
  check(new BackupRepository(dest).importFrom(uri)==3); check(Boolean.TRUE.equals(to.values.get("switch"))); check(Integer.valueOf(4).equals(to.values.get("number"))); check("中文".equals(to.values.get("text"))); check("untouched".equals(to.values.get("keep")));
  dest.resolver.data="not json".getBytes("UTF-8"); try {new BackupRepository(dest).importFrom(uri); throw new AssertionError();} catch(org.json.JSONException expected){} check("untouched".equals(to.values.get("keep")));
  dest.resolver.nullRead=true; try {new BackupRepository(dest).importFrom(uri); throw new AssertionError();} catch(IOException expected){}
  source.resolver.nullWrite=true; try {r.exportTo(uri); throw new AssertionError();} catch(IOException expected){}
  System.out.println("PASS: backup export/import, merge, invalid JSON and I/O failures (JVM stubs)");
 }
}'''
}
with tempfile.TemporaryDirectory() as temp:
    path = Path(temp)
    jar = path / 'json.jar'
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
    for name, content in FILES.items():
        target = path / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
    sources = [str(path / name) for name in FILES]
    sources += [str(ROOT / 'src/com/yz8023/purestart/Prefs.java'), str(ROOT / 'src/com/yz8023/purestart/ui/BackupRepository.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(path), *sources], check=True)
    subprocess.run(['java', '-cp', f'{path}:{jar}', 'TestBackup'], check=True)
