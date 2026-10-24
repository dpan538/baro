package studio.sundown.weather

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Opt-in controlled transport tests. Production URLs, signature checks and the
 * complete download/persistence path stay intact. This is not public delivery. */
class NativeUpdateFlowTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = "https://github.com/dpan538/baro/releases/download/controlled-flow/"
    private val pool = Executors.newSingleThreadExecutor()
    private val routes = mutableMapOf<String, () -> HttpURLConnection>()
    private fun connection(url: URL) = routes[url.toString()]?.invoke() ?: error("Unexpected URL $url")
    private fun response(url: String, code: Int = 200, location: String? = null, stream: () -> InputStream): HttpURLConnection =
        object : HttpURLConnection(URL(url)) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = code
            override fun getHeaderField(name: String?) = if (name == "Location") location else null
            override fun getInputStream() = stream()
        }
    private fun text(url: String, value: String, code: Int = 200) {
        routes[url] = { response(url, code) { value.byteInputStream() } }
    }
    private fun fixture(): Pair<File, Release> {
        val file = File(ctx.getExternalFilesDir(null), "update-fixtures/candidate.apk")
        assertTrue("Stage a newer same-signer APK before this opt-in test", file.isFile)
        val info = ctx.packageManager.getPackageArchiveInfo(file.path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!
        val signer = sha256(info.signingInfo!!.apkContentsSigners.single().toByteArray())
        return file to Release(info.longVersionCode, info.versionName!!, fileHash(file), signer, file.length(), base + "candidate.apk", "Controlled download", true)
    }
    private fun published(release: Release, name: String = "baro-update.json", draft: Boolean = false, preview: Boolean = true, hasManifest: Boolean = true): JSONObject =
        JSONObject().put("draft", draft).put("prerelease", preview).put("body", "Release notes").put("assets", JSONArray()
            .put(JSONObject().put("name", if(hasManifest) "baro-update.json" else "README.md").put("browser_download_url", base + name))
            .put(JSONObject().put("name", "candidate.apk").put("browser_download_url", release.url)))
    private fun checked(updates: Updates, preview: Boolean = true): Pair<Release?, String> {
        val latch = CountDownLatch(1);var result: Release? = null;var status = ""
        updates.check(preview) { r, s -> result = r; status = s; latch.countDown() }
        assertTrue("Update discovery timed out", latch.await(15, TimeUnit.SECONDS))
        return result to status
    }
    @Test fun catalogueChannelsHighestCodeIncompleteAndMalformedReleaseRecovery() {
        val (_, release) = fixture();val updates = Updates(ctx, pool, ::connection)
        val endpoint = "https://api.github.com/repos/dpan538/baro/releases?per_page=30"
        try {
            text(base + "baro-update.json", release.json().toString())
            text(base + "older.json", release.copy(code = release.code - 1).json().toString())
            text(base + "broken.json", "{invalid")
            text(endpoint, JSONArray().put(published(release, "broken.json")).put(published(release.copy(code = release.code - 1), "older.json")).put(published(release)).toString())
            assertEquals(release.code, checked(updates).first!!.code)
            text(endpoint, JSONArray().put(published(release, draft = true)).toString())
            assertEquals("current", checked(updates).second)
            text(endpoint, "[]");assertEquals("empty", checked(updates).second)
            text(endpoint, JSONArray().put(published(release, "README.md", hasManifest = false)).toString())
            assertEquals("unversioned", checked(updates).second)
            text(endpoint, JSONArray().put(published(release, "broken.json")).toString())
            assertTrue(checked(updates).second.startsWith("error:"))
            text(base + "baro-update.json", release.copy(code = BuildConfig.VERSION_CODE.toLong()).json().toString())
            text(endpoint, JSONArray().put(published(release)).toString());assertEquals("current", checked(updates).second)
            val stable = endpoint.substringBefore('?') + "/latest"
            text(stable, published(release, preview = true).toString());assertNull(checked(updates, false).first)
            text(base + "baro-update.json", release.json().toString())
            text(stable, published(release, preview = false).toString());assertFalse(checked(updates, false).first!!.preview)
            text(stable, "", 404);assertEquals("empty", checked(updates, false).second)
        } finally { updates.cancel();pool.shutdownNow() }
    }
    @Test fun redirectedDownloadVerifiesBytesPersistsAcrossRestartAndRejectsTampering() {
        val (file, release) = fixture();val updates = Updates(ctx, pool, ::connection)
        val dir = File(ctx.cacheDir, "updates");dir.listFiles()?.forEach { it.delete() }
        val redirected = "https://release-assets.githubusercontent.com/controlled/candidate.apk"
        routes[release.url] = { response(release.url, 302, redirected) { "".byteInputStream() } }
        routes[redirected] = { response(redirected) { file.inputStream() } }
        fun download(r: Release): Pair<String, List<Int>> {
            val latch = CountDownLatch(1);var final = "";val percentages = mutableListOf<Int>()
            updates.download(r) { p, state ->
                if (state == "downloading") percentages.add(p) else { final = state;latch.countDown() }
            }
            assertTrue(latch.await(40, TimeUnit.SECONDS));return final to percentages.toList()
        }
        try {
            assertTrue(download(release.copy(sha = "0".repeat(64))).first.startsWith("error:"))
            assertNull(updates.pending());assertFalse(updates.apk.exists())
            val (status, progress) = download(release)
            assertEquals("verified", status);assertTrue(progress.size > 2);assertEquals(100, progress.last())
            assertTrue(progress.zipWithNext().all { it.first <= it.second })
            assertEquals(release.sha, fileHash(updates.apk))
            val restored = Updates(ctx, pool, ::connection)
            assertEquals(release.code, restored.pending()!!.code)
            File(dir, "pending.json").writeText(release.copy(version = "wrong").json().toString())
            assertNull(restored.pending())
            File(dir, "pending.json").writeText(release.json().toString())
            assertNotNull(restored.pending())
            routes[release.url] = { response(release.url, 302, "http://example.com/candidate.apk") { "".byteInputStream() } }
            assertTrue(download(release).first.startsWith("error:"))
            assertNotNull(updates.pending()) // Failed replacement retains a verified candidate.
            assertFalse(dir.listFiles()!!.any { it.extension == "part" })
        } finally { updates.cancel();dir.listFiles()?.forEach { it.delete() };pool.shutdownNow() }
    }
    @Test fun cancellingBlockedDownloadDiscardsPartialAndSuppressesLateCallback() {
        val (_, release) = fixture();val updates = Updates(ctx, pool, ::connection)
        val entered = CountDownLatch(1);val stop = CountDownLatch(1);val callback = CountDownLatch(1)
        val dir = File(ctx.cacheDir, "updates");dir.listFiles()?.forEach { it.delete() }
        routes[release.url] = { object : HttpURLConnection(URL(release.url)) {
            override fun connect() {}
            override fun disconnect() { stop.countDown() }
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream() = object : InputStream() {
                override fun read(): Int { entered.countDown();stop.await(10, TimeUnit.SECONDS);return -1 }
            }
        } }
        try {
            updates.download(release) { _, _ -> callback.countDown() }
            assertTrue(entered.await(10, TimeUnit.SECONDS));updates.cancel()
            assertFalse("Cancelled generation must not update the UI", callback.await(800, TimeUnit.MILLISECONDS))
            assertFalse(dir.listFiles()!!.any { it.extension == "part" });assertNull(updates.pending())
        } finally { updates.cancel();stop.countDown();pool.shutdownNow() }
    }
}
