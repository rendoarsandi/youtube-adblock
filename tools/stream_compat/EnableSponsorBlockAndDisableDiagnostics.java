import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Applies v0.1.10 SponsorBlock + disable-diagnostics patches on top of v0.1.9:
 * 1. classes2.dex:
 *    - YouTubePlayerOverlaysLayout;->onFinishInflate()V:
 *      calls SponsorBlockViewController.initialize(this) to attach the floating Skip pill overlay.
 *    - Ljsi;->onDraw(Landroid/graphics/Canvas;)V:
 *      draws SponsorBlock colored segment bars right before the seekbar scrubber thumb.
 * 2. classes9.dex:
 *    - Replaces LocalDiagnostics with no-op (removes LocalDiagnostics$* inner classes).
 *    - Replaces SkipSponsorButton with programmatic view (removes old synthetic lambda).
 *    - Routes SponsorBlockViewController.initialize(ViewGroup) to SponsorBlockOverlayHelper.initialize(ViewGroup).
 *    - Wires VideoInformation.{initialize,setVideoId,setVideoInformation,setVideoTime} to SponsorBlockBridge.
 *    - Flips Settings.SB_TOAST_ON_CONNECTION_ERROR default to Boolean.FALSE.
 * 3. classes11.dex:
 *    - Replaces PlaybackDiagnostics with no-op (removes PlaybackDiagnostics$* inner classes).
 *    - Adds SponsorBlockBridge (+ inner classes) and SponsorBlockOverlayHelper (+ inner classes).
 */
