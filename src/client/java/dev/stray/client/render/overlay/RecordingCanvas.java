package dev.stray.client.render.overlay;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * A {@link Canvas} that only writes down what is drawn, with a running hash of it. The
 * streamproof overlay records a frame like this first, and only switches to its own GL
 * context to draw when the hash changed (and no faster than the monitor refreshes).
 */
public final class RecordingCanvas implements Canvas {
	private final List<Consumer<Canvas>> ops = new ArrayList<>();
	private final ToDoubleFunction<String> measure;
	private long hash = 1469598103934665603L;

	/** {@code measure}: text width in GUI units, answered now (the drawing canvas's own metrics). */
	public RecordingCanvas(ToDoubleFunction<String> measure) {
		this.measure = measure;
	}

	/** Starts a new frame. */
	public void reset() {
		ops.clear();
		hash = 1469598103934665603L;
	}

	public long hash() {
		return hash;
	}

	/** Mixes a value that isn't a draw call (window size, whether it shows) into the hash. */
	public void mix(long value) {
		hash = (hash ^ value) * 1099511628211L;
	}

	/** Draws everything recorded onto {@code target}. */
	public void replay(Canvas target) {
		for (Consumer<Canvas> op : ops) {
			op.accept(target);
		}
	}

	private void mix(int op, float... values) {
		mix(op);
		for (float v : values) {
			mix(Float.floatToIntBits(v));
		}
	}

	@Override
	public void push() {
		mix(1);
		ops.add(Canvas::push);
	}

	@Override
	public void pop() {
		mix(2);
		ops.add(Canvas::pop);
	}

	@Override
	public void translate(float x, float y) {
		mix(3, x, y);
		ops.add(c -> c.translate(x, y));
	}

	@Override
	public void scale(float s) {
		mix(4, s);
		ops.add(c -> c.scale(s));
	}

	@Override
	public void rotate(float radians) {
		mix(5, radians);
		ops.add(c -> c.rotate(radians));
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		mix(6, x, y, w, h);
		mix(argb);
		ops.add(c -> c.fill(x, y, w, h, argb));
	}

	@Override
	public void texture(Identifier id, float x, float y, float w, float h, float u, float v, float regionW, float regionH, float texW, float texH, int tint) {
		mix(7, x, y, w, h, u, v, regionW, regionH, texW, texH);
		mix(tint);
		mix(id == null ? 0 : id.hashCode());
		ops.add(c -> c.texture(id, x, y, w, h, u, v, regionW, regionH, texW, texH, tint));
	}

	@Override
	public void line(float x0, float y0, float x1, float y1, float width, int argb) {
		mix(8, x0, y0, x1, y1, width);
		mix(argb);
		ops.add(c -> c.line(x0, y0, x1, y1, width, argb));
	}

	@Override
	public void text(String text, float x, float y, int argb, boolean shadow) {
		mix(9, x, y);
		mix(argb);
		mix(shadow ? 1 : 0);
		mix(text == null ? 0 : text.hashCode());
		ops.add(c -> c.text(text, x, y, argb, shadow));
	}

	@Override
	public float width(String text) {
		return (float) measure.applyAsDouble(text);
	}

	@Override
	public void panel(float x, float y, float w, float h, float radius, int fill, int edge) {
		mix(10, x, y, w, h, radius);
		mix(fill);
		mix(edge);
		ops.add(c -> c.panel(x, y, w, h, radius, fill, edge));
	}
}
