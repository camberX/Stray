package dev.stray.client.config;

import dev.stray.client.render.GlowBlurRadius;

public final class EntityVisuals {
	public boolean glowEnabled = false;
	public boolean glowThroughWalls = false;
	public float glowRadius = GlowBlurRadius.DEFAULT;
	public float glowOpacity = 0.58f;
	public int glowRgb = 0x2FB5FF;
	public boolean nametagsEnabled = false;
	public boolean nametagThroughWalls = false;
	public boolean nametagDistance = false;
	public String nametagStyle = "custom";
	public int nametagRange = 128;
	public float nametagScale = 1.0f;
	public float nametagOpacity = 1.0f;
	public boolean healthEnabled = false;
	public boolean healthThroughWalls = false;
	public String healthSide = "right";
	public String healthStyle = "stray";
	public int healthFullRgb = 0x17EB17;
	public int healthEmptyRgb = 0xEB1717;
	public int healthRange = 48;
	public float healthWidth = 3f;
	public boolean boxEnabled = false;
	public boolean boxThroughWalls = false;
	public int boxRange = 48;
	public int boxRgb = 0x2FB5FF;
	public float boxOpacity = 0.90f;
	public float boxFill = 0.12f;
	public float boxWidth = 2f;

	public void clamp() {
		glowRadius = StrayConfig.clamp(glowRadius <= 0f ? GlowBlurRadius.DEFAULT : glowRadius, GlowBlurRadius.MIN, GlowBlurRadius.MAX);
		glowOpacity = StrayConfig.clamp(glowOpacity <= 0f ? 0.58f : glowOpacity, 0.15f, 0.90f);
		glowRgb = glowRgb & 0xFFFFFF;
		if (glowRgb == 0) {
			glowRgb = 0x2FB5FF;
		}
		nametagStyle = nametagCustom() ? "custom" : "vanilla";
		nametagRange = StrayConfig.clamp(nametagRange <= 0 ? 128 : nametagRange, 64, 256);
		nametagScale = StrayConfig.clamp(nametagScale <= 0f ? 1f : nametagScale, 0.50f, 2.00f);
		nametagOpacity = StrayConfig.clamp(nametagOpacity <= 0f ? 1f : nametagOpacity, 0.15f, 1f);
		healthSide = StrayConfig.normalizeHealthBarSide(healthSide);
		healthStyle = StrayConfig.normalizeHealthBarStyle(healthStyle);
		healthFullRgb = healthFullRgb & 0xFFFFFF;
		if (healthFullRgb == 0) {
			healthFullRgb = 0x17EB17;
		}
		healthEmptyRgb = healthEmptyRgb & 0xFFFFFF;
		if (healthEmptyRgb == 0) {
			healthEmptyRgb = 0xEB1717;
		}
		healthRange = StrayConfig.clamp(healthRange <= 0 ? 48 : healthRange, 16, 96);
		healthWidth = StrayConfig.clamp(healthWidth <= 0f ? 3f : healthWidth, 1f, 6f);
		boxRange = StrayConfig.clamp(boxRange <= 0 ? 48 : boxRange, 16, 96);
		boxRgb = boxRgb & 0xFFFFFF;
		if (boxRgb == 0) {
			boxRgb = 0x2FB5FF;
		}
		boxOpacity = StrayConfig.clamp(boxOpacity <= 0f ? 0.90f : boxOpacity, 0.15f, 1f);
		boxFill = StrayConfig.clamp(boxFill < 0f ? 0.12f : boxFill, 0f, 0.55f);
		boxWidth = StrayConfig.clamp(boxWidth <= 0f ? 2f : boxWidth, 1f, 6f);
	}

	public boolean nametagCustom() {
		return !"vanilla".equalsIgnoreCase(nametagStyle);
	}

	public String nametagStyleLabel() {
		return nametagCustom() ? "Stray" : "Vanilla";
	}

	public void cycleNametagStyle() {
		nametagStyle = nametagCustom() ? "vanilla" : "custom";
	}

	public boolean healthRight() {
		return !"left".equalsIgnoreCase(healthSide);
	}

	public String healthSideLabel() {
		return healthRight() ? "Right" : "Left";
	}

	public void cycleHealthSide() {
		healthSide = healthRight() ? "left" : "right";
	}

	public boolean healthCsgo() {
		return "csgo".equalsIgnoreCase(healthStyle);
	}

	public String healthStyleLabel() {
		return healthCsgo() ? "CS:GO" : "Stray";
	}

	public void cycleHealthStyle() {
		healthStyle = healthCsgo() ? "stray" : "csgo";
	}

	public boolean anyEnabled() {
		return glowEnabled || nametagsEnabled || healthEnabled || boxEnabled;
	}
}
