package org.jboss.qa.hornetq.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

/**
 * Utility class for server path discovery independent on server version.
 *
 * Works for EAP 6 only.
 */
public class ServerPathUtils {

    private static final String EAP_60_MODULES_ROOT = "modules";
    private static final String EAP_6x_MODULES_ROOT = "modules/system/layers/base".replaceAll("/", File.separator);
    private static final String CP_OVERLAYS_DIR = EAP_6x_MODULES_ROOT + File.separator + ".overlays";
    private static final String CP_ACTIVE_OVERLAY_FILE = CP_OVERLAYS_DIR + File.separator + ".overlays";

    /**
     * Returns path to given module root directory (including "main" directory).
     *
     * Abstracts user away from eap version and potential patch overlay installed.
     *
     * @param eapHome EAP root directory
     * @param modulePath Module path (e.g. "org/hornetq")
     * @return File with module directory path (e.g. ".../org/hornetq/main" directory)
     *
     * @throws IOException
     */
    public static File getModuleDirectory(final String eapHome, final String modulePath) throws IOException {
        EapVersion version = EapVersion.fromEapVersionFile(eapHome);
        File activeOverlay = ServerPathUtils.getOverlayDirectory(eapHome);
        File moduleOverlayDir = new File(activeOverlay, modulePath);

        if (version.isCpRelease() && moduleOverlayDir.exists()) {
            return new File(moduleOverlayDir, "main");
        } else {
            String moduleMainDir = (modulePath + "/main").replaceAll("/", File.separator);
            return new File(ServerPathUtils.modulesRootDirectory(eapHome), moduleMainDir);
        }
    }

    /**
     * Returns path to the directory with module tree root.
     *
     * Always returns base modules root directory, not overlay root if there's any patch installed.
     *
     * @param eapHome EAP root directory
     * @return File object with directory with modules root.
     *
     * @throws IOException
     */
    public static File modulesRootDirectory(final String eapHome) throws IOException {
        File eapHomeDir = new File(eapHome);
        EapVersion eapVersion = EapVersion.fromEapVersionFile(eapHomeDir);
        if (eapVersion.compareToString("6.0.1") <= 0) {
            return new File(eapHomeDir, EAP_60_MODULES_ROOT);
        } else {
            return new File(eapHomeDir, EAP_6x_MODULES_ROOT);
        }
    }

    /**
     * Returns path to the directory with active overlay in patched EAP.
     *
     * @param eapHome EAP root directory
     * @return File object with directory with active overlay root, or null if there is no patch installed.
     *
     * @throws IOException
     */
    public static File getOverlayDirectory(final String eapHome) throws IOException {
        File eapHomeDir = new File(eapHome);
        EapVersion eapVersion = EapVersion.fromEapVersionFile(eapHomeDir);

        String activeOverlay = getActiveOverlay(eapHomeDir, eapVersion);
        if (activeOverlay != null) {
            return new File(eapHome + File.separator + CP_OVERLAYS_DIR, activeOverlay);
        } else {
            return null;
        }
    }

    private static String getActiveOverlay(final File eapHome, final EapVersion version) throws FileNotFoundException {
        if (version.isCpRelease()) {
            Scanner scanner = new Scanner(new FileInputStream(new File(eapHome, CP_ACTIVE_OVERLAY_FILE)));
            return scanner.nextLine();
        } else {
            return null;
        }
    }

}
