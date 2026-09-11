package dev.stray.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Latest version from stray.gay, GitHub Releases, and repo latest.json.
 */
public final class UpdateMeta {
	public static final String SHOP = "https://stray.gay";
	static final String META = SHOP + "/api/mod";
	static final String GITHUB_META =
		"https://raw.githubusercontent.com/camberX/Stray/main/web/public/mod/latest.json";
	static final String EISENMANN_GITHUB_META =
		"https://raw.githubusercontent.com/camberX/Eisenmann/main/web/public/mod/latest.json";
	static final String LEGACY_GITHUB_META =
		"https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/latest.json";
	private static final String GITHUB_RELEASE =
		"https://api.github.com/repos/camberX/Stray/releases/latest";
	private static final String LEGACY_GITHUB_RELEASE =
		"https://api.github.com/repos/camberX/voidmark/releases/latest";
	private static final String[] META_URLS = {
		META,
		GITHUB_META,
		EISENMANN_GITHUB_META,
		LEGACY_GITHUB_META
	};
	private static final String[] RELEASE_URLS = {
		GITHUB_RELEASE,
		LEGACY_GITHUB_RELEASE
	};

	private UpdateMeta() {
	}

	public static String latestVersion() {
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(6))
			.build();
		String best = null;
		for (String url : RELEASE_URLS) {
			best = newer(best, versionFromRelease(getJson(http, url, 12, "application/vnd.github+json")));
		}
		for (String url : META_URLS) {
			best = newer(best, versionFromMeta(getJson(http, url, 12)));
		}
		return best;
	}

	static JsonObject getJson(HttpClient http, String url, int timeoutSec) {
		return getJson(http, url, timeoutSec, "application/json");
	}

	static JsonObject getJson(HttpClient http, String url, int timeoutSec, String accept) {
		try {
			HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create(cacheBust(url)))
					.timeout(Duration.ofSeconds(timeoutSec))
					.header("User-Agent", "Stray-Update (https://github.com/camberX/Stray)")
					.header("Accept", accept)
					.header("Cache-Control", "no-cache")
					.header("Pragma", "no-cache")
					.GET()
					.build(),
				HttpResponse.BodyHandlers.ofString()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception ignored) {
			return null;
		}
	}

	public static int compare(String left, String right) {
		int[] a = parts(left);
		int[] b = parts(right);
		int n = Math.max(a.length, b.length);
		for (int i = 0; i < n; i++) {
			int av = i < a.length ? a[i] : 0;
			int bv = i < b.length ? b[i] : 0;
			if (av != bv) {
				return Integer.compare(av, bv);
			}
		}
		return 0;
	}

	static String cacheBust(String url) {
		if (url == null || url.isBlank()) {
			return url;
		}
		return url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
	}

	private static String newer(String best, String candidate) {
		if (candidate == null || candidate.isBlank()) {
			return best;
		}
		if (best == null || compare(candidate, best) > 0) {
			return candidate;
		}
		return best;
	}

	private static String versionFromMeta(JsonObject json) {
		if (json == null || !json.has("version")) {
			return null;
		}
		String version = json.get("version").getAsString().trim();
		return version.isEmpty() ? null : version;
	}

	private static String versionFromRelease(JsonObject json) {
		if (json == null) {
			return null;
		}
		String tag = "";
		if (json.has("tag_name") && !json.get("tag_name").isJsonNull()) {
			tag = json.get("tag_name").getAsString().trim();
		}
		if (tag.isEmpty() && json.has("name") && !json.get("name").isJsonNull()) {
			tag = json.get("name").getAsString().trim();
		}
		if (tag.startsWith("v") || tag.startsWith("V")) {
			tag = tag.substring(1).trim();
		}
		return tag.isEmpty() ? null : tag;
	}

	private static int[] parts(String version) {
		String[] bits = version == null ? new String[0] : version.split("[^0-9]+");
		int[] out = new int[Math.max(1, bits.length)];
		int n = 0;
		for (String bit : bits) {
			if (bit.isEmpty()) {
				continue;
			}
			try {
				out[n++] = Integer.parseInt(bit);
			} catch (NumberFormatException ignored) {
			}
		}
		if (n == out.length) {
			return out;
		}
		int[] trimmed = new int[n];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
	}
}
