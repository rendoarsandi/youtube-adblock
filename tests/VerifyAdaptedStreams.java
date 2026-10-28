import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual-APK ART contracts only. No app startup, settings/account reads, or network calls. */
public final class VerifyAdaptedStreams {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Object allocate(Class<?> type)throws Exception{
  Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Field field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);Object unsafe=field.get(null);
  return unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,type);
 }
 static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 static void verifyOwners(ClassLoader loader)throws Exception{
  String[] owners={"arhj","ukr","akul","afnb","dlu","djs",
   "app.morphe.extension.youtube.patches.FixContentProviderPatch",
   "app.morphe.extension.youtube.patches.utils.PlayerTypeHookPatch",
   "app.morphe.extension.youtube.shared.VideoState",
   "app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch",
   "app.morphe.extension.shared.spoof.requests.StreamOrDetailsDataRequest",
   "app.morphe.extension.shared.spoof.requests.PlayerRoutes",
   "app.morphe.extension.shared.spoof.ClientType"};
  int count=0;
  for(String name:owners){Class<?> type=Class.forName(name,false,loader);Method[] methods=type.getDeclaredMethods();type.getDeclaredConstructors();check(methods.length>0,"Expected executable owner methods: "+name);count++;System.out.println("PASS: ART resolved "+name);}
  check(count==13,"Owner verification incomplete");
 }
 static void verifyProviderBoundary(ClassLoader loader)throws Exception{
  Class<?> helper=Class.forName("app.morphe.extension.youtube.patches.FixContentProviderPatch",true,loader);
  Method add=helper.getMethod("addNullMapEntry",Map.class),remove=helper.getMethod("removeNullMapEntries",Map.class);
  Map<String,String> rows=new HashMap<>();String keep=new String("retained-value");rows.put("keep",keep);rows.put("empty","");rows.put("null_value",null);add.invoke(null,rows);
  boolean rejected=false;try{new ConcurrentHashMap<String,String>().putAll(rows);}catch(NullPointerException expected){rejected=true;}
  check(rejected,"Stock ConcurrentHashMap did not reject unsanitized null values");
  remove.invoke(null,rows);
  check(rows.size()==2 && rows.get("keep")==keep && rows.containsKey("empty") && rows.get("empty").equals(""),"Actual null-removal helper removed/changed non-null provider values");
  check(!rows.containsValue(null),"Actual null-removal helper retained null values");
  ConcurrentHashMap<String,String> cache=new ConcurrentHashMap<>();cache.putAll(rows);
  check(cache.size()==2 && cache.get("keep")==keep && cache.containsKey("empty"),"Stock provider-cache putAll did not preserve sanitized rows");
  System.out.println("PASS: actual provider helper removes injected and native null values before stock ConcurrentHashMap.putAll while preserving non-null/empty values");
 }
 static void verifyNullState(ClassLoader loader)throws Exception{
  Class<?> controller=Class.forName("arhj",true,loader),state=Class.forName("arhd",true,loader);
  Object instance=allocate(controller),previous=allocate(state);
  field(controller,"k").set(instance,allocate(Class.forName("android.widget.ImageView")));
  Object first=allocate(field(controller,"f").getType()),second=allocate(field(controller,"e").getType());
  field(controller,"f").set(instance,first);field(controller,"e").set(instance,second);field(controller,"c").set(instance,previous);
  controller.getMethod("a",state).invoke(instance,new Object[]{null});
  check(field(controller,"c").get(instance)==previous,"Null state changed the native controller's previous state");
  System.out.println("PASS: actual injected state method accepts null and preserves native previous-state behavior without invoking the enum hook");
 }
 public static void main(String[] args){
  try{
   check(args.length==2,"Usage: READONLY_APK PRIVATE_OPT_DIR");
   Constructor<?> constructor=Class.forName("dalvik.system.DexClassLoader").getConstructor(String.class,String.class,String.class,ClassLoader.class);
   ClassLoader loader=(ClassLoader)constructor.newInstance(args[0],args[1],null,VerifyAdaptedStreams.class.getClassLoader());
   verifyOwners(loader);verifyProviderBoundary(loader);verifyNullState(loader);
   System.out.println("PASS: adapted-streams ART checks complete; no application launch or network operations");
  }catch(Throwable failure){System.out.println("FAIL: "+failure);failure.printStackTrace(System.out);System.exit(1);}
 }
}
