package app.revanced.extension.shared.spoof;
public final class StandaloneDefaults {
 static { initialize(); }
 public static void initialize() {
  SpoofVideoStreamsPatch.setClientsToUse(java.util.Arrays.asList(ClientType.ANDROID_REEL,ClientType.ANDROID_VR_1_43_32,ClientType.VISIONOS,ClientType.ANDROID_CREATOR),ClientType.ANDROID_REEL);
 }
 public static String filterPlayerRequest(String url, java.util.Map<String,String> headers) {
  SpoofVideoStreamsPatch.fetchStreams(url,headers);
  url=SpoofVideoStreamsPatch.blockGetAttRequest(url);
  android.net.Uri uri=SpoofVideoStreamsPatch.blockGetWatchRequest(android.net.Uri.parse(url));
  return uri.toString();
 }
}