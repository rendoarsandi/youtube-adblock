/* Local compiled fingerprint adaptation, Codex, 2026-10-28.
 * Used with anddea/revanced-patches v4.4.0-dev.3, GPL-3.0.
 * Preserves both earlier and later diagnostic layouts without reordering filters.
 */
package app.morphe.patches.local;
import app.morphe.patcher.InstructionLocation;
import app.morphe.patcher.InstructionFilterKt;
import app.morphe.patcher.StringFilter;
public final class ContentProviderDiagnosticAnywhere implements InstructionLocation {
 public boolean getSearchesBackwards() { return false; }
 public int searchStartIndex(int previousIndex,int instructionsSize) { return 0; }
 public boolean indexIsValidForMatching(int previousIndex,int instructionIndex) { return true; }
 public static StringFilter stringAnywhere(String value,InstructionLocation unused,int defaults,Object marker) {
  if(!value.equals("ContentProvider query returned null cursor") || unused!=null || defaults!=2 || marker!=null)
   throw new IllegalArgumentException("Unexpected content-provider fingerprint string factory");
  return InstructionFilterKt.string(value,new ContentProviderDiagnosticAnywhere());
 }
}
