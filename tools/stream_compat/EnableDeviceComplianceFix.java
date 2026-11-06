import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Applies the missing Morphe GmsCore compliance and signature hooks to YouTube 21.40.161:
 * 1. classes2.dex: Lita;->a(Lbuzj;)V (DeviceComplianceCheckPatchKt / GmsDeviceComplianceCheckFingerprint)
 *    Unconditionally emits Boolean.FALSE to Lbuzj;->d(Ljava/lang/Object;)V and returns void so the
 *    "This device isn't Play Protect certified" screen (UncertifiedDeviceActivity) is never triggered.
 * 2. classes2.dex: Ltfr;->a(Ljava/io/File;)Z (DroidGuardSignatureFingerprint)
 *    Returns true early so DroidGuard APK X509 certificate verification succeeds on non-root builds.
 * 3. classes3.dex: Lyvx;->a(Lyvo;)Lyqk; (GnpRegistrationTargetFingerprint)
 *    Spoofs Context.getPackageName() result to "com.google.android.youtube" for GNP registration.
 * 4. classes11.dex: PatchStatus (GmsCoreSupport=true, PackageNameYouTube="app.revanced.android.youtube")
 *    and DiagnosticsLogger (BUILD_TAG="v0.1.8", STREAM_LOG_PREFIX="YouTube-v0.1.8-playback-").
 */
