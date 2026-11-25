package com.socialpilot.app.data.security;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Encrypted key files live in shared preferences. Keystore keys never leave the device, so a
 * restored ciphertext would be unreadable anyway, but the files must not leave the device either.
 */
public final class BackupExclusionTest {
    private static final String[] DOMAINS = {"root", "file", "database", "sharedpref", "external"};

    @Test public void manifestDisablesBackupAndPointsToExclusionRules() throws Exception {
        Element application = (Element) parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0);
        assertEquals("false", application.getAttribute("android:allowBackup"));
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"));
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"));
    }

    @Test public void legacyFullBackupExcludesEveryDomain() throws Exception {
        assertExcludesAll(parse("src/main/res/xml/backup_rules.xml").getDocumentElement());
    }

    @Test public void cloudBackupAndDeviceTransferExcludeEveryDomain() throws Exception {
        Document rules = parse("src/main/res/xml/data_extraction_rules.xml");
        for (String section : new String[] {"cloud-backup", "device-transfer"}) {
            NodeList nodes = rules.getElementsByTagName(section);
            assertEquals(section, 1, nodes.getLength());
            assertExcludesAll((Element) nodes.item(0));
        }
    }

    private static void assertExcludesAll(Element parent) {
        assertEquals("No include rules may re-add data", 0, parent.getElementsByTagName("include").getLength());
        Set<String> excluded = new HashSet<>();
        NodeList excludes = parent.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element exclude = (Element) excludes.item(i);
            if (".".equals(exclude.getAttribute("path"))) excluded.add(exclude.getAttribute("domain"));
        }
        for (String domain : DOMAINS) assertTrue(domain, excluded.contains(domain));
    }

    private static Document parse(String path) throws Exception {
        File file = new File(path);
        assertTrue("Run from the app module: " + file.getAbsolutePath(), file.isFile());
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    }
}
