/* Local adaptation utility, Codex, 2026-10-28.
 * Pinned upstream: anddea/revanced-patches v4.4.0-dev.3, GPL-3.0.
 * Retains upstream files and authorship. Changes one compiled fingerprint class.
 * Does not modify or execute target app code; input APK is read for static guards.
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import java.net.*;
import java.lang.reflect.*;
import jdk.internal.org.objectweb.asm.*;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public final class AdaptStateFingerprint {
 static final String ORIGINAL_MPP_SHA="04c02b35bd48e8160fbc30519113659242073c3958dee954f60b22bc582eae57";
 static final String ENTRY="app/morphe/patches/youtube/utils/playertype/PlayerTypeHookPatchKt.class";
 static final String CLASS_NAME="app.morphe.patches.youtube.utils.playertype.PlayerTypeHookPatchKt";
 static final String ORIGINAL_CLASS_SHA="5de9aac1cd53d8273d09b4eb7173351ed36d604005988a8f0e908b1ee9d54d40";
 static final String BODY_SHA="fdb4c28d272785ae6ee9ffe969de31eff0e2a0c2a21af63543b66b128741fd76";
 static final String PREDICATE="playerTypeHookPatch$lambda$0$0$4";
 static final String PREDICATE_DESC="(Lcom/android/tools/smali/dexlib2/iface/Method;Lcom/android/tools/smali/dexlib2/iface/ClassDef;)Z";
 static final String METHOD_REF="com/android/tools/smali/dexlib2/iface/reference/MethodReference";
 static final String METHOD="com/android/tools/smali/dexlib2/iface/Method";
 static final String IMPL="com/android/tools/smali/dexlib2/iface/MethodImplementation";
 static final String NULL_LABEL="local_exact_state_hook_skip_2140161";
 static final String RECIPE_MARKER="# copyvideoState parameter field";
 static final String FIELD="Larhd;->a:Larhc;";
 static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
 static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
 static String methodId(com.android.tools.smali.dexlib2.iface.Method m) {
  StringBuilder s=new StringBuilder(m.getDefiningClass()).append("->").append(m.getName()).append('(');
  for(CharSequence p:m.getParameterTypes())s.append(p);return s.append(')').append(m.getReturnType()).toString();
 }
 static boolean signature(com.android.tools.smali.dexlib2.iface.Method m) {
  return m.getAccessFlags()==17 && m.getReturnType().equals("V") && m.getParameterTypes().size()==1 && m.getParameterTypes().get(0).toString().equals("Larhd;");
 }
 static List<Instruction> list(com.android.tools.smali.dexlib2.iface.Method m) {
  List<Instruction> a=new ArrayList<>();if(m.getImplementation()!=null)m.getImplementation().getInstructions().forEach(a::add);return a;
 }
 static List<Integer> patternPositions(com.android.tools.smali.dexlib2.iface.Method m) {
  List<Instruction> a=list(m);List<Integer> hits=new ArrayList<>();
  for(int i=0;i+3<a.size();i++)if(a.get(i).getOpcode()==Opcode.IF_EQZ && a.get(i+1).getOpcode()==Opcode.IGET_OBJECT && a.get(i+2).getOpcode()==Opcode.IGET_OBJECT && a.get(i+3).getOpcode()==Opcode.IF_NE)hits.add(i);
  return hits;
 }
 record InputEvidence(String apkSha,String dexSha,int scanned,int candidates,int patternMatches,ClassDef targetClass,com.android.tools.smali.dexlib2.iface.Method targetMethod,List<com.android.tools.smali.dexlib2.iface.Method> candidateMethods) {}
 static InputEvidence inspectApk(Path apk) throws Exception {
  ClassDef targetClass=null;com.android.tools.smali.dexlib2.iface.Method target=null;int scanned=0,candidates=0,patterns=0;String dexSha=null;
  List<com.android.tools.smali.dexlib2.iface.Method> candidateMethods=new ArrayList<>();
  try(ZipFile z=new ZipFile(apk.toFile())) {
   for(ZipEntry e:Collections.list(z.entries())) {
    if(!e.getName().matches("classes(?:[2-9][0-9]*)?\\.dex"))continue;
    byte[] data=z.getInputStream(e).readAllBytes();DexBackedDexFile d=new DexBackedDexFile(null,java.nio.ByteBuffer.wrap(data));
    for(ClassDef c:d.getClasses())for(com.android.tools.smali.dexlib2.iface.Method m:c.getMethods()) {
     scanned++;
     if(signature(m)) {
      candidates++;candidateMethods.add(m);var hits=patternPositions(m);if(!hits.isEmpty())patterns++;
      if(methodId(m).equals("Larhj;->a(Larhd;)V")) {
       require(target==null,"Duplicate target method");target=m;targetClass=c;dexSha=sha(data);
       require(hits.size()==1,"Target pattern must occur once");List<Instruction> ins=list(m);int pos=hits.get(0);
       require(ins.get(pos+1) instanceof ReferenceInstruction && ins.get(pos+2) instanceof ReferenceInstruction,"Expected field references");
       require(((ReferenceInstruction)ins.get(pos+1)).getReference().toString().equals(FIELD),"First matched field differs");
       require(((ReferenceInstruction)ins.get(pos+2)).getReference().toString().equals(FIELD),"Previous-state field differs");
       require(((TwoRegisterInstruction)ins.get(pos+1)).getRegisterB()==6,"Matched first field is not read from p1");
       var body=m.getImplementation();require(body.getRegisterCount()==7 && body.getTryBlocks().isEmpty(),"Target frame/try shape differs");
       int units=ins.stream().mapToInt(Instruction::getCodeUnits).sum();require(units==283 && ins.size()==134,"Target instruction shape differs");
       var first=(DexBackedInstruction)ins.get(0);byte[] raw=new byte[units*2];first.dexFile.getDataBuffer().readByteRange(first.instructionStart,units*2).get(raw);
       require(sha(raw).equals(BODY_SHA),"Target raw instruction digest differs");
      }
     }
    }
   }
  }
  require(target!=null && patterns==1,"Expected exactly one public-final Larhd method with the opcode pattern");
  return new InputEvidence(sha(Files.readAllBytes(apk)),dexSha,scanned,candidates,patterns,targetClass,target,candidateMethods);
 }
 static void stringCheck(MethodVisitor mv,String owner,String getter,String expected,Label original) {
  mv.visitLdcInsn(expected);mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,owner,getter,"()Ljava/lang/String;",true);
  mv.visitMethodInsn(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false);mv.visitJumpInsn(IFEQ,original);
 }
 static void addExactFallback(MethodVisitor mv) {
  Label original=new Label();
  stringCheck(mv,METHOD_REF,"getDefiningClass","Larhj;",original);
  stringCheck(mv,METHOD_REF,"getName","a",original);
  stringCheck(mv,METHOD_REF,"getReturnType","V",original);
  mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,METHOD,"getAccessFlags","()I",true);mv.visitIntInsn(BIPUSH,17);mv.visitJumpInsn(IF_ICMPNE,original);
  mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,METHOD_REF,"getParameterTypes","()Ljava/util/List;",true);
  mv.visitMethodInsn(INVOKEINTERFACE,"java/util/List","size","()I",true);mv.visitInsn(ICONST_1);mv.visitJumpInsn(IF_ICMPNE,original);
  mv.visitLdcInsn("Larhd;");mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,METHOD_REF,"getParameterTypes","()Ljava/util/List;",true);
  mv.visitInsn(ICONST_0);mv.visitMethodInsn(INVOKEINTERFACE,"java/util/List","get","(I)Ljava/lang/Object;",true);
  mv.visitMethodInsn(INVOKEVIRTUAL,"java/lang/Object","toString","()Ljava/lang/String;",false);
  mv.visitMethodInsn(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false);mv.visitJumpInsn(IFEQ,original);
  mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,METHOD,"getImplementation","()Lcom/android/tools/smali/dexlib2/iface/MethodImplementation;",true);mv.visitJumpInsn(IFNULL,original);
  mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,METHOD,"getImplementation","()Lcom/android/tools/smali/dexlib2/iface/MethodImplementation;",true);
  mv.visitMethodInsn(INVOKEINTERFACE,IMPL,"getRegisterCount","()I",true);mv.visitIntInsn(BIPUSH,7);mv.visitJumpInsn(IF_ICMPNE,original);
  mv.visitVarInsn(ALOAD,0);mv.visitMethodInsn(INVOKEINTERFACE,METHOD,"getImplementation","()Lcom/android/tools/smali/dexlib2/iface/MethodImplementation;",true);
  mv.visitMethodInsn(INVOKEINTERFACE,IMPL,"getTryBlocks","()Ljava/util/List;",true);mv.visitMethodInsn(INVOKEINTERFACE,"java/util/List","isEmpty","()Z",true);mv.visitJumpInsn(IFEQ,original);
  mv.visitInsn(ICONST_1);mv.visitInsn(IRETURN);
  mv.visitLabel(original);mv.visitFrame(F_SAME,0,null,0,null);
 }
 static byte[] adaptClass(byte[] original) {
  ClassReader cr=new ClassReader(original);ClassWriter cw=new ClassWriter(cr,ClassWriter.COMPUTE_MAXS);int[] changed={0,0};
  ClassVisitor cv=new ClassVisitor(ASM8,cw) {
   @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
    MethodVisitor base=super.visitMethod(access,name,desc,signature,exceptions);
    boolean predicate=name.equals(PREDICATE)&&desc.equals(PREDICATE_DESC);
    if(predicate) { require(access==(ACC_PRIVATE|ACC_STATIC|ACC_FINAL),"Predicate access flags differ");changed[0]++; }
    return new MethodVisitor(ASM8,base) {
     @Override public void visitCode() { super.visitCode();if(predicate)addExactFallback(this.mv); }
     @Override public void visitInvokeDynamicInsn(String indyName,String indyDesc,Handle bootstrap,Object... args) {
      Object[] updated=args.clone();
      for(int i=0;i<updated.length;i++)if(updated[i] instanceof String recipe && recipe.contains(RECIPE_MARKER)) {
       require(name.equals("playerTypeHookPatch$lambda$0$0"),"Recipe in unexpected method");
       require(bootstrap.getOwner().equals("java/lang/invoke/StringConcatFactory") && bootstrap.getName().equals("makeConcatWithConstants"),"Unexpected recipe bootstrap");
       require(recipe.contains("iget-object v0, p1, \u0001") && recipe.contains("->setVideoState(Ljava/lang/Enum;)V"),"Unexpected recipe body");
       require(!recipe.contains(NULL_LABEL),"Recipe already adapted");
       updated[i]="\n                        if-eqz p1, :"+NULL_LABEL+recipe+"                        :"+NULL_LABEL+"\n                        nop\n";changed[1]++;
      }
      super.visitInvokeDynamicInsn(indyName,indyDesc,bootstrap,updated);
     }
    };
   }
  };
  cr.accept(cv,0);require(changed[0]==1 && changed[1]==1,"Expected one predicate and one injection recipe, got "+Arrays.toString(changed));return cw.toByteArray();
 }
 static int writeBundle(Path from,Path to,byte[] cls) throws Exception {
  int count=0;try(ZipFile input=new ZipFile(from.toFile());ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(to,StandardOpenOption.CREATE_NEW))) {
   if(input.getComment()!=null)output.setComment(input.getComment());
   for(ZipEntry e:Collections.list(input.entries())) {
    byte[] data=e.getName().equals(ENTRY)?cls:input.getInputStream(e).readAllBytes();ZipEntry copy=new ZipEntry(e);CRC32 crc=new CRC32();crc.update(data);
    copy.setSize(data.length);copy.setCrc(crc.getValue());copy.setCompressedSize(copy.getMethod()==ZipEntry.STORED?data.length:-1);
    output.putNextEntry(copy);output.write(data);output.closeEntry();count++;
   }
  }
  int differences=0;try(ZipFile a=new ZipFile(from.toFile());ZipFile b=new ZipFile(to.toFile())) {
   require(a.size()==b.size(),"Entry count changed");
   for(ZipEntry e:Collections.list(a.entries())) {
    ZipEntry n=b.getEntry(e.getName());require(n!=null,"Missing entry "+e.getName());
    if(!Arrays.equals(a.getInputStream(e).readAllBytes(),b.getInputStream(n).readAllBytes())) {require(e.getName().equals(ENTRY),"Unexpected entry change "+e.getName());differences++;}
   }
  }
  require(differences==1,"Expected exactly one changed class payload");return count;
 }
 static Map<String,Boolean> evaluatePredicates(Path bundle,InputEvidence evidence) throws Exception {
  try(URLClassLoader loader=new URLClassLoader(new URL[]{bundle.toUri().toURL()},AdaptStateFingerprint.class.getClassLoader())) {
   Class<?> cls=Class.forName(CLASS_NAME,true,loader);java.lang.reflect.Method predicate=cls.getDeclaredMethod(PREDICATE,com.android.tools.smali.dexlib2.iface.Method.class,ClassDef.class);predicate.setAccessible(true);
   Map<String,Boolean> result=new TreeMap<>();
   for(var m:evidence.candidateMethods())result.put(methodId(m),(Boolean)predicate.invoke(null,m,evidence.targetClass()));return result;
  }
 }
 public static void main(String[] args) throws Exception {
  require(args.length==3,"Usage: ORIGINAL_MPP INPUT_APK NEW_MPP");Path from=Path.of(args[0]),apk=Path.of(args[1]),to=Path.of(args[2]);
  require(!Files.exists(to),"Output must be new");require(sha(Files.readAllBytes(from)).equals(ORIGINAL_MPP_SHA),"Original bundle digest differs");
  InputEvidence evidence=inspectApk(apk);byte[] original;
  try(ZipFile z=new ZipFile(from.toFile())) {original=z.getInputStream(z.getEntry(ENTRY)).readAllBytes();}
  require(sha(original).equals(ORIGINAL_CLASS_SHA),"Original class digest differs");
  Map<String,Boolean> before=evaluatePredicates(from,evidence);require(!before.get("Larhj;->a(Larhd;)V"),"Original target predicate already accepts method");
  byte[] adapted=adaptClass(original);int entries=writeBundle(from,to,adapted);
  Map<String,Boolean> after=evaluatePredicates(to,evidence);int changed=0;
  for(var e:before.entrySet())if(!Objects.equals(e.getValue(),after.get(e.getKey()))) {require(e.getKey().equals("Larhj;->a(Larhd;)V") && after.get(e.getKey()),"Unexpected predicate behavior change");changed++;}
  require(changed==1,"Expected exactly one predicate result change");
  String report="Local compiled fingerprint adaptation, Codex, 2026-10-28\n"
   +"Upstream: https://github.com/anddea/revanced-patches/tree/v4.4.0-dev.3 (GPL-3.0); original bundled credits and other entries retained.\n"
   +"Source: patches/src/main/kotlin/app/morphe/patches/youtube/utils/playertype/PlayerTypeHookPatch.kt\n"
   +"Input APK SHA-256: "+evidence.apkSha()+"\nOriginal classes6.dex SHA-256: "+evidence.dexSha()+"\n"
   +"Target: Larhj;->a(Larhd;)V; flags17, registers7, 134 instructions, 283 code units, zero try blocks.\n"
   +"Target raw instructions SHA-256: "+BODY_SHA+"\n"
   +"Input methods scanned: "+evidence.scanned()+"; public-final void(Larhd) candidates: "+evidence.candidates()+"; opcode-pattern matches: "+evidence.patternMatches()+".\n"
   +"Original MPP SHA-256: "+ORIGINAL_MPP_SHA+"\nNew MPP SHA-256: "+sha(Files.readAllBytes(to))+"\n"
   +"Changed entry: "+ENTRY+"\nOriginal class SHA-256: "+ORIGINAL_CLASS_SHA+"\nNew class SHA-256: "+sha(adapted)+"\n"
   +"ZIP entries: "+entries+"; exactly one entry payload changed; every other entry payload compared byte-for-byte.\n"
   +"Adaptation: exact owner/name/prototype/access/frame fallback before original equals predicate; original opcode filters and field extraction retained.\n"
   +"Null guard: exactly one setVideoState StringConcatFactory recipe adds if-eqz p1, a unique label, and nop before original app method.\n"
   +"Predicate check: original target false -> adapted true; all other "+(before.size()-1)+" public-final Larhd candidate results unchanged.\n"
   +"Limit: no APK patch/application execution, signing, installation, network playback, or account action performed by this utility.\n";
  Files.writeString(Path.of(to.toString()+".adaptation.txt"),report,StandardOpenOption.CREATE_NEW);System.out.print(report);
 }
}
