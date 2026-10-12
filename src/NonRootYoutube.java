import java.io.StringWriter;
import java.security.MessageDigest;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.iface.debug.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.immutable.debug.ImmutableLineNumber;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.arsc.chunk.xml.*;

/** Exact-version local adaptation of upstream GmsCore routing and the ad hook.
 * Upstream sources retained in upstream-gms/. Runtime behavior is unverified.
 * No account handling, networking, target execution or installation here.
 */
public final class NonRootYoutube {
    static final String FROM = "com.google.android.youtube";
    static final String TO = "app.revanced.android.youtube";
    static final String GMS = "app.revanced.android.gms";
    static final Set<String> GMS_PERMISSIONS = Set.of(
        "com.google.android.providers.gsf.permission.READ_GSERVICES", "com.google.android.c2dm.permission.RECEIVE",
        "com.google.android.c2dm.permission.SEND", "com.google.android.gtalkservice.permission.GTALK_SERVICE",
        "com.google.android.googleapps.permission.GOOGLE_AUTH", "com.google.android.googleapps.permission.GOOGLE_AUTH.cp",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.local", "com.google.android.googleapps.permission.GOOGLE_AUTH.mail",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.writely", "com.google.android.gms.permission.ACTIVITY_RECOGNITION",
        "com.google.android.gms.permission.AD_ID", "com.google.android.gms.permission.AD_ID_NOTIFICATION",
        "com.google.android.gms.auth.api.phone.permission.SEND", "com.google.android.gms.permission.CAR_INFORMATION",
        "com.google.android.gms.permission.CAR_SPEED", "com.google.android.gms.permission.CAR_FUEL",
        "com.google.android.gms.permission.CAR_MILEAGE", "com.google.android.gms.permission.CAR_VENDOR_EXTENSION",
        "com.google.android.gms.locationsharingreporter.periodic.STATUS_UPDATE", "com.google.android.gms.auth.permission.GOOGLE_ACCOUNT_CHANGE");
    static final Set<String> GMS_AUTHORITIES = Set.of(
        "com.google.android.gms.fileprovider", "com.google.android.gms.auth.accounts", "com.google.android.gms.chimera",
        "com.google.android.gms.fonts", "com.google.android.gms.phenotype", "com.google.android.gsf.gservices",
        "com.google.settings", "subscribedfeeds");
    static final Set<String> APP_PERMISSIONS = new HashSet<>(Set.of("org.microg.gms.STATUS_BROADCAST", "org.microg.gms.EXTENDED_ACCESS", "org.microg.gms.PROVISION"));
    static final Set<String> APP_AUTHORITIES = new HashSet<>();
    static final Map<String, Integer> CHANGES = new TreeMap<>();
    static final Set<String> SEEN_HOOKS = new HashSet<>();
    // Original classes4.dex has a full 16-bit method-reference pool. Relocating
    // the patched search endpoint keeps its additional reference out of that pool.
    static final List<ClassDef> RELOCATED = new ArrayList<>();
    static final Map<String, String> HOOKS = Map.of(
        "Lsye;->b(Landroid/content/Context;I)I", "availability",
        "Lsye;->d(Landroid/content/Context;I)V", "service-check",
        "Lzvu;->a()Z", "prime",
        "Lanca;->a(Lugg;)V", "cast-context",
        "Lalap;->b()V", "browse-ads",
        "Lalic;->b()V", "search-ads",
        "Lacpe;->t(Ljava/util/List;)V", "ads");

