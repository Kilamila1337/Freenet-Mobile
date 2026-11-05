package org.freenetproject.mobile;

import org.freenetproject.mobile.installer.InstallerException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Responsible for installing the node into the node directory.
 *
 * It creates the node directory, a freenet.ini configuration file and copies the bundled
 * seednodes and bookmarks into the node directory. The configuration file is generated
 * from the current wrapper defaults (which track fred's own defaults), overlaid with the
 * settings bundled with this application.
 */
public class Installer {
    /** File in which the node configuration is persisted. */
    private static final String PROPERTY_FILE = "freenet.ini";

    private static Installer instance = null;

    /** Node directory. Only known after install(). */
    private Path nodeDir = null;

    public static Installer getInstance() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    /**
     * @return The path to the freenet.ini file of the node, or null if it is not installed yet.
     */
    public String getFreenetIniPath() {
        return nodeDir == null ? null
                : Paths.get(nodeDir.toString(), PROPERTY_FILE).toString();
    }

    /**
     * Whether the node appears to be installed either in the last used node directory
     * or in the application's "data" directory.
     */
    public boolean isInstalled() {
        if (nodeDir != null) {
            return new File(nodeDir.toFile(), PROPERTY_FILE).exists();
        }
        Path defaultDir = Paths.get(App.getInstance().getFilesDir().getAbsolutePath(), "data");
        return new File(defaultDir.toFile(), PROPERTY_FILE).exists();
    }

    /**
     * Install the node into the given directory. Creates the node directory, a freenet.ini
     * configuration file and copies the bundled files into the node directory.
     *
     * Re-running install() on an installed node overwrites the configuration file, so it
     * should only be called when the node is not installed yet.
     *
     * @param path Path to the directory to install the node into.
     * @param seednodes Seednodes for the node to bootstrap from.
     * @param freenetIni Node configuration defaults bundled with the application.
     * @param bookmarks Bookmarks for the fproxy welcomepage.
     * @param language Locale's display language of the device.
     */
    public void install(String path, InputStream seednodes, InputStream freenetIni,
                        InputStream bookmarks, String language) throws InstallerException {
        try {
            nodeDir = Paths.get(path);
            File dir = nodeDir.toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new InstallerException("Failed to create node directory: " + path);
            }

            putNodeConfiguration(freenetIni, language);
            copyFile("seednodes.fref", seednodes);
            copyFile("bookmarks.dat", bookmarks);
        } catch (IOException e) {
            throw new InstallerException("Failed to install node: " + e, e);
        }
    }

    /**
     * Creates a freenet.ini file from the current wrapper defaults with the node
     * installation paths (node.install.*) and logging already pointing to the node
     * directory, overlaid with the settings bundled with the application.
     *
     * @param freenetIni Configuration defaults bundled with the application.
     * @param language Locale's display language of the device.
     */
    private void putNodeConfiguration(InputStream freenetIni, String language) throws IOException {
        // Load the wrapper defaults, which create all node.install.* paths under the
        // node directory among other required settings.
        NodeController nodeController = new NodeControllerImpl(nodeDir);
        String cfgDir = nodeController.getConfig("node.install.cfgDir");

        Map<String, String> config = new LinkedHashMap<>();
        try (InputStream ini = new FileInputStream(Paths.get(cfgDir, PROPERTY_FILE).toFile())) {
            config.putAll(parse(ini));
        }

        // Merge in the settings bundled with the application (fproxy, bandwidth
        // limits, etc.), which win over the defaults.
        config.putAll(parse(freenetIni));

        // Every install gets its own random SSL keystore password rather than the
        // constant placeholder in the bundled defaults.
        String password = randomPassword();
        config.put("ssl.sslKeyStorePass", password);
        config.put("ssl.sslKeyPass", password);

        // Localized UI language for the fproxy user interface, when recognizable.
        String languageCode = localeCode(language);
        if (languageCode != null) {
            config.put("freenet.l10n.language", languageCode);
        }

        writeFile(Paths.get(cfgDir, PROPERTY_FILE).toFile(), config);
    }

    /**
     * @param language Locale's display language ("Deutsch" for example).
     * @return Two letter language code ("de" for example), or null if not recognized.
     */
    static String localeCode(String language) {
        if (language == null || language.isEmpty()) {
            return null;
        }
        for (Locale locale : Locale.getAvailableLocales()) {
            if (locale.getDisplayLanguage().equalsIgnoreCase(language)
                    || locale.getLanguage().equalsIgnoreCase(language)) {
                return locale.getLanguage();
            }
        }
        return null;
    }

    /** @return A random 64 character password for the SSL keystores. */
    static String randomPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Copies a file into the node directory.
     *
     * @param filename File name to save as.
     * @param file Actual file to copy.
     */
    private void copyFile(String filename, InputStream file) throws IOException {
        File dest = new File(nodeDir.toFile(), filename);
        OutputStream outStream = null;
        try {
            outStream = new FileOutputStream(dest);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = file.read(buffer)) > 0) {
                outStream.write(buffer, 0, read);
            }
        } finally {
            try {
                file.close();
            } catch (IOException ignored) {
            }
            if (outStream != null) {
                outStream.close();
            }
        }
    }

    /**
     * Parses an INI-like input stream into a key/value map.
     *
     * @param ini INI-like file.
     * @return Mapping of configuration keys to values.
     */
    static Map<String, String> parse(InputStream ini) throws IOException {
        Map<String, String> properties = new LinkedHashMap<>();
        try {
            java.util.Scanner scanner = new java.util.Scanner(ini, "UTF-8");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                int sep = line.indexOf('=');
                if (sep == -1 || line.startsWith("#") || line.startsWith(",")) {
                    continue;
                }
                String key = line.substring(0, sep).trim();
                String value = line.length() > sep + 1 ? line.substring(sep + 1).trim() : "";
                properties.put(key, value);
            }
        } finally {
            try {
                ini.close();
            } catch (IOException ignored) {
            }
        }
        return properties;
    }

    /**
     * Writes the given configuration to file in freenet.ini format (key=value lines
     * without escaping, closed by an "End" line).
     *
     * @param file File to write to.
     * @param config Configuration keys and values.
     */
    static void writeFile(File file, Map<String, String> config) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue())
              .append(System.lineSeparator());
        }
        sb.append("End").append(System.lineSeparator());
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
