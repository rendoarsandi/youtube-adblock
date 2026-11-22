import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction23x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies v0.1.9 Shorts-disable and full Litho ad-blocking patches on top of v0.1.8:
 * 1. classes2.dex: Lrrg;->P(Z)V
 *    Hides the Shorts bottom-nav PivotBar tab via ShortsBlocker.hideShortsTab(v7, v2)
 *    right after check-cast v2, Landroid/view/View; at the tab creation merge point.
 * 2. classes5.dex: Lkym;->b(Lbdfp;Ljava/util/Map;)V
 *    Early return-void on ReelWatchEndpoint execution so Shorts player never opens.
 * 3. classes9.dex:
 *    - Lapp/morphe/extension/shared/patches/components/LithoFilterPatch;->patch_getFilterArray()
 *      Registers LayoutReloadObserverFilter, AdsFilter, and ShortsShelfFilter.
 *    - Lapp/morphe/extension/youtube/settings/Settings;-><clinit>()V
 *      Defaults HIDE_SHORTS_SHELF, HIDE_SHORTS_SHELF_CHANNEL, and HIDE_SHORTS_SHELF_HISTORY to Boolean.TRUE.
 *    - Replaces Lapp/local/youtube/adblock/DisplayAdPolicy; with the expanded ad + Shorts policy.
 * 4. classes11.dex:
 *    - Adds Lapp/local/youtube/adblock/ShortsBlocker;
 *    - Updates PlaybackDiagnostics build tag v0.1.8 -> v0.1.9.
 */
public final class EnableNoShortsAndAdblock {
    private static final String SHORTS_BLOCKER_TYPE = "Lapp/local/youtube/adblock/ShortsBlocker;";
    private static final String DISPLAY_POLICY_TYPE = "Lapp/local/youtube/adblock/DisplayAdPolicy;";
    private static final String LITHO_FILTER_PATCH_TYPE = "Lapp/morphe/extension/shared/patches/components/LithoFilterPatch;";
    private static final String SETTINGS_TYPE = "Lapp/morphe/extension/youtube/settings/Settings;";
    private static final String DIAGNOSTICS_TYPE = "Lapp/local/youtube/adblock/PlaybackDiagnostics;";

    private static final Set<String> SHORTS_TRUE_KEYS = new HashSet<>(Arrays.asList(
        "revanced_hide_shorts_shelf",
        "revanced_hide_shorts_shelf_channel",
        "revanced_hide_shorts_shelf_history"
    ));

    public static void main(String[] args) throws Exception {
        if (args.length != 9) {
            throw new IllegalArgumentException(
                "Usage: EnableNoShortsAndAdblock <in2> <out2> <in5> <out5> <in9> <out9> <in11> <out11> <helpers.dex>"
            );
        }
        DexFile helpersDex = DexFileFactory.loadDexFile(new File(args[8]), Opcodes.getDefault());
        ClassDef newPolicyClass = null;
        ClassDef shortsBlockerClass = null;
        for (ClassDef c : helpersDex.getClasses()) {
            if (c.getType().equals(DISPLAY_POLICY_TYPE)) newPolicyClass = c;
            else if (c.getType().equals(SHORTS_BLOCKER_TYPE)) shortsBlockerClass = c;
        }
        if (newPolicyClass == null || shortsBlockerClass == null) {
            throw new IllegalStateException("Missing helper classes in " + args[8]);
        }

        patchClasses2(new File(args[0]), new File(args[1]));
        patchClasses5(new File(args[2]), new File(args[3]));
        patchClasses9(new File(args[4]), new File(args[5]), newPolicyClass);
        patchClasses11(new File(args[6]), new File(args[7]), shortsBlockerClass);
        System.out.println("EnableNoShortsAndAdblock applied all v0.1.9 DEX patches.");
    }