    record StreamHook(int template, int at, int flags, int registers, String digest) {}
    static final Map<String, StreamHook> STREAMS = Map.ofEntries(
        Map.entry("Lafnb;->aL(Lafbr;Ljava/util/Map;[BLaezf;Lorg/chromium/net/UrlRequest$Callback;Lafem;Laeyk;)Lorg/chromium/net/UrlRequest$Builder;", new StreamHook(0, 23, 9, 12, "6b79cdc7b3236b13cdd6a542669233fb8fce2c63cb5dc3282fbbddf6e6b14c72")),
        Map.entry("Ldlu;->o(Ldjs;)Lorg/chromium/net/UrlRequest$Builder;", new StreamHook(1, 8, 4, 8, "9246d5232aa6bd234e8ace54e257eccaf0d8566fed9127e0c88194e8746bb153")),
        Map.entry("Lakul;-><init>(Lbgmw;)V", new StreamHook(2, 44, 65537, 6, "0ae244895e82439ccf496a75d5842cb6dd141d72478998f8558def5ce9153491")),
        Map.entry("Ldjs;-><init>(Landroid/net/Uri;JLjava/lang/String;I[BLjava/util/Map;JJLjava/lang/String;ILjava/lang/Object;)V", new StreamHook(3, 103, 65537, 25, "ef9698a3f282e8dad7a8c28d29ba2154f917d5367e8b689e54347769dad1a107")),
        Map.entry("Laoja;->a(Laoku;Laumz;Lasbi;Lapcj;Lafbr;Lanjz;Laoix;)Laojd;", new StreamHook(4, 13, 17, 44, "2f424569f46bad564911250bdcbec91dee2b14fa859d9638bf6e327e1e50941f")),
        Map.entry("Lihd;->ad()Lasaz;", new StreamHook(5, 56, 16, 7, "efedacdedfcbae4a817096e8ac95be0be930ab945be2c2b510da413faf2ac98a")),
        Map.entry("Laosi;->ax(ZLblgy;)V", new StreamHook(6, 143, 17, 25, "e4ecbff5ae5a96fabe48af30a8c27251e06ef06d1c68d60259cc5a402ac5b120")),
        Map.entry("Lcom/google/android/libraries/youtube/media/player/exo2/platypus/PlatypusEnablingManager;->c(Lakui;Lakun;)Lcom/google/android/libraries/youtube/media/player/exo2/platypus/PlatypusEnablingManager$DecisionWithReason;", new StreamHook(7, 0, 18, 4, "11f3d752e74f3973bb33b78d35edc95b4a4fb7bdd90a7d810c2e30f372cd72d4")));
    static ClassDef TEMPLATES;

