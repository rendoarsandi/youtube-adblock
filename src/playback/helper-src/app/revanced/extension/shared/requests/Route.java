package app.revanced.extension.shared.requests;
public final class Route {
 public enum Method { POST }
 private final Method method; private final String path;
 public Route(Method m,String p) { method=m;path=p; }
 public CompiledRoute compile() { return new CompiledRoute(method,path); }
 public static final class CompiledRoute { public final Method method; public final String path; CompiledRoute(Method m,String p) {method=m;path=p;} }
}