    private static void patchClasses2(File inDex, File outDex) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean patched = false;
        for (ClassDef c : dex.getClasses()) {
            if (!c.getType().equals("Lrrg;")) {
                updated.add(c);
                continue;
            }
            List<Method> direct = new ArrayList<>();
            for (Method m : c.getDirectMethods()) direct.add(m);
            List<Method> virtual = new ArrayList<>();
            for (Method m : c.getVirtualMethods()) {
                if (m.getName().equals("P")
                    && m.getParameterTypes().size() == 1
                    && m.getParameterTypes().get(0).toString().equals("Z")
                    && m.getReturnType().equals("V")) {
                    virtual.add(patchPivotBarTabCreation(m));
                    patched = true;
                } else {
                    virtual.add(m);
                }
            }
            updated.add(new ImmutableClassDef(
                c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(),
                direct, virtual
            ));
        }
        if (!patched) throw new IllegalStateException("Did not find Lrrg;->P(Z)V in classes2.dex");
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static Method patchPivotBarTabCreation(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) throw new IllegalStateException("Lrrg;->P(Z)V has no body");
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        int targetIdx = -1;
        for (int i = 0; i < insns.size() - 1; i++) {
            Instruction cur = insns.get(i);
            Instruction next = insns.get(i + 1);
            if (cur.getOpcode() == Opcode.CHECK_CAST
                && cur instanceof OneRegisterInstruction
                && cur instanceof ReferenceInstruction
                && ((OneRegisterInstruction) cur).getRegisterA() == 2
                && "Landroid/view/View;".equals(((ReferenceInstruction) cur).getReference().toString())
                && next.getOpcode() == Opcode.IGET_OBJECT
                && next instanceof ReferenceInstruction
                && "Laupy;->h:Lj$/util/Optional;".equals(((ReferenceInstruction) next).getReference().toString())) {
                targetIdx = i + 1;
                break;
            }
        }
        if (targetIdx < 0) {
            throw new IllegalStateException("Could not find check-cast v2, View; iget-object v3, v7, Laupy;->h in Lrrg;->P(Z)V");
        }
        mutable.addInstruction(
            targetIdx,
            new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                2,
                7, // v7 = Laupy
                2, // v2 = android.view.View
                0, 0, 0,
                new ImmutableMethodReference(
                    SHORTS_BLOCKER_TYPE,
                    "hideShortsTab",
                    Arrays.asList("Ljava/lang/Object;", "Ljava/lang/Object;"),
                    "V"
                )
            )
        );
        return new ImmutableMethod(
            m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
            m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), mutable
        );
    }

    private static void patchClasses5(File inDex, File outDex) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean patched = false;
        for (ClassDef c : dex.getClasses()) {
            if (!c.getType().equals("Lkym;")) {
                updated.add(c);
                continue;
            }
            List<Method> direct = new ArrayList<>();
            for (Method m : c.getDirectMethods()) direct.add(m);
            List<Method> virtual = new ArrayList<>();
            for (Method m : c.getVirtualMethods()) {
                if (m.getName().equals("b")
                    && m.getParameterTypes().size() == 2
                    && m.getParameterTypes().get(0).toString().equals("Lbdfp;")
                    && m.getParameterTypes().get(1).toString().equals("Ljava/util/Map;")
                    && m.getReturnType().equals("V")) {
                    MutableMethodImplementation empty = new MutableMethodImplementation(3);
                    empty.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
                    virtual.add(new ImmutableMethod(
                        m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
                        m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), empty
                    ));
                    patched = true;
                } else {
                    virtual.add(m);
                }
            }
            updated.add(new ImmutableClassDef(
                c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(),
                direct, virtual
            ));
        }
        if (!patched) throw new IllegalStateException("Did not find Lkym;->b(Lbdfp;Ljava/util/Map;)V in classes5.dex");
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static void patchClasses9(File inDex, File outDex, ClassDef newPolicyClass) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean patchedLitho = false;
        boolean patchedSettings = false;
        boolean replacedPolicy = false;

        for (ClassDef c : dex.getClasses()) {
            if (c.getType().equals(DISPLAY_POLICY_TYPE)) {
                updated.add(newPolicyClass);
                replacedPolicy = true;
            } else if (c.getType().equals(LITHO_FILTER_PATCH_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) {
                    if (m.getName().equals("patch_getFilterArray")
                        && m.getParameterTypes().isEmpty()
                        && m.getReturnType().equals("[Lapp/morphe/extension/shared/patches/components/Filter;")) {
                        direct.add(buildFilterArrayMethod(m));
                        patchedLitho = true;
                    } else {
                        direct.add(m);
                    }
                }
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) virtual.add(m);
                updated.add(new ImmutableClassDef(
                    c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                    c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(),
                    direct, virtual
                ));
            } else if (c.getType().equals(SETTINGS_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) {
                    if (m.getName().equals("<clinit>")) {
                        direct.add(patchShortsSettingsDefaults(m));
                        patchedSettings = true;
                    } else {
                        direct.add(m);
                    }
                }
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) virtual.add(m);
                updated.add(new ImmutableClassDef(
                    c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                    c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(),
                    direct, virtual
                ));
            } else {
                updated.add(c);
            }
        }
        if (!patchedLitho) throw new IllegalStateException("Did not find LithoFilterPatch.patch_getFilterArray in classes9.dex");
        if (!patchedSettings) throw new IllegalStateException("Did not find Settings.<clinit> in classes9.dex");
        if (!replacedPolicy) throw new IllegalStateException("Did not find DisplayAdPolicy in classes9.dex");
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static Method buildFilterArrayMethod(Method m) {
        MutableMethodImplementation impl = new MutableMethodImplementation(3);
        impl.addInstruction(new BuilderInstruction21s(Opcode.CONST_16, 1, 3));
        impl.addInstruction(new BuilderInstruction22c(
            Opcode.NEW_ARRAY, 2, 1,
            new ImmutableTypeReference("[Lapp/morphe/extension/shared/patches/components/Filter;")
        ));

        String[] filterTypes = {
            "Lapp/morphe/extension/youtube/patches/components/LayoutReloadObserverFilter;",
            "Lapp/morphe/extension/youtube/patches/components/AdsFilter;",
            "Lapp/morphe/extension/youtube/patches/components/ShortsShelfFilter;"
        };
        for (int i = 0; i < filterTypes.length; i++) {
            impl.addInstruction(new BuilderInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference(filterTypes[i])));
            impl.addInstruction(new BuilderInstruction35c(
                Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(filterTypes[i], "<init>", Collections.emptyList(), "V")
            ));
            impl.addInstruction(new BuilderInstruction21s(Opcode.CONST_16, 1, i));
            impl.addInstruction(new BuilderInstruction23x(Opcode.APUT_OBJECT, 0, 2, 1));
        }
        impl.addInstruction(new BuilderInstruction11x(Opcode.RETURN_OBJECT, 2));
        return new ImmutableMethod(
            m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
            m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), impl
        );
    }

    private static Method patchShortsSettingsDefaults(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) throw new IllegalStateException("Settings.<clinit> has no body");
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        int flipped = 0;
        for (int i = 0; i < insns.size() - 3; i++) {
            Instruction cur = insns.get(i);
            if ((cur.getOpcode() != Opcode.CONST_STRING && cur.getOpcode() != Opcode.CONST_STRING_JUMBO)
                || !(cur instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) cur).getReference();
            if (!(ref instanceof StringReference)) continue;
            String key = ((StringReference) ref).getString();
            if (!SHORTS_TRUE_KEYS.contains(key)) continue;

            // Find the BooleanSetting.<init> call within the next 6 instructions
            boolean found = false;
            for (int j = i + 1; j <= Math.min(i + 6, insns.size() - 1); j++) {
                Instruction cand = insns.get(j);
                if (cand.getOpcode() == Opcode.INVOKE_DIRECT
                    && cand instanceof FiveRegisterInstruction
                    && cand instanceof ReferenceInstruction) {
                    String mref = ((ReferenceInstruction) cand).getReference().toString();
                    if (mref.startsWith("Lapp/morphe/extension/shared/settings/BooleanSetting;-><init>(Ljava/lang/String;Ljava/lang/Boolean;")) {
                        FiveRegisterInstruction call = (FiveRegisterInstruction) cand;
                        // In Settings.<clinit> around line 7140, v12 holds Boolean.TRUE and v1 holds Boolean.FALSE.
                        if (call.getRegisterE() != 1) {
                            throw new IllegalStateException("Unexpected default boolean register for " + key + ": v" + call.getRegisterE());
                        }
                        mutable.replaceInstruction(
                            j,
                            new BuilderInstruction35c(
                                Opcode.INVOKE_DIRECT,
                                call.getRegisterCount(),
                                call.getRegisterC(),
                                call.getRegisterD(),
                                12, // v12 = Boolean.TRUE
                                call.getRegisterF(),
                                call.getRegisterG(),
                                ((ReferenceInstruction) cand).getReference()
                            )
                        );
                        flipped++;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                throw new IllegalStateException("Could not find BooleanSetting.<init> for " + key);
            }
        }
        if (flipped != SHORTS_TRUE_KEYS.size()) {
            throw new IllegalStateException("Expected to flip " + SHORTS_TRUE_KEYS.size() + " Shorts settings, flipped " + flipped);
        }
        return new ImmutableMethod(
            m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
            m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), mutable
        );
    }

    private static void patchClasses11(File inDex, File outDex, ClassDef shortsBlockerClass) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean updatedDiag = false;
        for (ClassDef c : dex.getClasses()) {
            if (c.getType().equals(SHORTS_BLOCKER_TYPE)) {
                continue;
            }
            if (c.getType().equals(DIAGNOSTICS_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) direct.add(rewriteVersionTag(m));
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) virtual.add(rewriteVersionTag(m));
                updated.add(new ImmutableClassDef(
                    c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                    c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(),
                    direct, virtual
                ));
                updatedDiag = true;
            } else {
                updated.add(c);
            }
        }
        if (!updatedDiag) throw new IllegalStateException("Did not find PlaybackDiagnostics in classes11.dex");
        updated.add(shortsBlockerClass);
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static Method rewriteVersionTag(Method method) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return method;
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        boolean changed = false;
        for (int i = 0; i < insns.size(); i++) {
            Instruction ins = insns.get(i);
            if ((ins.getOpcode() != Opcode.CONST_STRING && ins.getOpcode() != Opcode.CONST_STRING_JUMBO)
                || !(ins instanceof OneRegisterInstruction)
                || !(ins instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) ins).getReference();
            if (!(ref instanceof StringReference)) continue;
            String val = ((StringReference) ref).getString();
            if (!val.contains("v0.1.8")) continue;
            mutable.replaceInstruction(
                i,
                new BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    ((OneRegisterInstruction) ins).getRegisterA(),
                    new ImmutableStringReference(val.replace("v0.1.8", "v0.1.9"))
                )
            );
            changed = true;
        }
        if (!changed) return method;
        return new ImmutableMethod(
            method.getDefiningClass(), method.getName(), method.getParameters(), method.getReturnType(),
            method.getAccessFlags(), method.getAnnotations(), method.getHiddenApiRestrictions(), mutable
        );
    }
}
