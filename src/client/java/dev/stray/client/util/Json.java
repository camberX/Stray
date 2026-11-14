package dev.stray.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Null-safe reads from Gson objects, as used for the Hypixel API, stray.gay and saved files:
 * a missing key or a value of the wrong kind gives the empty value instead of throwing.
 */
public final class Json {
	private Json() {
	}

	/** The number under {@code key}, 0 when missing or not a number. */
	public static double num(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return 0d;
		}
		try {
			return object.get(key).getAsDouble();
		} catch (Exception ignored) {
			return 0d;
		}
	}

	/** The string under {@code key}, "" when missing or not a primitive. */
	public static String string(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return object.get(key).getAsString();
		} catch (Exception ignored) {
			return "";
		}
	}

	/** The object under {@code key}, null when missing or not an object. */
	public static JsonObject object(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	/** The array under {@code key}, null when missing or not an array. */
	public static JsonArray array(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}

	/** The boolean under {@code key}, false when missing. */
	public static boolean bool(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return false;
		}
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsBoolean();
	}

	/** The encoded item data of a Hypixel API inventory field, which comes as a string or as an object holding it under one of several keys. */
	public static String data(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			return primitive.isString() ? primitive.getAsString() : "";
		}
		if (!element.isJsonObject()) {
			return "";
		}
		JsonObject object = element.getAsJsonObject();
		for (String key : new String[]{"data", "value", "contents", "nbt", "bytes", "item_bytes"}) {
			JsonElement value = object.get(key);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				String text = value.getAsString();
				if (!text.isBlank()) {
					return text;
				}
			}
		}
		return "";
	}
}
