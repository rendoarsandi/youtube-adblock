import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

/** Correct the existing GmsCore status and add read-only, fail-safe outcomes. */
public final class EnablePlayback {
    static final String HELPER = "Lapp/local/youtube/adblock/PlaybackDiagnostics;";
    record Target(String hook, int flags, int registers, String digest) {}
    static final Map<String, Target> TARGETS = Map.ofEntries(
        Map.entry("Lapp/morphe/extension/shared/patches/PatchStatus;->GmsCoreSupport()Z", new Target("gms-status", 9, 1, "3eee0b08fa052c50ed72b4ae9486257a113bc9ef48c0d11efbba79dff7458729")),
        Map.entry("Lapp/local/youtube/adblock/GmsStartup;->check(Ljava/lang/Object;)V", new Target("startup", 9, 9, "8f28f601599e42363bfc9a509ba717ab3ad10bdd89f91d23a236babd98566b65")),
        Map.entry("Ldlt;->onFailed(Lorg/chromium/net/UrlRequest;Lorg/chromium/net/UrlResponseInfo;Lorg/chromium/net/CronetException;)V", new Target("mediaFailed", 131089, 4, "20fd2a55df0ea0a713f155002687d4912fdb6f0d5137d0ac8b7cd9a17997f086")),
        Map.entry("Ldlt;->onResponseStarted(Lorg/chromium/net/UrlRequest;Lorg/chromium/net/UrlResponseInfo;)V", new Target("mediaResponse", 131089, 5, "d950e95c59a2c5d9b213290b8647f176ecd014ac56ab915175ffa05f0055cb6a")),
        Map.entry("Laxtf;->onFailed(Lorg/chromium/net/UrlRequest;Lorg/chromium/net/UrlResponseInfo;Lorg/chromium/net/CronetException;)V", new Target("apiFailed", 17, 6, "9d69a7e3f0caa785f3d27cfe35b579f6d06904371f59720deb62ba96451a2139")),
        Map.entry("Laxtf;->onResponseStarted(Lorg/chromium/net/UrlRequest;Lorg/chromium/net/UrlResponseInfo;)V", new Target("apiResponse", 17, 6, "282a0b52ea6f79862a2c768bc7cc706cf5b0b356739e9f58cf6ddd475cfa9200")),
        Map.entry("Lapp/morphe/extension/shared/spoof/requests/StreamOrDetailsDataRequest;->send(Lapp/morphe/extension/shared/spoof/ClientType;Ljava/lang/String;Ljava/util/Map;ZZ)Ljava/net/HttpURLConnection;", new Target("spoofResponse", 10, 14, "788f6ff7359f29da888a814b60161d88b5ff10af4bffb2c61d2bc83d1d715979"))
    );
    static final Set<String> SEEN = new HashSet<>();
    static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    static void prefix(MutableMethodImplementation body, int index, com.android.tools.smali.dexlib2.builder.BuilderInstruction instruction) {
        var old = body.getInstructions().get(index);
        body.replaceInstruction(index, instruction); body.addInstruction(index + 1, old);
    }
    static Method patch(Method method) throws Exception {
        Target target = TARGETS.get(method.toString());
        if (target == null) return method;
        check(SEEN.add(method.toString()), "Duplicate guarded method");
        var original = method.getImplementation();
        check(original != null && method.getAccessFlags() == target.flags() && original.getRegisterCount() == target.registers(), "Target frame differs: " + method);
        List<Instruction> code = new ArrayList<>(); original.getInstructions().forEach(code::add);
        var first = (DexBackedInstruction) code.get(0);
        int units = code.stream().mapToInt(Instruction::getCodeUnits).sum();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first.dexFile.getDataBuffer().readByteRange(first.instructionStart, units * 2)));
        check(digest.equals(target.digest()), "Target digest differs: " + method);
        MethodImplementation changed;
        if (target.hook().equals("gms-status")) {
            // Legacy GmsCore routing is already present in this exact input.
            changed = new ImmutableMethodImplementation(1, List.of(
                new ImmutableInstruction11n(Opcode.CONST_4, 0, 1), new ImmutableInstruction11x(Opcode.RETURN, 0)), List.of(), List.of());
        } else {
            MutableMethodImplementation body = new MutableMethodImplementation(original);
            if (target.hook().equals("spoofResponse")) {
                int matches = 0;
                for (int i = code.size() - 1; i >= 0; i--) {
                    Instruction instruction = code.get(i);
                    if (!(instruction instanceof ReferenceInstruction r) || !(r.getReference() instanceof MethodReference m)
                            || !m.toString().equals("Ljava/net/HttpURLConnection;->getResponseCode()I")) continue;
                    check(code.get(i + 1).getOpcode() == Opcode.MOVE_RESULT, "Response result layout differs");
                    int status = ((OneRegisterInstruction) code.get(i + 1)).getRegisterA();
                    check(status == 10, "Expected response code in p1");
                    prefix(body, i + 2, new BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, status, 9, 0, 0, 0,
                        new ImmutableMethodReference(HELPER, "spoofResponse", List.of("I", "Ljava/lang/Object;"), "V")));
                    matches++;
                }
                check(matches == 1, "Expected exactly one HTTP response observation");
            } else {
                boolean failure = target.hook().endsWith("Failed");
                int input = target.hook().equals("startup") ? 8 : target.registers() - (failure ? 2 : 1);
                String hook = target.hook().equals("startup") ? "install" : target.hook();
                List<String> types = failure ? List.of("Ljava/lang/Object;", "Ljava/lang/Object;") : List.of("Ljava/lang/Object;");
                prefix(body, 0, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE, input, types.size(), new ImmutableMethodReference(HELPER, hook, types, "V")));
            }
            changed = body;
        }
        return new ImmutableMethod(method.getDefiningClass(), method.getName(), method.getParameters(), method.getReturnType(), method.getAccessFlags(), method.getAnnotations(), method.getHiddenApiRestrictions(), changed);
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]); Files.createDirectories(output);
        Set<String> owners = new HashSet<>(); for (String id : TARGETS.keySet()) owners.add(id.substring(0, id.indexOf("->")));
        List<ClassDef> moved = new ArrayList<>();
        try (ApkModule module = ApkModule.loadApkFile(Path.of(args[0]).toFile()); ZipFile zip = new ZipFile(args[0])) {
            var manifest = module.getAndroidManifest();
            check(manifest.getPackageName().equals("app.revanced.android.youtube") && manifest.getVersionCode() == 1561301414, "Wrong update input");
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().matches("classes(?:[2-9][0-9]*)?\\.dex")) continue;
                DexBackedDexFile dex = new DexBackedDexFile(null, zip.getInputStream(entry).readAllBytes());
                List<ClassDef> retained = new ArrayList<>(); boolean changed = false;
                for (ClassDef type : dex.getClasses()) {
                    check(!type.getType().startsWith(HELPER.substring(0, HELPER.length() - 1)), "Diagnostic namespace already exists");
                    if (!owners.contains(type.getType())) { retained.add(type); continue; }
                    List<Method> direct = new ArrayList<>(), virtual = new ArrayList<>();
                    for (Method m : type.getDirectMethods()) direct.add(patch(m));
                    for (Method m : type.getVirtualMethods()) virtual.add(patch(m));
                    moved.add(new ImmutableClassDef(type.getType(), type.getAccessFlags(), type.getSuperclass(), type.getInterfaces(), type.getSourceFile(), type.getAnnotations(), type.getStaticFields(), type.getInstanceFields(), direct, virtual));
                    changed = true;
                }
                if (changed) {
                    Path file = output.resolve(entry.getName());
                    DexPool.writeTo(file.toString(), new ImmutableDexFile(dex.getOpcodes(), retained));
                    module.add(new ByteInputSource(Files.readAllBytes(file), entry.getName()));
                }
            }
            check(SEEN.equals(TARGETS.keySet()) && moved.size() == owners.size(), "Missing guarded integration targets");
            DexBackedDexFile helpers = new DexBackedDexFile(null, Files.readAllBytes(Path.of(args[2])));
            List<ClassDef> combined = new ArrayList<>(moved);
            for (ClassDef type : helpers.getClasses()) {
                check(type.getType().startsWith(HELPER.substring(0, HELPER.length() - 1)), "Unexpected helper namespace");
                combined.add(type);
            }
            check(!combined.isEmpty() && zip.getEntry("classes11.dex") == null, "Unexpected added DEX");
            Path helperPath = output.resolve("classes11.dex");
            DexPool.writeTo(helperPath.toString(), new ImmutableDexFile(helpers.getOpcodes(), combined));
            DexBackedDexFile assembled = new DexBackedDexFile(null, Files.readAllBytes(helperPath));
            Map<String, ClassDef> actual = new HashMap<>(); for (ClassDef c : assembled.getClasses()) actual.put(c.getType(), c);
            check(actual.size() == combined.size(), "Added DEX class count differs");
            for (ClassDef c : combined) check(NonRootYoutube.render(c).equals(NonRootYoutube.render(actual.get(c.getType()))), "Assembly differs: " + c.getType());
            module.add(new ByteInputSource(Files.readAllBytes(helperPath), "classes11.dex"));
            manifest.setVersionCode(1561301415);
            module.setApkSignatureBlock(null); module.removeInputSource("stamp-cert-sha256");
            for (var source : module.listInputSources()) if (source.getAlias().matches("META-INF/(?:MANIFEST\\.MF|[^/]+\\.(?:SF|RSA|DSA|EC))")) module.removeInputSource(source.getAlias());
            module.refreshManifest(); module.refreshTable();
            module.writeApk(output.resolve("updated-unsigned.apk").toFile());
            Files.writeString(output.resolve("hook-count.txt"), Integer.toString(SEEN.size()));
            System.out.println("Validated GmsCore playback default and six bounded observation hooks.");
        }
    }
}
