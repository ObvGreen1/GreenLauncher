package me.obvgreen;

import com.google.gson.JsonObject;
import me.obvgreen.core.account.Account;
import me.obvgreen.core.account.AccountStore;
import me.obvgreen.core.download.VersionJson;
import me.obvgreen.core.java.JavaRuntimeManager;
import me.obvgreen.core.mojang.MojangApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Core logic tests: maven paths, version rules, java heuristics, offline accounts, live Mojang manifest. */
class CoreLogicTest {

    @Test
    void mavenPathBuildsCorrectRelativePath() {
        assertEquals("com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                VersionJson.mavenPath("com.mojang:brigadier:1.0.18", null, "jar"));
        assertEquals("org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar",
                VersionJson.mavenPath("org.lwjgl:lwjgl:3.3.1", "natives-windows", "jar"));
String s = VersionJson.mavenPath("org.slf4j:slf4j-api:2.0.9", null, "jar");
        assertEquals("org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar", s);
    }

    @Test
    void libraryRulesRespectWindows() {
        JsonObject allow = com.google.gson.JsonParser.parseString("""
                {"name":"x:y:1","rules":[{"action":"allow","os":{"name":"windows"}}]}
                """).getAsJsonObject();
        JsonObject disallow = com.google.gson.JsonParser.parseString("""
                {"name":"x:y:1","rules":[{"action":"allow","os":{"name":"osx"}}]}
                """).getAsJsonObject();
        JsonObject noRules = com.google.gson.JsonParser.parseString("""
                {"name":"x:y:1"}
                """).getAsJsonObject();
        assertTrue(VersionJson.appliesToWindows(allow));
        assertFalse(VersionJson.appliesToWindows(disallow));
        assertTrue(VersionJson.appliesToWindows(noRules));
    }

    @Test
    void nativesClassifierReplacesArch() {
        JsonObject lib = com.google.gson.JsonParser.parseString("""
                {"name":"x:y:1","natives":{"windows":"natives-windows-${arch}"}}
                """).getAsJsonObject();
        assertEquals("natives-windows-64", VersionJson.windowsNativesClassifier(lib));
    }

    @Test
    void gameArgsSupportOldAndNewFormat() {
        JsonObject old = com.google.gson.JsonParser.parseString("""
                {"minecraftArguments":"--username ${auth_player_name} --version ${version_name}"}
                """).getAsJsonObject();
        assertEquals(List.of("--username", "${auth_player_name}", "--version", "${version_name}"),
                VersionJson.gameArgs(old));

        JsonObject neu = com.google.gson.JsonParser.parseString("""
                {"arguments":{"game":["--username","${auth_player_name}",
                    {"rules":[{"action":"allow","os":{"name":"osx"}}],"value":["--macflag"]}],"minecraftArguments":""}
                }
                """).getAsJsonObject();
        var args = VersionJson.gameArgsConditional(neu);
        assertEquals(List.of("--username", "${auth_player_name}"), args);
    }

    @Test
    void requiredMajorFollowsKnownVersions() {
        assertEquals(8, JavaRuntimeManager.requiredMajor(null, "1.12.2"));
        assertEquals(8, JavaRuntimeManager.requiredMajor(null, "1.16.5"));
        assertEquals(16, JavaRuntimeManager.requiredMajor(null, "1.17"));
        assertEquals(17, JavaRuntimeManager.requiredMajor(null, "1.18.2"));
        assertEquals(17, JavaRuntimeManager.requiredMajor(null, "1.20.4"));
        assertEquals(21, JavaRuntimeManager.requiredMajor(null, "1.20.5"));
        assertEquals(21, JavaRuntimeManager.requiredMajor(null, "1.21"));
        // json wins over heuristic
        JsonObject v = com.google.gson.JsonParser.parseString("""
                {"javaVersion":{"majorVersion":17}}
                """).getAsJsonObject();
        assertEquals(17, JavaRuntimeManager.requiredMajor(v, "1.12.2"));
    }

    @Test
    void offlineUuidMatchesVanillaFormula() {
        Account a = Account.offline("Notch");
        // vanilla: UUID.nameUUIDFromBytes("OfflinePlayer:Notch")
        var expected = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes()).toString().replace("-", "");
        assertEquals(expected, a.uuid);
        assertEquals("Notch", a.username);
    }

    @Test
    void offlineAccountValidation() {
        assertThrows(IllegalArgumentException.class, () -> AccountStore.addOffline("  "));
        assertThrows(IllegalArgumentException.class, () -> AccountStore.addOffline("ThisNameIsWayTooLong"));
    }

    @Test
    void liveMojangManifestIsParseable() throws Exception {
        List<me.obvgreen.core.mojang.VersionEntry> all = MojangApi.manifest();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(v -> v.id.equals("1.20.1") && v.type.equals("release")));
        assertNotNull(MojangApi.find("1.20.1").url);
        // newest release is first and is a release
        assertTrue(all.stream().anyMatch(v -> v.type.equals("release")));
        // filtered returns only requested types
        var releases = MojangApi.filtered(List.of(me.obvgreen.core.mojang.VersionType.RELEASE));
        assertTrue(releases.stream().allMatch(v -> v.type.equals("release")));
        assertFalse(releases.isEmpty());
    }

    @Test
    void coordClassifierExtractsFourthSegment() {
        assertEquals("natives-windows", VersionJson.coordClassifier("org.lwjgl:lwjgl:3.3.3:natives-windows"));
        assertNull(VersionJson.coordClassifier("com.mojang:brigadier:1.3.10"));
    }

    @Test
    void featureGatedRulesDoNotApply() {
        // e.g. the --demo arg and quickPlay args are feature-gated; they must be skipped
        JsonObject demo = com.google.gson.JsonParser.parseString("""
                {"name":"x:y:1","rules":[{"action":"allow","features":{"is_demo_user":true}}]}
                """).getAsJsonObject();
        assertFalse(VersionJson.appliesToWindows(demo));
        JsonObject quickPlay = com.google.gson.JsonParser.parseString("""
                {"name":"x:y:1","rules":[{"action":"allow","features":{"has_quick_play_support":true}}]}
                """).getAsJsonObject();
        assertFalse(VersionJson.appliesToWindows(quickPlay));
    }

    @Test
    void liveFabricLatestLoaderResolves() throws Exception {
        String v = me.obvgreen.core.loader.FabricLoader.latestLoader();
        assertTrue(v.matches("\\d+\\.\\d+\\.\\d+.*"), "unexpected fabric loader version: " + v);
    }
}
