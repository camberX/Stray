package dev.stray.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared stray.gay / GitHub latest-version lookup for auto-update and chat notify.
 */
public final class UpdateMeta {
	public static final String SHOP = "https://stray.gay";
	static final String META = SHOP + "/api/mod";
	static final String GITHUB_META =
		"https://raw.githubusercontent.com/camberX/Eisenmann/main/web/public/mod/latest.json";
	static final String LEGACY_GITHUB_META =
		"https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/latest.json";

	private UpdateMeta() {
	}

	public static String latestVersion() {
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(6))
			.build();
		JsonObject json = getJson(http, META, 12);
		if (json == null) {
			json = getJson(http, GITHUB_META, 12);
		}
		if (json == null) {
			json = getJson(http, LEGACY_GITHUB_META, 12);
		}
		if (json == null || !json.has("version")) {
			return null;
		}
		String version = json.get("version").getAsString().trim();
		return version.isEmpty() ? null : version;
	}

	static JsonObject getJson(HttpClient http, String url, int timeoutSec) {
		try {
			HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(timeoutSec))
					.header("User-Agent", "Stray-Update")
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
