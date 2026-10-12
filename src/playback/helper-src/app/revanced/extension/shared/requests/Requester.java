package app.revanced.extension.shared.requests;
import java.net.*; import java.io.*;
public final class Requester {
 public static HttpURLConnection getConnectionFromCompiledRoute(String base, Route.CompiledRoute route) throws IOException {
  if (!base.equals("https://youtubei.googleapis.com/youtubei/v1/")) throw new IOException("Unexpected compatibility endpoint");
  HttpURLConnection c=(HttpURLConnection)new URL(base+route.path).openConnection(); c.setRequestMethod(route.method.name()); return c;
 }
}