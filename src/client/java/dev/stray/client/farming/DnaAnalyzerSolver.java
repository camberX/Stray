package dev.stray.client.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SkyHanni DnaAnalyzerSolver board search: permute each mutable column, connect
 * adjacent columns, and emit the column-internal swaps for a cheapest path.
 */
public final class DnaAnalyzerSolver {
	public static final int ROWS = 4;
	public static final int COLUMNS = 9;
	public static final int UNREACHABLE = 1_000;

	private static final List<int[]> ROW_PERMUTATIONS = rowPermutations();

	private DnaAnalyzerSolver() {
	}

	public enum Color {
		RED,
		YELLOW,
		BLUE,
		GREEN
	}

	public record Cell(int column, int row) {
		public int slot() {
			return column + (row + 1) * 9;
		}
	}

	public record Swap(Cell a, Cell b) {
	}

	public record Solution(int swapsNeeded, List<Swap> swaps) {
		public static Solution none() {
			return new Solution(UNREACHABLE, List.of());
		}

		public boolean reachable() {
			return swapsNeeded < UNREACHABLE;
		}
	}

	public static Solution solve(Color[][] columns, boolean allowEnds) {
		if (columns == null || columns.length != COLUMNS) {
			return Solution.none();
		}
		for (Color[] column : columns) {
			if (column == null || column.length != ROWS) {
				return Solution.none();
			}
			for (Color color : column) {
				if (color == null) {
					return Solution.none();
				}
			}
		}

		int firstMutable = allowEnds ? 0 : 1;
		int lastMutable = allowEnds ? COLUMNS - 1 : COLUMNS - 2;
		int mutableCount = lastMutable - firstMutable + 1;
		int permCount = ROW_PERMUTATIONS.size();

		int[][] dp = new int[mutableCount][permCount];
		int[][] parent = new int[mutableCount][permCount];
		int[][] cost = new int[mutableCount][permCount];
		@SuppressWarnings("unchecked")
		List<int[]>[][] swapMap = new List[mutableCount][permCount];

		for (int i = 0; i < mutableCount; i++) {
			Arrays.fill(dp[i], UNREACHABLE);
			Arrays.fill(parent[i], -1);
			Color[] column = columns[firstMutable + i];
			for (int p = 0; p < permCount; p++) {
				Color[] perm = permute(column, ROW_PERMUTATIONS.get(p));
				ColumnSwaps computed = minimumColumnSwaps(column, perm);
				cost[i][p] = computed.cost;
				swapMap[i][p] = computed.swaps;
			}
		}

		for (int p = 0; p < permCount; p++) {
			Color[] perm = permute(columns[firstMutable], ROW_PERMUTATIONS.get(p));
			if (!allowEnds && !canColumnsConnect(columns[0], perm)) {
				continue;
			}
			dp[0][p] = cost[0][p];
		}

		for (int i = 1; i < mutableCount; i++) {
			for (int p = 0; p < permCount; p++) {
				Color[] cur = permute(columns[firstMutable + i], ROW_PERMUTATIONS.get(p));
				for (int q = 0; q < permCount; q++) {
					if (dp[i - 1][q] == UNREACHABLE) {
						continue;
					}
					Color[] prev = permute(columns[firstMutable + i - 1], ROW_PERMUTATIONS.get(q));
					if (!canColumnsConnect(prev, cur)) {
						continue;
					}
					int newCost = dp[i - 1][q] + cost[i][p];
					if (newCost < dp[i][p]) {
						dp[i][p] = newCost;
						parent[i][p] = q;
					}
				}
			}
		}

		int best = UNREACHABLE;
		int last = -1;
		for (int p = 0; p < permCount; p++) {
			Color[] perm = permute(columns[lastMutable], ROW_PERMUTATIONS.get(p));
			if (!allowEnds && !canColumnsConnect(perm, columns[COLUMNS - 1])) {
				continue;
			}
			if (dp[mutableCount - 1][p] < best) {
				best = dp[mutableCount - 1][p];
				last = p;
			}
		}
		if (last == -1) {
			return Solution.none();
		}

		List<Swap> result = new ArrayList<>();
		int i = mutableCount - 1;
		int cur = last;
		while (i >= 0) {
			int colIndex = firstMutable + i;
			for (int[] pair : swapMap[i][cur]) {
				result.add(new Swap(new Cell(colIndex, pair[0]), new Cell(colIndex, pair[1])));
			}
			cur = parent[i][cur];
			i--;
		}
		return new Solution(best, List.copyOf(result));
	}

	private static Color[] permute(Color[] column, int[] perm) {
		Color[] out = new Color[ROWS];
		for (int r = 0; r < ROWS; r++) {
			out[r] = column[perm[r]];
		}
		return out;
	}

	private static boolean canColumnsConnect(Color[] a, Color[] b) {
		for (int r = 0; r < ROWS; r++) {
			Color v = a[r];
			if (b[r] == v) {
				continue;
			}
			if (r > 0 && b[r - 1] == v) {
				continue;
			}
			if (r < ROWS - 1 && b[r + 1] == v) {
				continue;
			}
			return false;
		}
		return true;
	}

	private static ColumnSwaps minimumColumnSwaps(Color[] from, Color[] to) {
		int[] pos = new int[ROWS];
		for (int i = 0; i < ROWS; i++) {
			pos[indexOf(from, to[i])] = i;
		}
		boolean[] visited = new boolean[ROWS];
		List<int[]> swaps = new ArrayList<>();
		int cost = 0;
		for (int i = 0; i < ROWS; i++) {
			if (visited[i]) {
				continue;
			}
			int cur = i;
			List<Integer> cycle = new ArrayList<>();
			while (!visited[cur]) {
				visited[cur] = true;
				cycle.add(cur);
				cur = pos[cur];
			}
			if (cycle.size() > 1) {
				cost += cycle.size() - 1;
				for (int k = 1; k < cycle.size(); k++) {
					swaps.add(new int[]{cycle.get(0), cycle.get(k)});
				}
			}
		}
		return new ColumnSwaps(cost, swaps);
	}

	private static int indexOf(Color[] column, Color color) {
		for (int i = 0; i < column.length; i++) {
			if (column[i] == color) {
				return i;
			}
		}
		return 0;
	}

	private static List<int[]> rowPermutations() {
		List<int[]> perms = new ArrayList<>();
		int[] values = {0, 1, 2, 3};
		generate(perms, values, 0);
		return List.copyOf(perms);
	}

	private static void generate(List<int[]> perms, int[] values, int start) {
		if (start == ROWS) {
			perms.add(values.clone());
			return;
		}
		for (int i = start; i < ROWS; i++) {
			swap(values, start, i);
			generate(perms, values, start + 1);
			swap(values, start, i);
		}
	}

	private static void swap(int[] values, int a, int b) {
		int tmp = values[a];
		values[a] = values[b];
		values[b] = tmp;
	}

	private record ColumnSwaps(int cost, List<int[]> swaps) {
	}
}
