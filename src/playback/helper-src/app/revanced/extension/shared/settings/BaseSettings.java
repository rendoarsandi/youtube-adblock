package app.revanced.extension.shared.settings;
public final class BaseSettings {
 public static final Flag SPOOF_VIDEO_STREAMS=new Flag(true), DEBUG=new Flag(false), DEBUG_TOAST_ON_ERROR=new Flag(false), SPOOF_STREAMING_DATA_STATS_FOR_NERDS=new Flag(false);
 public static final class Flag { private final boolean value; Flag(boolean v) { value=v; } public boolean get() { return value; } }
}