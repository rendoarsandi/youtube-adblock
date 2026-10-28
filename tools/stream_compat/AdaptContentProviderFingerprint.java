/* Local adaptation utility, Codex, 2026-10-28.
 * Pinned anddea/revanced-patches v4.4.0-dev.3 source, GPL-3.0.
 * Reads APK metadata, modifies one fingerprint class and adds one location class.
 * Does not apply APK patches or execute target app code.
 */
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.net.*;
import java.security.*;
import jdk.internal.org.objectweb.asm.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import app.morphe.patcher.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
public final class AdaptContentProviderFingerprint {
 static final String INPUT_SHA="5c24a6cff6b336315aa2cf4661cb549070f39fc8c27501e93e82dc0f0281f89e";
 static final String ENTRY="app/morphe/patches/youtube/utils/fix/contentprovider/UnstableContentProviderFingerprint.class";
 static final String CLASS="app.morphe.patches.youtube.utils.fix.contentprovider.UnstableContentProviderFingerprint";
 static final String CLASS_SHA="a4e9abe8c7ac08ed191c789ee047e69127b353a4daa35876f066284a1adbddd2";
 static final String HELPER="app/morphe/patches/local/ContentProviderDiagnosticAnywhere";
 static final String BODY_SHA="aa0f7c69c384c8d99c57ce70e869404bdf56c8c1e85f387ae9f173e9969ce31d";
 static final String TARGET="Lukr;->e(Landroid/content/ContentResolver;[Ljava/lang/String;)V";
 static final String DIAGNOSTIC="ContentProvider query returned null cursor";
 static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
 static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 static String id(com.android.tools.smali.dexlib2.iface.Method m){return m.getDefiningClass()+"->"+m.getName()+"("+String.join("",m.getParameterTypes())+")"+m.getReturnType();}
 record Candidate(com.android.tools.smali.dexlib2.iface.Method method,List<Instruction> instructions){}
 record Evidence(String apkSha,List<Candidate> candidates,Candidate target){}
 static Evidence inspect(Path apk)throws Exception{
  List<Candidate> candidates=new ArrayList<>();Candidate target=null;
  try(ZipFile z=new ZipFile(apk.toFile())){
   for(ZipEntry e:Collections.list(z.entries())){
    if(!e.getName().matches("classes(?:[2-9][0-9]*)?\\.dex"))continue;
    DexBackedDexFile dex=new DexBackedDexFile(null,ByteBuffer.wrap(z.getInputStream(e).readAllBytes()));
    for(ClassDef c:dex.getClasses())for(com.android.tools.smali.dexlib2.iface.Method m:c.getMethods()){
     if((m.getAccessFlags()&17)!=17 || !m.getReturnType().equals("V") || !m.getParameterTypes().toString().equals("[Landroid/content/ContentResolver;, [Ljava/lang/String;]") || m.getImplementation()==null)continue;
     List<Instruction> ins=new ArrayList<>();m.getImplementation().getInstructions().forEach(ins::add);Candidate candidate=new Candidate(m,ins);candidates.add(candidate);
     if(id(m).equals(TARGET)){
      check(target==null,"Duplicate target");target=candidate;var impl=m.getImplementation();int units=ins.stream().mapToInt(Instruction::getCodeUnits).sum();
      check(m.getAccessFlags()==17 && impl.getRegisterCount()==10 && impl.getTryBlocks().size()==9 && ins.size()==103 && units==198,"Target frame/body shape differs");
      var first=(DexBackedInstruction)ins.get(0);byte[] raw=new byte[units*2];first.dexFile.getDataBuffer().readByteRange(first.instructionStart,units*2).get(raw);check(sha(raw).equals(BODY_SHA),"Target raw digest differs");
     }
    }
   }
  }
  check(target!=null,"Target method missing");return new Evidence(sha(Files.readAllBytes(apk)),candidates,target);
 }
 static byte[] adapt(byte[] input){
  ClassReader reader=new ClassReader(input);ClassWriter writer=new ClassWriter(reader,0);int[] changes={0};
  reader.accept(new ClassVisitor(ASM8,writer){
   @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
    MethodVisitor base=super.visitMethod(access,name,desc,signature,exceptions);
    return new MethodVisitor(ASM8,base){
     @Override public void visitMethodInsn(int opcode,String owner,String methodName,String methodDesc,boolean isInterface){
      if(owner.equals("app/morphe/patcher/InstructionFilterKt") && methodName.equals("string$default")){
       check(name.equals("<init>") && opcode==INVOKESTATIC && methodDesc.equals("(Ljava/lang/String;Lapp/morphe/patcher/InstructionLocation;ILjava/lang/Object;)Lapp/morphe/patcher/StringFilter;"),"Unexpected string factory call");
       super.visitMethodInsn(opcode,HELPER,"stringAnywhere",methodDesc,false);changes[0]++;
      }else super.visitMethodInsn(opcode,owner,methodName,methodDesc,isInterface);
     }
    };
   }
  },0);check(changes[0]==1,"Expected exactly one string factory redirection");return writer.toByteArray();
 }
 static Fingerprint load(Path bundle)throws Exception{
  URLClassLoader loader=new URLClassLoader(new URL[]{bundle.toUri().toURL()},AdaptContentProviderFingerprint.class.getClassLoader());
  Class<?> cls=Class.forName(CLASS,true,loader);return (Fingerprint)cls.getField("INSTANCE").get(null);
 }
 static List<Integer> match(Fingerprint fp,Candidate c){
  int previous=-1;List<Integer> indices=new ArrayList<>();
  for(InstructionFilter filter:fp.getFilters()){
   InstructionLocation loc=filter.getLocation();int i=loc.searchStartIndex(previous,c.instructions().size());boolean found=false;
   while(i>=0 && i<c.instructions().size() && loc.indexIsValidForMatching(previous,i)){
    if(filter.matches(c.method(),c.instructions().get(i))){indices.add(i);previous=i;found=true;break;}i+=loc.getSearchesBackwards()?-1:1;
   }
   if(!found)return List.of();
  }
  return indices;
 }
 static int write(Path input,Path output,byte[] cls,byte[] helper)throws Exception{
  try(ZipFile a=new ZipFile(input.toFile());ZipOutputStream b=new ZipOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))){
   if(a.getComment()!=null)b.setComment(a.getComment());
   for(ZipEntry e:Collections.list(a.entries())){
    check(!e.getName().equals(HELPER+".class"),"Helper already bundled");byte[] data=e.getName().equals(ENTRY)?cls:a.getInputStream(e).readAllBytes();ZipEntry n=new ZipEntry(e);CRC32 crc=new CRC32();crc.update(data);n.setSize(data.length);n.setCrc(crc.getValue());n.setCompressedSize(n.getMethod()==ZipEntry.STORED?data.length:-1);b.putNextEntry(n);b.write(data);b.closeEntry();
   }
   ZipEntry n=new ZipEntry(HELPER+".class");n.setTime(0);b.putNextEntry(n);b.write(helper);b.closeEntry();
  }
  int equal=0,diff=0;try(ZipFile a=new ZipFile(input.toFile());ZipFile b=new ZipFile(output.toFile())){
   check(b.size()==a.size()+1,"Expected one added helper entry");
   for(ZipEntry e:Collections.list(a.entries())){
    ZipEntry n=b.getEntry(e.getName());check(n!=null,"Entry removed");boolean same=Arrays.equals(a.getInputStream(e).readAllBytes(),b.getInputStream(n).readAllBytes());if(same)equal++;else{check(e.getName().equals(ENTRY),"Unexpected changed entry "+e.getName());diff++;}
   }
  }
  check(diff==1,"Expected one modified fingerprint entry");return equal;
 }
 public static void main(String[] a)throws Exception{
  check(a.length==4,"Usage: STATE_FIXED_MPP INPUT_APK HELPER_CLASS NEW_MPP");Path input=Path.of(a[0]),apk=Path.of(a[1]),helper=Path.of(a[2]),output=Path.of(a[3]);check(!Files.exists(output),"Output must be new");check(sha(Files.readAllBytes(input)).equals(INPUT_SHA),"Input state-fixed bundle digest differs");Evidence e=inspect(apk);byte[] original;
  try(ZipFile z=new ZipFile(input.toFile())){original=z.getInputStream(z.getEntry(ENTRY)).readAllBytes();}check(sha(original).equals(CLASS_SHA),"Original fingerprint class differs");
  Fingerprint before=load(input);check(before.getFilters().size()==2 && before.getFilters().get(0) instanceof MethodCallFilter && before.getFilters().get(1) instanceof StringFilter,"Unexpected original filter types/order");
  check(match(before,e.target()).isEmpty(),"Original fingerprint unexpectedly matches target");
  byte[] changed=adapt(original);int unchanged=write(input,output,changed,Files.readAllBytes(helper));Fingerprint after=load(output);check(after.getFilters().size()==2 && after.getFilters().get(0) instanceof MethodCallFilter && after.getFilters().get(1) instanceof StringFilter,"Filter order/type changed");
  check(((StringFilter)after.getFilters().get(1)).getString().invoke().equals(DIAGNOSTIC),"Diagnostic value changed");
  check(after.getFilters().get(1).getLocation().searchStartIndex(90,103)==0 && !after.getFilters().get(1).getLocation().getSearchesBackwards(),"New filter is not a forward whole-method search");
  int matched=0;List<Integer> targetMatches=null;
  for(Candidate c:e.candidates()){
   List<Integer> b=match(before,c),n=match(after,c);
   if(!n.isEmpty()){matched++;check(id(c.method()).equals(TARGET),"Unexpected newly matching method");targetMatches=n;}
   if(!id(c.method()).equals(TARGET))check(b.equals(n),"Other candidate result changed");
  }
  check(matched==1 && targetMatches!=null,"Expected exactly one matching target");
  var first=e.target().instructions().get(targetMatches.get(0));check(first instanceof FiveRegisterInstruction && first instanceof ReferenceInstruction,"First match cannot provide map argument");
  check(((ReferenceInstruction)first).getReference().toString().equals("Ljava/util/concurrent/ConcurrentMap;->putAll(Ljava/util/Map;)V"),"First match not ConcurrentMap.putAll");
  check(((FiveRegisterInstruction)first).getRegisterD()==0,"Expected source map in v0");
  String report="Local content-provider fingerprint adaptation, Codex, 2026-10-28\n"
   +"Upstream: https://github.com/anddea/revanced-patches/tree/v4.4.0-dev.3 (GPL-3.0); original bundled credits retained.\n"
   +"Source: patches/src/main/kotlin/app/morphe/patches/youtube/utils/fix/contentprovider/Fingerprints.kt\n"
   +"Input APK SHA-256: "+e.apkSha()+"\nTarget: "+TARGET+"\nTarget raw digest: "+BODY_SHA+"\n"
   +"External guard: flags17, registers10, 103 instructions, 198 code units, nine try blocks.\n"
   +"Input MPP SHA-256: "+INPUT_SHA+"\nNew MPP SHA-256: "+sha(Files.readAllBytes(output))+"\n"
   +"Modified class: "+ENTRY+"\nOriginal class SHA-256: "+CLASS_SHA+"\nNew class SHA-256: "+sha(changed)+"\n"
   +"Added helper: "+HELPER+".class; SHA-256 "+sha(Files.readAllBytes(helper))+"\n"
   +"All "+unchanged+" other original entry payloads unchanged, including previous state fingerprint adaptation.\n"
   +"Actual getters: filter[0] MethodCallFilter, filter[1] StringFilter with the same diagnostic and a whole-method location.\n"
   +"Actual filter evaluation: original target does not match; new target uniquely matches among "+e.candidates().size()+" signature candidates.\n"
   +"Instruction match indices (not sorted): "+targetMatches+"; first is FiveRegisterInstruction ConcurrentMap.putAll(Map), map argument registerD=v0.\n"
   +"Both filters retained and order preserved; before/after diagnostic layouts accepted. Repro and null-removal patches untouched.\n"
   +"Limit: no APK patching, target app execution, signing, installation, or account/network action performed.\n";
  Files.writeString(Path.of(output+".adaptation.txt"),report,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.print(report);
 }
}