public final class EnableSponsorBlockAndDisableDiagnostics {
    private static final String OVERLAYS_LAYOUT_TYPE =
        "Lcom/google/android/apps/youtube/app/common/player/overlay/YouTubePlayerOverlaysLayout;";
    private static final String TIMEBAR_TYPE = "Ljsi;";
    private static final String LOCAL_DIAG_TYPE = "Lapp/local/youtube/adblock/LocalDiagnostics;";
    private static final String LOCAL_DIAG_PREFIX = "Lapp/local/youtube/adblock/LocalDiagnostics$";
    private static final String PLAYBACK_DIAG_TYPE = "Lapp/local/youtube/adblock/PlaybackDiagnostics;";
    private static final String PLAYBACK_DIAG_PREFIX = "Lapp/local/youtube/adblock/PlaybackDiagnostics$";
    private static final String SKIP_BTN_TYPE = "Lapp/morphe/extension/youtube/sponsorblock/ui/SkipSponsorButton;";
    private static final String SKIP_BTN_PREFIX = "Lapp/morphe/extension/youtube/sponsorblock/ui/SkipSponsorButton$";
    private static final String SB_VIEW_CTRL_TYPE = "Lapp/morphe/extension/youtube/sponsorblock/ui/SponsorBlockViewController;";
    private static final String VIDEO_INFO_TYPE = "Lapp/morphe/extension/youtube/shared/VideoInformation;";
    private static final String SETTINGS_TYPE = "Lapp/morphe/extension/youtube/settings/Settings;";
    private static final String SB_BRIDGE_TYPE = "Lapp/local/youtube/adblock/SponsorBlockBridge;";
    private static final String SB_BRIDGE_PREFIX = "Lapp/local/youtube/adblock/SponsorBlockBridge$";
    private static final String SB_OVERLAY_HELPER_TYPE = "Lapp/local/youtube/adblock/SponsorBlockOverlayHelper;";
    private static final String SB_OVERLAY_HELPER_PREFIX = "Lapp/local/youtube/adblock/SponsorBlockOverlayHelper$";

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException(
                "Usage: EnableSponsorBlockAndDisableDiagnostics <in2> <out2> <in9> <out9> <in11> <out11> <helpers.dex>"
            );
        }
        DexFile helpersDex = DexFileFactory.loadDexFile(new File(args[6]), Opcodes.getDefault());
        ClassDef newLocalDiag = null;
        ClassDef newPlaybackDiag = null;
        List<ClassDef> skipBtnClasses = new ArrayList<>();
        List<ClassDef> bridgeAndOverlayClasses = new ArrayList<>();

        for (ClassDef c : helpersDex.getClasses()) {
            String t = c.getType();
            if (t.equals(LOCAL_DIAG_TYPE)) {
                newLocalDiag = c;
            } else if (t.equals(PLAYBACK_DIAG_TYPE)) {
                newPlaybackDiag = c;
            } else if (t.equals(SKIP_BTN_TYPE) || t.startsWith(SKIP_BTN_PREFIX)) {
                skipBtnClasses.add(c);
            } else if (t.equals(SB_BRIDGE_TYPE) || t.startsWith(SB_BRIDGE_PREFIX)
                || t.equals(SB_OVERLAY_HELPER_TYPE) || t.startsWith(SB_OVERLAY_HELPER_PREFIX)) {
                bridgeAndOverlayClasses.add(c);
            }
        }
        if (newLocalDiag == null || newPlaybackDiag == null || skipBtnClasses.isEmpty() || bridgeAndOverlayClasses.isEmpty()) {
            throw new IllegalStateException("Missing helper classes in " + args[6]);
        }

        patchClasses2(new File(args[0]), new File(args[1]));
        patchClasses9(new File(args[2]), new File(args[3]), newLocalDiag, skipBtnClasses);
        patchClasses11(new File(args[4]), new File(args[5]), newPlaybackDiag, bridgeAndOverlayClasses);
        System.out.println("EnableSponsorBlockAndDisableDiagnostics applied all v0.1.10 DEX patches.");
    }

    private static void patchClasses2(File inDex, File outDex) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean patchedOverlays = false;
        boolean patchedTimeBar = false;

        for (ClassDef c : dex.getClasses()) {
            if (c.getType().equals(OVERLAYS_LAYOUT_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) direct.add(m);
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) {
                    if (m.getName().equals("onFinishInflate")
                        && m.getParameterTypes().isEmpty()
                        && m.getReturnType().equals("V")) {
                        virtual.add(patchOverlaysOnFinishInflate(m));
                        patchedOverlays = true;
                    } else {
                        virtual.add(m);
                    }
                }
                updated.add(cloneClass(c, direct, virtual));
            } else if (c.getType().equals(TIMEBAR_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) direct.add(m);
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) {
                    if (m.getName().equals("onDraw")
                        && m.getParameterTypes().size() == 1
                        && m.getParameterTypes().get(0).toString().equals("Landroid/graphics/Canvas;")
                        && m.getReturnType().equals("V")) {
                        virtual.add(patchTimeBarOnDraw(m));
                        patchedTimeBar = true;
                    } else {
                        virtual.add(m);
                    }
                }
                updated.add(cloneClass(c, direct, virtual));
            } else {
                updated.add(c);
            }
        }
        if (!patchedOverlays) throw new IllegalStateException("Did not find YouTubePlayerOverlaysLayout.onFinishInflate in classes2.dex");
        if (!patchedTimeBar) throw new IllegalStateException("Did not find Ljsi.onDraw(Canvas) in classes2.dex");
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static Method patchOverlaysOnFinishInflate(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null || impl.getRegisterCount() != 2) {
            throw new IllegalStateException("Unexpected YouTubePlayerOverlaysLayout.onFinishInflate register count");
        }
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        int retIdx = -1;
        for (int i = insns.size() - 1; i >= 0; i--) {
            if (insns.get(i).getOpcode() == Opcode.RETURN_VOID) {
                retIdx = i;
                break;
            }
        }
        if (retIdx < 0) throw new IllegalStateException("No return-void in onFinishInflate");
        mutable.addInstruction(
            retIdx,
            new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                1,
                1, // p0 = this (ViewGroup)
                0, 0, 0, 0,
                new ImmutableMethodReference(
                    SB_VIEW_CTRL_TYPE,
                    "initialize",
                    Collections.singletonList("Landroid/view/ViewGroup;"),
                    "V"
                )
            )
        );
        return cloneMethod(m, mutable);
    }

    private static Method patchTimeBarOnDraw(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) throw new IllegalStateException("Ljsi.onDraw has no body");
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();

        int matchIdx = -1;
        int matches = 0;
        Reference origFieldRef = null;
        for (int i = 0; i < insns.size() - 4; i++) {
            Instruction a = insns.get(i);
            Instruction b = insns.get(i + 1);
            Instruction c = insns.get(i + 2);
            Instruction d = insns.get(i + 3);
            Instruction e = insns.get(i + 4);
            if (a.getOpcode() == Opcode.IGET_BOOLEAN
                && a instanceof TwoRegisterInstruction
                && a instanceof ReferenceInstruction
                && ((TwoRegisterInstruction) a).getRegisterA() == 2
                && ((TwoRegisterInstruction) a).getRegisterB() == 0
                && "Ljsi;->E:Z".equals(((ReferenceInstruction) a).getReference().toString())
                && b.getOpcode() == Opcode.IF_NEZ
                && c.getOpcode() == Opcode.IGET_BOOLEAN
                && c instanceof TwoRegisterInstruction
                && c instanceof ReferenceInstruction
                && ((TwoRegisterInstruction) c).getRegisterA() == 2
                && ((TwoRegisterInstruction) c).getRegisterB() == 0
                && "Ljsi;->F:Z".equals(((ReferenceInstruction) c).getReference().toString())
                && d.getOpcode() == Opcode.IF_EQZ
                && e.getOpcode() == Opcode.IGET_OBJECT
                && e instanceof ReferenceInstruction
                && "Ljsi;->ao:Layqm;".equals(((ReferenceInstruction) e).getReference().toString())) {
                matchIdx = i;
                origFieldRef = ((ReferenceInstruction) a).getReference();
                matches++;
            }
        }
        if (matches != 1 || matchIdx < 0) {
            throw new IllegalStateException("Expected unique pre-thumb anchor in Ljsi.onDraw, found " + matches);
        }
        // Replace matchIdx so incoming branch labels (:cond_755) stay attached to our hook instruction,
        // then re-insert the original iget-boolean right after it.
        mutable.replaceInstruction(
            matchIdx,
            new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                2,
                0, // v0 = this (Ljsi)
                1, // v1 = Canvas
                0, 0, 0,
                new ImmutableMethodReference(
                    SB_BRIDGE_TYPE,
                    "drawSeekbarSegments",
                    Arrays.asList("Ljava/lang/Object;", "Landroid/graphics/Canvas;"),
                    "V"
                )
            )
        );
        mutable.addInstruction(
            matchIdx + 1,
            new BuilderInstruction22c(Opcode.IGET_BOOLEAN, 2, 0, origFieldRef)
        );
        return cloneMethod(m, mutable);
    }

    private static void patchClasses9(
        File inDex,
        File outDex,
        ClassDef newLocalDiag,
        List<ClassDef> skipBtnClasses
    ) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean replacedLocalDiag = false;
        boolean replacedSkipBtn = false;
        boolean patchedViewCtrl = false;
        boolean patchedVideoInfo = false;
        boolean patchedSettings = false;

        for (ClassDef c : dex.getClasses()) {
            String t = c.getType();
            if (t.equals(LOCAL_DIAG_TYPE)) {
                updated.add(newLocalDiag);
                replacedLocalDiag = true;
            } else if (t.startsWith(LOCAL_DIAG_PREFIX)) {
                // Drop old LocalDiagnostics synthetic lambdas
                continue;
            } else if (t.equals(SKIP_BTN_TYPE)) {
                updated.addAll(skipBtnClasses);
                replacedSkipBtn = true;
            } else if (t.startsWith(SKIP_BTN_PREFIX)) {
                // Drop old SkipSponsorButton synthetic lambda
                continue;
            } else if (t.equals(SB_VIEW_CTRL_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) {
                    if (m.getName().equals("initialize")
                        && m.getParameterTypes().size() == 1
                        && m.getParameterTypes().get(0).toString().equals("Landroid/view/ViewGroup;")
                        && m.getReturnType().equals("V")) {
                        direct.add(buildViewCtrlInitialize(m));
                        patchedViewCtrl = true;
                    } else {
                        direct.add(m);
                    }
                }
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) virtual.add(m);
                updated.add(cloneClass(c, direct, virtual));
            } else if (t.equals(VIDEO_INFO_TYPE)) {
                updated.add(patchVideoInformation(c));
                patchedVideoInfo = true;
            } else if (t.equals(SETTINGS_TYPE)) {
                List<Method> direct = new ArrayList<>();
                for (Method m : c.getDirectMethods()) {
                    if (m.getName().equals("<clinit>")) {
                        direct.add(patchSponsorBlockSettingsDefaults(m));
                        patchedSettings = true;
                    } else {
                        direct.add(m);
                    }
                }
                List<Method> virtual = new ArrayList<>();
                for (Method m : c.getVirtualMethods()) virtual.add(m);
                updated.add(cloneClass(c, direct, virtual));
            } else {
                updated.add(c);
            }
        }
        if (!replacedLocalDiag) throw new IllegalStateException("Did not find LocalDiagnostics in classes9.dex");
        if (!replacedSkipBtn) throw new IllegalStateException("Did not find SkipSponsorButton in classes9.dex");
        if (!patchedViewCtrl) throw new IllegalStateException("Did not find SponsorBlockViewController.initialize in classes9.dex");
        if (!patchedVideoInfo) throw new IllegalStateException("Did not find VideoInformation in classes9.dex");
        if (!patchedSettings) throw new IllegalStateException("Did not find Settings.<clinit> in classes9.dex");
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static Method buildViewCtrlInitialize(Method m) {
        MutableMethodImplementation impl = new MutableMethodImplementation(1);
        impl.addInstruction(new BuilderInstruction35c(
            Opcode.INVOKE_STATIC,
            1,
            0, // p0 = ViewGroup
            0, 0, 0, 0,
            new ImmutableMethodReference(
                SB_OVERLAY_HELPER_TYPE,
                "initialize",
                Collections.singletonList("Landroid/view/ViewGroup;"),
                "V"
            )
        ));
        impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        return cloneMethod(m, impl);
    }

    private static ClassDef patchVideoInformation(ClassDef c) {
        boolean hookedInit = false;
        boolean hookedVideoId = false;
        boolean hookedVideoInfo = false;
        boolean hookedVideoTime = false;

        List<Method> direct = new ArrayList<>();
        for (Method m : c.getDirectMethods()) {
            String name = m.getName();
            List<? extends CharSequence> params = m.getParameterTypes();
            if (name.equals("initialize") && params.isEmpty() && m.getReturnType().equals("V")) {
                direct.add(hookVideoInfoInitialize(m));
                hookedInit = true;
            } else if (name.equals("setVideoId")
                && params.size() == 1
                && params.get(0).toString().equals("Ljava/lang/String;")
                && m.getReturnType().equals("V")) {
                direct.add(hookSetVideoId(m));
                hookedVideoId = true;
            } else if (name.equals("setVideoInformation")
                && params.size() == 6
                && params.get(0).toString().equals("Ljava/lang/String;")
                && params.get(4).toString().equals("J")
                && params.get(5).toString().equals("Z")
                && m.getReturnType().equals("V")) {
                direct.add(hookSetVideoInformation(m));
                hookedVideoInfo = true;
            } else if (name.equals("setVideoTime")
                && params.size() == 1
                && params.get(0).toString().equals("J")
                && m.getReturnType().equals("V")) {
                direct.add(hookSetVideoTime(m));
                hookedVideoTime = true;
            } else {
                direct.add(m);
            }
        }
        if (!hookedInit || !hookedVideoId || !hookedVideoInfo || !hookedVideoTime) {
            throw new IllegalStateException(
                "Missing VideoInformation hooks: init=" + hookedInit
                    + " id=" + hookedVideoId
                    + " info=" + hookedVideoInfo
                    + " time=" + hookedVideoTime
            );
        }
        List<Method> virtual = new ArrayList<>();
        for (Method m : c.getVirtualMethods()) virtual.add(m);
        return cloneClass(c, direct, virtual);
    }

    private static Method hookVideoInfoInitialize(Method m) {
        MutableMethodImplementation mutable = new MutableMethodImplementation(m.getImplementation());
        List<? extends Instruction> insns = mutable.getInstructions();
        int retIdx = -1;
        for (int i = insns.size() - 1; i >= 0; i--) {
            if (insns.get(i).getOpcode() == Opcode.RETURN_VOID) {
                retIdx = i;
                break;
            }
        }
        if (retIdx < 0) throw new IllegalStateException("No return-void in VideoInformation.initialize");
        mutable.addInstruction(
            retIdx,
            new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                0, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(SB_BRIDGE_TYPE, "initialize", Collections.emptyList(), "V")
            )
        );
        return cloneMethod(m, mutable);
    }

    private static Method hookSetVideoId(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null || impl.getRegisterCount() != 2) {
            throw new IllegalStateException("Unexpected VideoInformation.setVideoId register count");
        }
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        int sputIdx = -1;
        for (int i = 0; i < insns.size(); i++) {
            Instruction ins = insns.get(i);
            if (ins.getOpcode() == Opcode.SPUT_OBJECT
                && ins instanceof ReferenceInstruction
                && "Lapp/morphe/extension/youtube/shared/VideoInformation;->videoId:Ljava/lang/String;"
                    .equals(((ReferenceInstruction) ins).getReference().toString())) {
                sputIdx = i;
                break;
            }
        }
        if (sputIdx < 0) throw new IllegalStateException("Could not find sput-object videoId in setVideoId");
        mutable.addInstruction(
            sputIdx + 1,
            new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                1,
                1, // p0 = videoId
                0, 0, 0, 0,
                new ImmutableMethodReference(
                    SB_BRIDGE_TYPE,
                    "onVideoId",
                    Collections.singletonList("Ljava/lang/String;"),
                    "V"
                )
            )
        );
        return cloneMethod(m, mutable);
    }

    private static Method hookSetVideoInformation(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null || impl.getRegisterCount() != 16) {
            throw new IllegalStateException("Unexpected VideoInformation.setVideoInformation register count");
        }
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        // p0..p6 occupy registers v9..v15 (7 registers total: 4 refs + 2 for long + 1 for boolean).
        mutable.addInstruction(
            0,
            new BuilderInstruction3rc(
                Opcode.INVOKE_STATIC_RANGE,
                9,
                7,
                new ImmutableMethodReference(
                    SB_BRIDGE_TYPE,
                    "onVideoInformation",
                    Arrays.asList(
                        "Ljava/lang/String;",
                        "Ljava/lang/String;",
                        "Ljava/lang/String;",
                        "Ljava/lang/String;",
                        "J",
                        "Z"
                    ),
                    "V"
                )
            )
        );
        return cloneMethod(m, mutable);
    }

    private static Method hookSetVideoTime(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null || impl.getRegisterCount() != 3) {
            throw new IllegalStateException("Unexpected VideoInformation.setVideoTime register count");
        }
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        int retIdx = -1;
        for (int i = insns.size() - 1; i >= 0; i--) {
            if (insns.get(i).getOpcode() == Opcode.RETURN_VOID) {
                retIdx = i;
                break;
            }
        }
        if (retIdx < 0) throw new IllegalStateException("No return-void in VideoInformation.setVideoTime");
        mutable.addInstruction(
            retIdx,
            new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                2,
                1, // p0 (lo)
                2, // p1 (hi)
                0, 0, 0,
                new ImmutableMethodReference(
                    SB_BRIDGE_TYPE,
                    "onVideoTime",
                    Collections.singletonList("J"),
                    "V"
                )
            )
        );
        return cloneMethod(m, mutable);
    }

    private static Method patchSponsorBlockSettingsDefaults(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) throw new IllegalStateException("Settings.<clinit> has no body");
        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<? extends Instruction> insns = mutable.getInstructions();
        boolean flippedConnErrorToast = false;

        for (int i = 0; i < insns.size() - 3; i++) {
            Instruction cur = insns.get(i);
            if ((cur.getOpcode() != Opcode.CONST_STRING && cur.getOpcode() != Opcode.CONST_STRING_JUMBO)
                || !(cur instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) cur).getReference();
            if (!(ref instanceof StringReference)) continue;
            String key = ((StringReference) ref).getString();
            if (!"sb_toast_on_connection_error".equals(key)) continue;

            for (int j = i + 1; j <= Math.min(i + 6, insns.size() - 1); j++) {
                Instruction cand = insns.get(j);
                if (cand.getOpcode() == Opcode.INVOKE_DIRECT
                    && cand instanceof FiveRegisterInstruction
                    && cand instanceof ReferenceInstruction) {
                    String mref = ((ReferenceInstruction) cand).getReference().toString();
                    if (mref.startsWith("Lapp/morphe/extension/shared/settings/BooleanSetting;-><init>(Ljava/lang/String;Ljava/lang/Boolean;")) {
                        FiveRegisterInstruction call = (FiveRegisterInstruction) cand;
                        // In Settings.<clinit> around line 9792, v9 holds Boolean.TRUE and v4 holds Boolean.FALSE.
                        if (call.getRegisterE() != 9) {
                            throw new IllegalStateException(
                                "Unexpected default boolean register for sb_toast_on_connection_error: v" + call.getRegisterE()
                            );
                        }
                        mutable.replaceInstruction(
                            j,
                            new BuilderInstruction35c(
                                Opcode.INVOKE_DIRECT,
                                call.getRegisterCount(),
                                call.getRegisterC(),
                                call.getRegisterD(),
                                4, // v4 = Boolean.FALSE
                                call.getRegisterF(),
                                call.getRegisterG(),
                                ((ReferenceInstruction) cand).getReference()
                            )
                        );
                        flippedConnErrorToast = true;
                        break;
                    }
                }
            }
        }
        if (!flippedConnErrorToast) {
            throw new IllegalStateException("Could not flip sb_toast_on_connection_error default in Settings.<clinit>");
        }
        return cloneMethod(m, mutable);
    }

    private static void patchClasses11(
        File inDex,
        File outDex,
        ClassDef newPlaybackDiag,
        List<ClassDef> bridgeAndOverlayClasses
    ) throws Exception {
        DexFile dex = DexFileFactory.loadDexFile(inDex, Opcodes.getDefault());
        List<ClassDef> updated = new ArrayList<>();
        boolean replacedPlaybackDiag = false;

        for (ClassDef c : dex.getClasses()) {
            String t = c.getType();
            if (t.equals(PLAYBACK_DIAG_TYPE)) {
                updated.add(newPlaybackDiag);
                replacedPlaybackDiag = true;
            } else if (t.startsWith(PLAYBACK_DIAG_PREFIX)) {
                // Drop old PlaybackDiagnostics anonymous inner classes
                continue;
            } else if (t.equals(SB_BRIDGE_TYPE) || t.startsWith(SB_BRIDGE_PREFIX)
                || t.equals(SB_OVERLAY_HELPER_TYPE) || t.startsWith(SB_OVERLAY_HELPER_PREFIX)) {
                continue;
            } else {
                updated.add(c);
            }
        }
        if (!replacedPlaybackDiag) throw new IllegalStateException("Did not find PlaybackDiagnostics in classes11.dex");
        updated.addAll(bridgeAndOverlayClasses);
        DexFileFactory.writeDexFile(outDex.getAbsolutePath(), new ImmutableDexFile(dex.getOpcodes(), updated));
    }

    private static ClassDef cloneClass(ClassDef c, List<Method> direct, List<Method> virtual) {
        return new ImmutableClassDef(
            c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
            c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(),
            direct, virtual
        );
    }

    private static Method cloneMethod(Method m, MethodImplementation impl) {
        return new ImmutableMethod(
            m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
            m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), impl
        );
    }
}