public final class EnableDeviceComplianceFix {
    private static final String PATCH_STATUS_CLASS = "Lapp/morphe/extension/shared/patches/PatchStatus;";
    private static final String DIAGNOSTICS_CLASS = "Lapp/local/youtube/adblock/PlaybackDiagnostics;";

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                "Usage: EnableDeviceComplianceFix <in-classes2.dex> <out-classes2.dex> "
                    + "<in-classes3.dex> <out-classes3.dex> <in-classes11.dex> <out-classes11.dex>"
            );
        }
        patchClasses2(new File(args[0]), new File(args[1]));
        patchClasses3(new File(args[2]), new File(args[3]));
        patchClasses11(new File(args[4]), new File(args[5]));
    }

    private static void patchClasses2(File inputDex, File outputDex) throws Exception {
        DexFile dexFile = DexFileFactory.loadDexFile(inputDex, Opcodes.getDefault());
        List<ClassDef> updatedClasses = new ArrayList<>();
        boolean patchedCompliance = false;
        boolean patchedDroidGuardSig = false;

        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDef.getType().equals("Lita;")) {
                List<Method> directMethods = new ArrayList<>();
                for (Method m : classDef.getDirectMethods()) {
                    directMethods.add(m);
                }
                List<Method> virtualMethods = new ArrayList<>();
                for (Method m : classDef.getVirtualMethods()) {
                    if (m.getName().equals("a")
                        && m.getParameterTypes().size() == 1
                        && m.getParameterTypes().get(0).toString().equals("Lbuzj;")
                        && m.getReturnType().equals("V")) {
                        virtualMethods.add(buildDeviceComplianceBypassMethod(m));
                        patchedCompliance = true;
                    } else {
                        virtualMethods.add(m);
                    }
                }
                updatedClasses.add(new ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getStaticFields(),
                    classDef.getInstanceFields(),
                    directMethods,
                    virtualMethods
                ));
            } else if (classDef.getType().equals("Ltfr;")) {
                List<Method> directMethods = new ArrayList<>();
                for (Method m : classDef.getDirectMethods()) {
                    directMethods.add(m);
                }
                List<Method> virtualMethods = new ArrayList<>();
                for (Method m : classDef.getVirtualMethods()) {
                    if (m.getName().equals("a")
                        && m.getParameterTypes().size() == 1
                        && m.getParameterTypes().get(0).toString().equals("Ljava/io/File;")
                        && m.getReturnType().equals("Z")) {
                        virtualMethods.add(buildReturnTrueVirtualMethod(m));
                        patchedDroidGuardSig = true;
                    } else {
                        virtualMethods.add(m);
                    }
                }
                updatedClasses.add(new ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getStaticFields(),
                    classDef.getInstanceFields(),
                    directMethods,
                    virtualMethods
                ));
            } else {
                updatedClasses.add(classDef);
            }
        }

        if (!patchedCompliance) {
            throw new IllegalStateException("Did not find Lita;->a(Lbuzj;)V in classes2.dex");
        }
        if (!patchedDroidGuardSig) {
            throw new IllegalStateException("Did not find Ltfr;->a(Ljava/io/File;)Z in classes2.dex");
        }

        DexFileFactory.writeDexFile(
            outputDex.getAbsolutePath(),
            new ImmutableDexFile(dexFile.getOpcodes(), updatedClasses)
        );
    }

    private static void patchClasses3(File inputDex, File outputDex) throws Exception {
        DexFile dexFile = DexFileFactory.loadDexFile(inputDex, Opcodes.getDefault());
        List<ClassDef> updatedClasses = new ArrayList<>();
        boolean patchedGnp = false;

        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDef.getType().equals("Lyvx;")) {
                List<Method> directMethods = new ArrayList<>();
                for (Method m : classDef.getDirectMethods()) {
                    directMethods.add(m);
                }
                List<Method> virtualMethods = new ArrayList<>();
                for (Method m : classDef.getVirtualMethods()) {
                    if (m.getName().equals("a")
                        && m.getParameterTypes().size() == 1
                        && m.getParameterTypes().get(0).toString().equals("Lyvo;")
                        && m.getReturnType().equals("Lyqk;")) {
                        virtualMethods.add(patchGnpRegistrationMethod(m));
                        patchedGnp = true;
                    } else {
                        virtualMethods.add(m);
                    }
                }
                updatedClasses.add(new ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getStaticFields(),
                    classDef.getInstanceFields(),
                    directMethods,
                    virtualMethods
                ));
            } else {
                updatedClasses.add(classDef);
            }
        }

        if (!patchedGnp) {
            throw new IllegalStateException("Did not find Lyvx;->a(Lyvo;)Lyqk; in classes3.dex");
        }

        DexFileFactory.writeDexFile(
            outputDex.getAbsolutePath(),
            new ImmutableDexFile(dexFile.getOpcodes(), updatedClasses)
        );
    }

    private static void patchClasses11(File inputDex, File outputDex) throws Exception {
        DexFile dexFile = DexFileFactory.loadDexFile(inputDex, Opcodes.getDefault());
        List<ClassDef> updatedClasses = new ArrayList<>();
        boolean patchedGmsStatus = false;
        boolean patchedPkgName = false;
        boolean updatedDiagnostics = false;

        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDef.getType().equals(PATCH_STATUS_CLASS)) {
                List<Method> directMethods = new ArrayList<>();
                for (Method m : classDef.getDirectMethods()) {
                    if (m.getName().equals("GmsCoreSupport")
                        && m.getParameterTypes().isEmpty()
                        && m.getReturnType().equals("Z")) {
                        directMethods.add(buildReturnTrueStaticMethod(m));
                        patchedGmsStatus = true;
                    } else if (m.getName().equals("PackageNameYouTube")
                        && m.getParameterTypes().isEmpty()
                        && m.getReturnType().equals("Ljava/lang/String;")) {
                        directMethods.add(buildReturnStringStaticMethod(m, "app.revanced.android.youtube"));
                        patchedPkgName = true;
                    } else {
                        directMethods.add(m);
                    }
                }
                List<Method> virtualMethods = new ArrayList<>();
                for (Method m : classDef.getVirtualMethods()) {
                    virtualMethods.add(m);
                }
                updatedClasses.add(new ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getStaticFields(),
                    classDef.getInstanceFields(),
                    directMethods,
                    virtualMethods
                ));
            } else if (classDef.getType().equals(DIAGNOSTICS_CLASS)) {
                List<Method> directMethods = new ArrayList<>();
                for (Method m : classDef.getDirectMethods()) {
                    directMethods.add(rewriteDiagnosticsStrings(m));
                }
                List<Method> virtualMethods = new ArrayList<>();
                for (Method m : classDef.getVirtualMethods()) {
                    virtualMethods.add(rewriteDiagnosticsStrings(m));
                }
                updatedClasses.add(new ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getStaticFields(),
                    classDef.getInstanceFields(),
                    directMethods,
                    virtualMethods
                ));
                updatedDiagnostics = true;
            } else {
                updatedClasses.add(classDef);
            }
        }

        if (!patchedGmsStatus || !patchedPkgName) {
            throw new IllegalStateException("Did not patch PatchStatus in classes11.dex");
        }
        if (!updatedDiagnostics) {
            throw new IllegalStateException("Did not find DiagnosticsLogger in classes11.dex");
        }

        DexFileFactory.writeDexFile(
            outputDex.getAbsolutePath(),
            new ImmutableDexFile(dexFile.getOpcodes(), updatedClasses)
        );
    }

    private static Method buildDeviceComplianceBypassMethod(Method method) {
        // Lita;->a(Lbuzj;)V is an instance method: with 2 registers, v0 = p0 (this), v1 = p1 (Lbuzj).
        MutableMethodImplementation impl = new MutableMethodImplementation(2);
        impl.addInstruction(new BuilderInstruction21c(
            Opcode.SGET_OBJECT,
            0,
            new ImmutableFieldReference("Ljava/lang/Boolean;", "FALSE", "Ljava/lang/Boolean;")
        ));
        impl.addInstruction(new BuilderInstruction35c(
            Opcode.INVOKE_VIRTUAL,
            2,
            1,
            0,
            0,
            0,
            0,
            new ImmutableMethodReference(
                "Lbuzj;",
                "d",
                Collections.singletonList("Ljava/lang/Object;"),
                "V"
            )
        ));
        impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            impl
        );
    }

    private static Method buildReturnTrueVirtualMethod(Method method) {
        MutableMethodImplementation impl = new MutableMethodImplementation(2);
        impl.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, 0, 1));
        impl.addInstruction(new BuilderInstruction11x(Opcode.RETURN, 0));
        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            impl
        );
    }

    private static Method patchGnpRegistrationMethod(Method method) {
        MethodImplementation implementation = method.getImplementation();
        if (implementation == null) {
            throw new IllegalStateException("Lyvx;->a(Lyvo;)Lyqk; has no implementation");
        }
        MutableMethodImplementation mutable = new MutableMethodImplementation(implementation);
        List<? extends Instruction> instructions = mutable.getInstructions();
        int matchIdx = -1;
        int targetReg = -1;
        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction ins = instructions.get(i);
            Instruction next = instructions.get(i + 1);
            if (ins.getOpcode() == Opcode.INVOKE_VIRTUAL && ins instanceof ReferenceInstruction) {
                String ref = ((ReferenceInstruction) ins).getReference().toString();
                if (ref.equals("Landroid/content/Context;->getPackageName()Ljava/lang/String;")
                    && (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT || next.getOpcode() == Opcode.CONST_STRING)
                    && next instanceof OneRegisterInstruction) {
                    matchIdx = i + 1;
                    targetReg = ((OneRegisterInstruction) next).getRegisterA();
                    break;
                }
            }
        }
        if (matchIdx < 0 || targetReg < 0) {
            throw new IllegalStateException("Did not find Context.getPackageName() in Lyvx;->a(Lyvo;)Lyqk;");
        }
        mutable.replaceInstruction(
            matchIdx,
            new BuilderInstruction21c(
                Opcode.CONST_STRING,
                targetReg,
                new ImmutableStringReference("com.google.android.youtube")
            )
        );
        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            mutable
        );
    }

    private static Method buildReturnTrueStaticMethod(Method method) {
        MutableMethodImplementation impl = new MutableMethodImplementation(1);
        impl.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, 0, 1));
        impl.addInstruction(new BuilderInstruction11x(Opcode.RETURN, 0));
        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            impl
        );
    }

    private static Method buildReturnStringStaticMethod(Method method, String value) {
        MutableMethodImplementation impl = new MutableMethodImplementation(1);
        impl.addInstruction(new BuilderInstruction21c(
            Opcode.CONST_STRING,
            0,
            new ImmutableStringReference(value)
        ));
        impl.addInstruction(new BuilderInstruction11x(Opcode.RETURN_OBJECT, 0));
        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            impl
        );
    }

    private static Method rewriteDiagnosticsStrings(Method method) {
        MethodImplementation implementation = method.getImplementation();
        if (implementation == null) {
            return method;
        }
        MutableMethodImplementation mutable = new MutableMethodImplementation(implementation);
        List<? extends Instruction> instructions = mutable.getInstructions();
        boolean changed = false;
        for (int index = 0; index < instructions.size(); index++) {
            Instruction instruction = instructions.get(index);
            if ((instruction.getOpcode() != Opcode.CONST_STRING && instruction.getOpcode() != Opcode.CONST_STRING_JUMBO)
                || !(instruction instanceof OneRegisterInstruction)
                || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (!(reference instanceof StringReference)) {
                continue;
            }
            String value = ((StringReference) reference).getString();
            String updated = null;
            if (value.contains("v0.1.7")) {
                updated = value.replace("v0.1.7", "v0.1.8");
            }
            if (updated == null) {
                continue;
            }
            int register = ((OneRegisterInstruction) instruction).getRegisterA();
            mutable.replaceInstruction(
                index,
                new BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    register,
                    new ImmutableStringReference(updated)
                )
            );
            changed = true;
        }
        if (!changed) {
            return method;
        }
        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            mutable
        );
    }
}
