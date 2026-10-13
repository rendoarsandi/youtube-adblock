import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.ValueType;

/** Exact-version resource changes to dedicated companion-ad views.
 * Keeps the mixed companion parent, other stubs and shared element layout intact.
 */
public final class CompanionLayouts {
    private static final int HEIGHT = 0x010100f5;
    private static final int VISIBILITY = 0x010100dc;
    private static final int LAYOUT = 0x010100f2;
    private static final int INFLATED_ID = 0x010100f3;

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void guard(ApkModule module, ZipFile original, String path, String expected) throws Exception {
        ZipEntry entry = original.getEntry(path);
        require(entry != null, "Missing original companion layout: " + path);
        try (InputStream input = original.getInputStream(entry)) {
            require(expected.equals(digest(input.readAllBytes())), "Original companion layout changed: " + path);
        }
        require(module.getInputSource(path) != null, "Missing merged companion layout: " + path);
        try (InputStream input = module.getInputSource(path).openStream()) {
            require(expected.equals(digest(input.readAllBytes())), "Merged companion layout changed: " + path);
        }
    }

    private static void zeroHeight(ResXmlElement element) {
        ResXmlAttribute height = element.searchAttributeByResourceId(HEIGHT);
        require(height != null && height.getData() == -2, "Unexpected companion layout height");
        height.setValueType(ValueType.DEC);
        height.setData(0);
    }

    private static void store(ApkModule module, String path, ResXmlDocument document) {
        document.refreshFull();
        module.add(new ByteInputSource(document.getBytes(), path));
    }

    public static void patch(ApkModule module, ZipFile originalZip) throws Exception {
        String cardPath = "res/QWG.xml";
        String parentPath = "res/QgJ.xml";
        guard(module, originalZip, cardPath,
            "0c30d05ad47558f387f71155bd4f935f04560d8ab19db4ea1ac8f71d6468c961");
        guard(module, originalZip, parentPath,
            "2bfa1cd62082c06e70ac5204d27c9c2b77eb9ffde0639b1342d86833bc5383bd");

        ResXmlDocument card = module.loadResXmlDocument(cardPath);
        ResXmlElement root = card.getDocumentElement();
        require("LinearLayout".equals(root.getName()) && root.getIdAttribute() != null
            && root.getIdAttribute().getData() == 0x7f0b00ce, "Unexpected dedicated ad-card root");
        zeroHeight(root);
        ResXmlAttribute visibility = root.getOrCreateAndroidAttribute("visibility", VISIBILITY);
        visibility.setValueType(ValueType.DEC);
        visibility.setData(8);

        ResXmlDocument parent = module.loadResXmlDocument(parentPath);
        int found = 0;
        Iterator<ResXmlElement> elements = parent.getDocumentElement().recursiveElements();
        while (elements.hasNext()) {
            ResXmlElement element = elements.next();
            ResXmlAttribute id = element.getIdAttribute();
            if (id == null) continue;
            int resource = id.getData();
            if (resource != 0x7f0b00cf && resource != 0x7f0b06fd) continue;
            require("ViewStub".equals(element.getName()), "Expected dedicated companion ViewStub");
            ResXmlAttribute layout = element.searchAttributeByResourceId(LAYOUT);
            ResXmlAttribute inflated = element.searchAttributeByResourceId(INFLATED_ID);
            int expectedLayout = resource == 0x7f0b00cf ? 0x7f0e0045 : 0x7f0e0224;
            int expectedInflated = resource == 0x7f0b00cf ? 0x7f0b00cd : 0x7f0b06fc;
            require(layout != null && layout.getData() == expectedLayout
                && inflated != null && inflated.getData() == expectedInflated,
                "Unexpected dedicated companion stub target");
            zeroHeight(element);
            found |= resource == 0x7f0b00cf ? 1 : 2;
        }
        require(found == 3, "Both dedicated companion stubs must exist");
        store(module, cardPath, card);
        store(module, parentPath, parent);
    }
}
