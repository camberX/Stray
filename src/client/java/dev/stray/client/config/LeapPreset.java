package dev.stray.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** A saved set of Auto Leap and Fast Leap settings. */
public final class LeapPreset {
	public int mode;
	public boolean swapBack;
	public boolean autoP3;
	public boolean fastP3;
	public boolean autoOnlyGate;
	public boolean autoDoorAfterBlood;
	public boolean fastDoorAfterBlood;
	public boolean autoMelody;
	public boolean fastMelody;
	public Map<String, LeapTargetConfig> auto = new LinkedHashMap<>();
	public Map<String, LeapTargetConfig> fast = new LinkedHashMap<>();
}
