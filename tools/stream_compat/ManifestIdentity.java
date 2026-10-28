import com.reandroid.apk.ApkModule;
import java.io.File;
public final class ManifestIdentity {
 public static void main(String[] a) throws Exception {
  try (ApkModule module=ApkModule.loadApkFile(new File(a[0]))) {
   var manifest=module.getAndroidManifest();
   if(!manifest.getPackageName().equals(a[2]))throw new IllegalStateException("Unexpected package");
   manifest.ensureFullClassNames();manifest.setPackageName(a[3]);
   module.setApkSignatureBlock(null);module.removeInputSource("stamp-cert-sha256");
   for(var input:module.listInputSources())if(input.getAlias().matches("META-INF/(?:MANIFEST\\.MF|[^/]+\\.(?:SF|RSA|DSA|EC))"))module.removeInputSource(input.getAlias());
   for(var tablePackage:module.getTableBlock())if(tablePackage.getName().equals(a[2]))tablePackage.setName(a[3]);
   module.refreshManifest();module.refreshTable();module.writeApk(new File(a[1]));
  }
 }
}
