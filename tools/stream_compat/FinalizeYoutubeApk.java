import com.reandroid.apk.ApkModule;
import java.io.File;

/** Packaging only. Retains all patched DEX code and upstream extension credits. */
public final class FinalizeYoutubeApk {
    public static void main(String[] args) throws Exception {
        try (ApkModule module = ApkModule.loadApkFile(new File(args[0]))) {
            var manifest = module.getAndroidManifest();
            if (!"com.google.android.youtube".equals(manifest.getPackageName())
                    || !"21.40.161".equals(manifest.getVersionName()))
                throw new IllegalStateException("Unexpected patched YouTube identity");
            manifest.ensureFullClassNames();
            manifest.setPackageName("app.revanced.android.youtube");
            manifest.setVersionCode(1561301414);
            manifest.setApplicationLabel("YouTube Adblock");
            manifest.getMainActivity().getOrCreateAndroidAttribute("label", 0x01010001)
                .setValueAsString("YouTube Adblock");
            for (var pkg : module.getTableBlock())
                if ("com.google.android.youtube".equals(pkg.getName())) pkg.setName("app.revanced.android.youtube");
            module.setApkSignatureBlock(null);
            module.removeInputSource("assets/dexopt/baseline.prof");
            module.removeInputSource("assets/dexopt/baseline.profm");
            module.refreshManifest(); module.refreshTable();
            module.writeApk(new File(args[1]));
        }
    }
}