    static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    static void count(String value) { CHANGES.merge(value, 1, Integer::sum); }
    static String replacePrefix(String value, String from, String to) {
        return value.startsWith(from) ? value.replace(from, to) : to + "." + value;
    }
    static String transform(String s) {
        if (s.equals("com.google") || s.equals("com.google.android.gms") || GMS_PERMISSIONS.contains(s) || GMS_AUTHORITIES.contains(s))
            return replacePrefix(s, "com.google", "app.revanced");
        if (APP_PERMISSIONS.contains(s) || APP_AUTHORITIES.contains(s)) return replacePrefix(s, FROM, TO);
        if (s.startsWith("content://")) {
            try {
                String authority = URI.create(s).getAuthority();
                if (GMS_AUTHORITIES.contains(authority)) return s.replace(authority, replacePrefix(authority, "com.google", "app.revanced"));
                if (APP_AUTHORITIES.contains(authority)) return s.replace(authority, replacePrefix(authority, FROM, TO));
            } catch (IllegalArgumentException ignored) { }
        }
        return s;
    }
    static String string(Instruction i) {
        if (i.getOpcode() != Opcode.CONST_STRING && i.getOpcode() != Opcode.CONST_STRING_JUMBO) return null;
        return ((StringReference)((ReferenceInstruction)i).getReference()).getString();
    }
    static String id(Method m) { return m.toString(); }
    static void named(ResXmlElement e, String name, String value) {
        int resource = name.equals("name") ? 0x01010003 : name.equals("value") ? 0x01010024 : 0x01010001;
        e.getOrCreateAndroidAttribute(name, resource).setValueAsString(value);
    }
    static void metadata(ResXmlElement app, String name, String value) {
        ResXmlElement e = app.newElement("meta-data"); named(e, "name", name); named(e, "value", value);
    }
    static void patchManifest(ApkModule module) {
        AndroidManifestBlock manifest = module.getAndroidManifest();
        require(FROM.equals(manifest.getPackageName()) && manifest.getVersionCode() == 1561301413, "Wrong merged target");
        manifest.ensureFullClassNames();
        ResXmlElement root = manifest.getManifestElement(), app = manifest.getApplicationElement();
        Iterator<ResXmlElement> permissions = root.getElements("permission");
        while (permissions.hasNext()) {
            ResXmlAttribute a = permissions.next().searchAttributeByResourceId(0x01010003);
            if (a != null) APP_PERMISSIONS.add(a.getValueAsString());
        }
        for (ResXmlElement provider : manifest.listApplicationElementsByTag("provider")) {
            ResXmlAttribute a = provider.searchAttributeByResourceId(0x01010018);
            if (a == null) continue;
            String[] authorities = a.getValueAsString().split(";");
            for (int i = 0; i < authorities.length; i++) {
                APP_AUTHORITIES.add(authorities[i]); authorities[i] = replacePrefix(authorities[i], FROM, TO);
            }
            a.setValueAsString(String.join(";", authorities));
        }
        Iterator<ResXmlElement> elements = root.recursiveElements();
        while (elements.hasNext()) {
            ResXmlElement e = elements.next();
            for (int attrId : new int[]{0x01010003, 0x01010006, 0x01010007, 0x01010008}) {
                ResXmlAttribute a = e.searchAttributeByResourceId(attrId);
                if (a == null) continue;
                String s = a.getValueAsString();
                if (s != null && (APP_PERMISSIONS.contains(s) || GMS_PERMISSIONS.contains(s))) a.setValueAsString(transform(s));
            }
        }
        module.setPackageName(TO);
        for (com.reandroid.arsc.chunk.PackageBlock tablePackage : module.getTableBlock()) {
            if (FROM.equals(tablePackage.getName())) tablePackage.setName(TO);
        }
        manifest.setApplicationLabel("YouTube (Ad test)");
        if (manifest.getMainActivity() != null) named(manifest.getMainActivity(), "label", "YouTube (Ad test)");
        ResXmlElement query = root.getOrCreateElement("queries").newElement("package"); named(query, "name", GMS);
        metadata(app, GMS + ".SPOOFED_PACKAGE_NAME", FROM);
        metadata(app, GMS + ".SPOOFED_PACKAGE_SIGNATURE", "24bb24c05e47e0aefa68a58a766179d9b613a600");
        metadata(app, "app.revanced.MICROG_PACKAGE_NAME", GMS);
        module.refreshManifest();
        module.refreshTable();
    }

