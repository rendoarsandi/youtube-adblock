import java.security.MessageDigest;
import java.util.*;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.*;

/** Exact-version adaptation, 2026-10-22, of ReVanced's GPL-3.0 background
 * playback patch. https://gitlab.com/ReVanced/revanced-patches/-/blob/main/
 * patches/src/main/kotlin/app/revanced/patches/youtube/misc/backgroundplayback/BackgroundPlaybackPatch.kt
 * Only local eligibility/settings/child-content policy is changed. Stream
 * requests, decoding, media services, and ad filters retain their existing code.
 */
public final class BackgroundPlayback {
    private record Guard(String hook, int flags, int registers, String digest) {}
    private static final Map<String, Guard> TARGETS = Map.of(
        "Laqqn;->q(Lbgmn;)Z", new Guard("background-video", 9, 4,
            "4023e298d902c4f11e8ac76effcaa5a28098c481ec750e139f3312c4aed4db8c"),
        "Laqqn;->v(Lbgmn;)Z", new Guard("background-shorts", 9, 3,
            "c6d365f4b8d8289cf3b9b7da536c79395adfd85e22f37eaef017a43b9b4e1bb3"),
        "Lvaz;->z()Z", new Guard("background-settings", 17, 2,
            "a10a7bfeb13c58e2d54f7928b2db495a361fd035ad1e87c141624d93b90f41d8"),
        "Latdr;->cd()Z", new Guard("background-shorts-feature", 17, 4,
            "b5b5b6559a8df0a77eecb5842c28d2e0af5aab2425355336e8083b34e66a5ecf"),
        "Lrhq;->g(ILjkw;Lbiuc;)V", new Guard("background-kids-policy", 17, 7,
            "380944f5aa496d2bb81b8f640fe53faf3f14f1876d74d25756324b5457549c68")
    );

    static String hook(String id) {
        Guard guard = TARGETS.get(id);
        return guard == null ? null : guard.hook();
    }

    static Set<String> hooks() {
        Set<String> result = new HashSet<>();
        for (Guard guard : TARGETS.values()) result.add(guard.hook());
        return result;
    }

    static MethodImplementation apply(Method method) {
        Guard guard = TARGETS.get(method.toString());
        MethodImplementation body = method.getImplementation();
        if (guard == null || body == null) throw new IllegalArgumentException("Missing background target");
        if (method.getAccessFlags() != guard.flags() || body.getRegisterCount() != guard.registers())
            throw new IllegalStateException("Background target frame changed: " + method);
        List<Instruction> original = new ArrayList<>();
        body.getInstructions().forEach(original::add);
        DexBackedInstruction first = (DexBackedInstruction) original.get(0);
        int units = original.stream().mapToInt(Instruction::getCodeUnits).sum();
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                first.dexFile.getDataBuffer().readByteRange(first.instructionStart, units * 2)));
            if (!digest.equals(guard.digest())) throw new IllegalStateException("Background target instructions changed: " + method);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        List<? extends Instruction> replacement = method.getReturnType().equals("V")
            ? List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID))
            : List.of(new ImmutableInstruction11n(Opcode.CONST_4, 0, 1), new ImmutableInstruction11x(Opcode.RETURN, 0));
        return new ImmutableMethodImplementation(body.getRegisterCount(), replacement, List.of(), List.of());
    }
}
