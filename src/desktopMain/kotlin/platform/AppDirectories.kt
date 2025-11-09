package platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Provides platform-specific application directories for storing user data.
 *
 * On macOS: ~/Library/Application Support/MtgPirate
 * On Windows: %APPDATA%/MtgPirate
 * On Linux: ~/.local/share/MtgPirate
 */
object AppDirectories {
    private const val APP_NAME = "MtgPirate"

    /**
     * Returns the platform-specific application data directory.
     * Creates the directory if it doesn't exist.
     */
    val dataDir: Path by lazy {
        val dir = when {
            isMacOS() -> {
                // macOS: ~/Library/Application Support/AppName
                val home = System.getProperty("user.home")
                Path.of(home, "Library", "Application Support", APP_NAME)
            }
            isWindows() -> {
                // Windows: %APPDATA%/AppName
                val appData = System.getenv("APPDATA")
                    ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming").toString()
                Path.of(appData, APP_NAME)
            }
            else -> {
                // Linux/Unix: ~/.local/share/AppName
                val home = System.getProperty("user.home")
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                    ?: Path.of(home, ".local", "share").toString()
                Path.of(xdgDataHome, APP_NAME)
            }
        }

        // Create directory if it doesn't exist
        if (!dir.exists()) {
            Files.createDirectories(dir)
        }

        dir
    }

    /**
     * Returns the platform-specific directory for exports.
     * On all platforms, defaults to the user's Documents folder.
     */
    val exportsDir: Path by lazy {
        val dir = when {
            isMacOS() -> {
                val home = System.getProperty("user.home")
                Path.of(home, "Documents", APP_NAME)
            }
            isWindows() -> {
                val userProfile = System.getProperty("user.home")
                Path.of(userProfile, "Documents", APP_NAME)
            }
            else -> {
                // Linux: try XDG_DOCUMENTS_DIR or fallback to ~/Documents
                val home = System.getProperty("user.home")
                val xdgDocs = System.getenv("XDG_DOCUMENTS_DIR")
                    ?: Path.of(home, "Documents").toString()
                Path.of(xdgDocs, APP_NAME)
            }
        }

        // Create directory if it doesn't exist
        if (!dir.exists()) {
            Files.createDirectories(dir)
        }

        dir
    }

    private fun isMacOS(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return "mac" in os || "darwin" in os
    }

    private fun isWindows(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return "win" in os
    }
}

