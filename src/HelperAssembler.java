import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import java.nio.file.Files;
import java.nio.file.Path;

/** Assemble locally authored version-specific bridge helpers. */
public final class HelperAssembler {
    public static void main(String[] args) throws Exception {
        if (Files.exists(Path.of(args[1]))) throw new IllegalStateException("Helper output exists");
        SmaliOptions options = new SmaliOptions();
        options.apiLevel = 32;
        options.outputDexFile = args[1];
        options.jobs = 1;
        if (!Smali.assemble(options, args[0])) throw new IllegalStateException("Helper assembly failed");
    }
}
