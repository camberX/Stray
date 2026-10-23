package dev.stray.client.cs2.skin;

/**
 * Valve's uniform random stream (Park-Miller with a Bays-Durham shuffle, as in the
 * Source SDK). Pattern seeds feed this, so its sequence must match exactly.
 */
public final class ValveRandom {
	private static final int NTAB = 32;
	private static final int IA = 16807;
	private static final int IM = 2147483647;
	private static final int IQ = 127773;
	private static final int IR = 2836;
	private static final int NDIV = 1 + (IM - 1) / NTAB;
	private static final double AM = 1.0 / IM;
	private static final double RNMX = 1.0 - 1.2e-7;

	private int idum;
	private int iy;
	private final int[] iv = new int[NTAB];

	public ValveRandom(int seed) {
		idum = seed < 0 ? seed : -seed;
		iy = 0;
	}

	private int next() {
		int j;
		int k;
		if (idum <= 0 || iy == 0) {
			if (-idum < 1) {
				idum = 1;
			} else {
				idum = -idum;
			}
			for (j = NTAB + 7; j >= 0; j--) {
				k = idum / IQ;
				idum = IA * (idum - k * IQ) - IR * k;
				if (idum < 0) {
					idum += IM;
				}
				if (j < NTAB) {
					iv[j] = idum;
				}
			}
			iy = iv[0];
		}
		k = idum / IQ;
		idum = IA * (idum - k * IQ) - IR * k;
		if (idum < 0) {
			idum += IM;
		}
		j = iy / NDIV;
		if (j >= NTAB || j < 0) {
			j = (j % NTAB) & 0x7FFFFFFF;
		}
		iy = iv[j];
		iv[j] = idum;
		return iy;
	}

	public float nextFloat(float low, float high) {
		float fl = (float) (AM * next());
		if (fl > RNMX) {
			fl = (float) RNMX;
		}
		return fl * (high - low) + low;
	}
}