    static Method patch(Method method) {
        MethodImplementation body = method.getImplementation();
        if (body == null) return ImmutableMethod.of(method);
        String hook = HOOKS.get(id(method));
        List<Instruction> original = new ArrayList<>(); body.getInstructions().forEach(original::add);
        Set<String> strings = new HashSet<>(); for (Instruction i : original) { String s = string(i); if (s != null) strings.add(s); }
        if (hook != null) {
            require(SEEN_HOOKS.add(hook), "Duplicate hook " + hook);
            switch (hook) {
                case "availability": require(strings.containsAll(Set.of("This should never happen.", "MetadataValueReader", "com.google.android.gms")), "Availability fingerprint changed"); break;
                case "service-check": require(strings.contains("Google Play Services not available"), "Service fingerprint changed"); break;
                case "cast-context": require(strings.contains("Error fetching CastContext."), "Cast fingerprint changed"); break;
                case "prime": require(strings.containsAll(Set.of("com.android.vending", "com.google.android.GoogleCamera", FROM)), "Prime fingerprint changed"); break;
                case "browse-ads":
                case "search-ads":
                    boolean browse = hook.equals("browse-ads");
                    Opcode[] expected = browse
                        ? new Opcode[]{Opcode.IGET_OBJECT, Opcode.IGET_OBJECT, Opcode.FILLED_NEW_ARRAY, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_STATIC, Opcode.RETURN_VOID}
                        : new Opcode[]{Opcode.SGET_OBJECT, Opcode.IGET_OBJECT, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.IF_NEZ, Opcode.IGET_OBJECT, Opcode.IGET_OBJECT, Opcode.FILLED_NEW_ARRAY, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_STATIC, Opcode.RETURN_VOID};
                    require(method.getAccessFlags() == 20 && body.getRegisterCount() == (browse ? 2 : 3)
                        && body.getTryBlocks().isEmpty() && original.size() == expected.length, "Feed hook frame changed");
                    for (int i = 0; i < expected.length; i++) require(original.get(i).getOpcode() == expected[i], "Feed opcode guard mismatch");
                    String owner = browse ? "Lalap;" : "Lalic;";
                    require(((ReferenceInstruction)original.get(original.size() - 2)).getReference().toString().equals(owner + "->F([Ljava/lang/String;)V"), "Feed endpoint method changed");
                    if (!browse) require(((OffsetInstruction)original.get(4)).getCodeOffset() == 13, "Search return branch changed");
                    break;
                case "ads":
                    require(strings.containsAll(Set.of(
                        "TriggerBundle doesn't have the required metadata specified by the trigger ",
                        "Ping migration no associated ping bindings for activated trigger: ")),
                        "Video ad loader fingerprint changed");
                    require(method.getReturnType().equals("V") && method.getAccessFlags() == 17
                        && body.getRegisterCount() == 33, "Video ad loader frame changed"); break;
            }
            count("hook:" + hook);
        }
        List<Instruction> code = new ArrayList<>(); boolean modified = false;
        for (Instruction i : original) {
            String s = string(i), replacement = s == null ? null : transform(s);
            if ("prime".equals(hook) && FROM.equals(s)) replacement = TO;
            if (s != null && !s.equals(replacement)) {
                int register = ((OneRegisterInstruction)i).getRegisterA();
                i = i.getOpcode() == Opcode.CONST_STRING ? new ImmutableInstruction21c(Opcode.CONST_STRING, register, new ImmutableStringReference(replacement))
                    : new ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, register, new ImmutableStringReference(replacement));
                modified = true; count("string:" + s + " -> " + replacement);
            }
            code.add(i);
        }
        MethodImplementation updated = new ImmutableMethodImplementation(body.getRegisterCount(), code, body.getTryBlocks(), body.getDebugItems());
        if ("availability".equals(hook)) {
            updated = new ImmutableMethodImplementation(body.getRegisterCount(), List.of(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0), new ImmutableInstruction11x(Opcode.RETURN, 0)), List.of(), List.of());
        } else if ("service-check".equals(hook) || "cast-context".equals(hook)) {
            updated = new ImmutableMethodImplementation(body.getRegisterCount(), List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of());
        } else if ("ads".equals(hook)) {
            // Equivalent to the checked upstream Video ads guard always choosing
            // its early-return branch. No additional settings/extension class.
            updated = new ImmutableMethodImplementation(body.getRegisterCount(),
                List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of());
        } else if ("browse-ads".equals(hook) || "search-ads".equals(hook)) {
            MutableMethodImplementation feed = new MutableMethodImplementation(updated);
            ImmutableMethodReference helper = new ImmutableMethodReference(
                "Lapp/local/youtube/adblock/FeedAds;", "patch", List.of("Lakvx;"), "V");
            int returns = 0;
            for (int index = feed.getInstructions().size() - 1; index >= 0; index--) {
                if (feed.getInstructions().get(index).getOpcode() != Opcode.RETURN_VOID) continue;
                // Replacing the return first keeps incoming branches on the hook.
                feed.replaceInstruction(index, new BuilderInstruction3rc(
                    Opcode.INVOKE_STATIC_RANGE, body.getRegisterCount() - 1, 1, helper));
                feed.addInstruction(index + 1, new BuilderInstruction10x(Opcode.RETURN_VOID));
                returns++;
            }
            require(returns == 1, "Unexpected feed return count");
            updated = feed;
        }
        StreamHook stream = STREAMS.get(id(method));
        if (stream != null) {
            require(SEEN_HOOKS.add("stream:" + stream.template()), "Duplicate streaming hook");
            require(method.getAccessFlags() == stream.flags() && body.getRegisterCount() == stream.registers(), "Streaming frame changed");
            DexBackedInstruction first = (DexBackedInstruction)original.get(0);
            int units = original.stream().mapToInt(Instruction::getCodeUnits).sum();
            try {
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    first.dexFile.getDataBuffer().readByteRange(first.instructionStart, units * 2)));
                require(digest.equals(stream.digest()), "Streaming instruction digest changed: " + id(method));
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            int address = 0, index = -1;
            for (int j = 0; j < original.size(); j++) {
                if (address == stream.at()) { index = j; break; }
                address += original.get(j).getCodeUnits();
            }
            require(index >= 0, "Streaming insertion is not an instruction boundary");
            Method template = null;
            for (Method m : TEMPLATES.getMethods()) if (m.getName().equals("i" + stream.template())) template = m;
            require(template != null, "Streaming template absent");
            List<BuilderInstruction> prefix = new ArrayList<>(new MutableMethodImplementation(template.getImplementation()).getInstructions());
            if (stream.template() != 7) {
                require(prefix.get(prefix.size() - 1).getOpcode() == Opcode.RETURN_VOID, "Invalid template terminator");
                prefix.remove(prefix.size() - 1);
            }
            MutableMethodImplementation mutable = new MutableMethodImplementation(updated);
            BuilderInstruction displaced = mutable.getInstructions().get(index);
            // Replacing first preserves branch targets and try boundaries on the injected prefix.
            mutable.replaceInstruction(index, prefix.get(0));
            for (int j = 1; j < prefix.size(); j++) mutable.addInstruction(index + j, prefix.get(j));
            mutable.addInstruction(index + prefix.size(), displaced);
            updated = mutable;
            count("hook:stream:" + stream.template());
        }
        if (!modified && hook == null && stream == null) return ImmutableMethod.of(method);
        return new ImmutableMethod(method.getDefiningClass(), method.getName(), method.getParameters(), method.getReturnType(),
            method.getAccessFlags(), method.getAnnotations(), method.getHiddenApiRestrictions(), updated);
    }
    static String render(ClassDef c) throws Exception {
        BaksmaliOptions options = new BaksmaliOptions(); options.apiLevel = 36; options.accessorComments = false;
        StringWriter buffer = new StringWriter();
        BaksmaliWriter writer = new BaksmaliWriter(buffer) {
            @Override public void writeAnnotationElements(Collection<? extends AnnotationElement> elements) throws java.io.IOException {
                // Annotation member order is not semantic; the DEX writer sorts it.
                List<AnnotationElement> sorted = new ArrayList<>(elements);
                sorted.sort(Comparator.comparing(AnnotationElement::getName));
                super.writeAnnotationElements(sorted);
            }
        };
        ClassDefinition definition = new ClassDefinition(options, c); definition.writeTo(writer); writer.flush();
        require(!definition.hadValidationErrors(), "Invalid disassembly " + c.getType());
        return canonicalAnnotations(buffer.toString()).replaceAll("(?m)^(\\.field [^\\n]*:Z) = false$", "$1")
            .replaceAll("(?m)^(\\.field [^\\n]*:[BSCIJ]) = 0x0(?:L|t|s)?$", "$1")
            .replaceAll("(?m)^(\\.field [^\\n]*:[FD]) = 0\\.0f?$", "$1")
            .replaceAll("(?m)^(\\.field [^\\n]*:(?:L[^\\n]*;|\\[[^\\n]*)) = null$", "$1");
    }
    static String canonicalAnnotations(String text) {
        // A DEX annotation set is unordered. Sort only adjacent complete annotation
        // blocks separated by whitespace; array values retain their original order.
        Matcher matcher = Pattern.compile("(?ms)^([ \\t]*)\\.annotation [^\\n]+\\n.*?^\\1\\.end annotation(?:\\n|$)").matcher(text);
        List<int[]> ranges = new ArrayList<>(); List<String> indents = new ArrayList<>();
        while (matcher.find()) { ranges.add(new int[]{matcher.start(), matcher.end()}); indents.add(matcher.group(1)); }
        StringBuilder result = new StringBuilder(); int cursor = 0;
        for (int i = 0; i < ranges.size();) {
            int end = i;
            while (end + 1 < ranges.size() && indents.get(end + 1).equals(indents.get(i))
                && text.substring(ranges.get(end)[1], ranges.get(end + 1)[0]).isBlank()) end++;
            result.append(text, cursor, ranges.get(i)[0]);
            List<String> blocks = new ArrayList<>();
            for (int j = i; j <= end; j++) blocks.add(text.substring(ranges.get(j)[0], ranges.get(j)[1]).stripTrailing());
            Collections.sort(blocks); result.append(String.join("\n\n", blocks)).append("\n");
            cursor = ranges.get(end)[1]; i = end + 1;
        }
        return result.append(text.substring(cursor)).toString();
    }
    static void rewriteDex(ApkModule module, String name, byte[] original, Path output) throws Exception {
        DexBackedDexFile before = new DexBackedDexFile(null, original); List<ClassDef> expectedClasses = new ArrayList<>();
        for (ClassDef c : before.getClasses()) {
            List<Method> direct = new ArrayList<>(), virtual = new ArrayList<>();
            for (Method m : c.getDirectMethods()) direct.add(patch(m));
            for (Method m : c.getVirtualMethods()) virtual.add(patch(m));
            if (c.getType().equals("Lakul;")) {
                require(direct.stream().noneMatch(m -> m.getName().equals("patch_setStreamingData")), "Streaming bridge already exists");
                Method bridge = null;
                for (Method m : TEMPLATES.getDirectMethods()) if (m.getName().equals("streaming")) bridge = m;
                require(bridge != null, "Streaming bridge template absent");
                direct.add(new ImmutableMethod("Lakul;", "patch_setStreamingData", bridge.getParameters(), "V",
                    bridge.getAccessFlags(), bridge.getAnnotations(), bridge.getHiddenApiRestrictions(), bridge.getImplementation()));
            }
            ClassDef updatedClass = new ImmutableClassDef(c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(), c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(), direct, virtual);
            if ((name.equals("classes4.dex") && c.getType().equals("Lalic;")) || (name.equals("classes2.dex") && c.getType().equals("Lakul;"))) RELOCATED.add(updatedClass);
            else expectedClasses.add(updatedClass);
        }
        ImmutableDexFile expected = new ImmutableDexFile(before.getOpcodes(), expectedClasses);
        Path dexPath = output.resolve(name); require(!Files.exists(dexPath), "DEX output exists"); DexPool.writeTo(dexPath.toString(), expected);
        DexBackedDexFile actual = new DexBackedDexFile(null, Files.readAllBytes(dexPath)); Map<String, ClassDef> actualClasses = new HashMap<>();
        for (ClassDef c : actual.getClasses()) actualClasses.put(c.getType(), c);
        require(expected.getClasses().size() == actualClasses.size(), "Class count changed");
        for (ClassDef c : expected.getClasses()) {
            ClassDef a = actualClasses.get(c.getType());
            require(a != null, "Missing assembled class " + c.getType());
            String wanted = render(c), found = render(a);
            if (!wanted.equals(found)) {
                Files.writeString(output.resolve("mismatch-expected.smali"), wanted);
                Files.writeString(output.resolve("mismatch-actual.smali"), found);
                throw new IllegalStateException("Round-trip differs: " + c.getType());
            }
            if (c.getType().equals("Lacpe;") || c.getType().equals("Lsye;") || c.getType().equals("Lzvu;") || c.getType().equals("Lanca;")) Files.writeString(output.resolve(c.getType().substring(1,c.getType().length()-1) + ".smali"), render(a));
        }
        module.add(new ByteInputSource(Files.readAllBytes(dexPath), name));
        System.out.println("Validated assembled " + name + ": " + actualClasses.size() + " classes.");
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]).toAbsolutePath(); require(!Files.exists(output), "Output directory exists"); Files.createDirectories(output);
        try (ApkModule module = ApkModule.loadApkFile(Path.of(args[0]).toFile()); ZipFile zip = new ZipFile(args[0])) {
            DexBackedDexFile smaliHelpers = new DexBackedDexFile(null, Files.readAllBytes(Path.of(args[2])));
            for (ClassDef c : smaliHelpers.getClasses()) if (c.getType().equals("Lapp/local/youtube/adblock/Templates;")) TEMPLATES = c;
            require(TEMPLATES != null, "Streaming templates absent");
            patchManifest(module);
            List<String> dexFiles = zip.stream().map(ZipEntry::getName).filter(n -> n.matches("classes(?:[2-9][0-9]*)?\\.dex")).sorted().toList();
            require(dexFiles.size() == 8, "Unexpected DEX count");
            Set<String> originalTypes = new HashSet<>();
            for (String name : dexFiles) {
                byte[] data = zip.getInputStream(zip.getEntry(name)).readAllBytes();
                for (ClassDef type : new DexBackedDexFile(null, data).getClasses()) originalTypes.add(type.getType());
                rewriteDex(module, name, data, output);
            }
            Path helperPath = Path.of(args[2]);
            byte[] helperBytes = Files.readAllBytes(helperPath);
            DexBackedDexFile helpers = new DexBackedDexFile(null, helperBytes);
            List<ClassDef> combined = new ArrayList<>();
            for (ClassDef helper : helpers.getClasses()) if (!helper.getType().equals(TEMPLATES.getType())) combined.add(helper);
            DexBackedDexFile playback = new DexBackedDexFile(null, Files.readAllBytes(Path.of(args[3])));
            combined.addAll(playback.getClasses());
            Set<String> unique = new HashSet<>();
            for (ClassDef c : combined) {
                require(unique.add(c.getType()) && !originalTypes.contains(c.getType()), "Helper descriptor collision " + c.getType());
                require(!c.getType().startsWith("Landroid/") && !c.getType().startsWith("Lorg/json/")
                    && !c.getType().startsWith("Lcom/google/protobuf/"), "Compilation stub or unshaded protobuf leaked");
            }
            boolean feedFound = false;
            for (ClassDef helper : helpers.getClasses()) {
                require(!originalTypes.contains(helper.getType()), "Helper class collides with original " + helper.getType());
                render(helper);
                if (helper.getType().equals("Lapp/local/youtube/adblock/FeedAds;")) feedFound = true;
            }
            require(feedFound, "Feed helper absent");
            require(RELOCATED.size() == 2 && new HashSet<>(RELOCATED.stream().map(ClassDef::getType).toList()).equals(Set.of("Lalic;", "Lakul;")), "Unexpected relocated class set");
            combined.addAll(RELOCATED);
            Path combinedPath = output.resolve("classes9.dex");
            DexPool.writeTo(combinedPath.toString(), new ImmutableDexFile(helpers.getOpcodes(), combined));
            DexBackedDexFile serialized = new DexBackedDexFile(null, Files.readAllBytes(combinedPath));
            Map<String, ClassDef> classes = new HashMap<>();
            for (ClassDef c : serialized.getClasses()) classes.put(c.getType(), c);
            require(classes.size() == combined.size(), "Combined helper class count differs");
            for (ClassDef c : combined) require(classes.containsKey(c.getType()) && render(c).equals(render(classes.get(c.getType()))), "Combined helper serialization differs " + c.getType());
            module.add(new ByteInputSource(Files.readAllBytes(combinedPath), "classes9.dex"));
            System.out.println("Validated helper classes9.dex: " + classes.size() + " classes, including two relocated classes.");
            Set<String> expectedHooks = new HashSet<>(HOOKS.values());
            for (StreamHook stream : STREAMS.values()) expectedHooks.add("stream:" + stream.template());
            require(SEEN_HOOKS.equals(expectedHooks), "Missing hooks " + SEEN_HOOKS);
            module.setApkSignatureBlock(null); module.removeInputSource("stamp-cert-sha256");
            for (com.reandroid.archive.InputSource input : module.listInputSources()) {
                if (input.getAlias().matches("META-INF/(?:MANIFEST\\.MF|[^/]+\\.(?:SF|RSA|DSA|EC))")) module.removeInputSource(input.getAlias());
            }
            module.removeInputSource("assets/dexopt/baseline.prof"); module.removeInputSource("assets/dexopt/baseline.profm");
            module.refreshManifest(); module.refreshTable();
            module.writeApk(output.resolve("app-unsigned.apk").toFile());
        }
        List<String> lines = new ArrayList<>(); CHANGES.forEach((key, value) -> lines.add(value + "\t" + key)); Files.write(output.resolve("changes.tsv"), lines);
        System.out.println("Built " + TO + "; hooks " + SEEN_HOOKS + "; not runtime verified.");
    }
}
