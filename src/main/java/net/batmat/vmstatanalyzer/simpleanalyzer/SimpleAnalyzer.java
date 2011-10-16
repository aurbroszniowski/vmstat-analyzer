package net.batmat.vmstatanalyzer.simpleanalyzer;

import net.batmat.vmstatanalyzer.model.Stats;
import net.batmat.vmstatanalyzer.model.VmstatAnalyzer;
import net.batmat.vmstatanalyzer.model.VmstatData;

public class SimpleAnalyzer implements VmstatAnalyzer {

	private static final int DEFAULT_CPU_COUNT = 4;
	private static final int MAX_LINE_COUNT = 20;
	private final VmstatData data;
	private final StringBuilder builder;

	MachineDescriptor machineDescriptor;

	public SimpleAnalyzer(VmstatData data) {
		this(data, new MachineDescriptor(DEFAULT_CPU_COUNT));
		builder.append("WARNING: Default CPU count (" + DEFAULT_CPU_COUNT
				+ ") is being used. Specify your actual number to have better results.\n");
	}

	public SimpleAnalyzer(VmstatData data, MachineDescriptor descriptor) {
		// TODO : assert that minimum columns are present
		this.data = data;
		machineDescriptor = descriptor;
		builder = new StringBuilder();
		if (data.getLineCount() > MAX_LINE_COUNT) {
			builder.append("WARNING: line count to be analyzed is too big (max=" + MAX_LINE_COUNT + ".\n"
					+ "You'd better select a shorter period, something like using 'vmstat 3' and selecting "
					+ MAX_LINE_COUNT + " when you know the system is having issues reported against it");
		}
	}

	public String getReport() {
		Dominance dominance = findDominatingConsumer();
		assert dominance != null;
		switch (dominance) {
		case SYSTEM:
			analyzeSystem();
		case USERSPACE:
			analyzeUserspace();
		}
		System.out.println(builder.toString());
		return builder.toString();
	}

	private void analyzeSystem() {
		analyzeSwap();
		analyzeCpuThreads();
	}

	// Linux : procs part, AIX : kthr part (columns r and b)
	// TODO : take the CPU number in account
	private void analyzeCpuThreads() {
		Stats rStats = new Stats(data.getValuesFor("r"));
		if (rStats.getAvg() > machineDescriptor.getCpuCount()) {
			builder
					.append("CPU-contention : a lot of threads are queued waiting for CPU. It should never be really higher than the CPU count. (r average="
							+ rStats.getAvg() + ", cpu count=" + machineDescriptor.getCpuCount() + ")\n");
		}
	}

	// Under Linux, it's si/so, with AIX it's pi/po
	// TODO : analyze other paging columns when present
	private void analyzeSwap() {
		String[] outData = data.getValuesFor("po");
		if (outData == null) {
			outData = data.getValuesFor("so");
		}
		assert outData != null;

		String[] inData = data.getValuesFor("pi");
		if (inData == null) {
			inData = data.getValuesFor("si");
		}
		assert inData != null;

		Stats swapOutStats = new Stats(outData);
		Stats swapInStats = new Stats(inData);
		if (swapOutStats.getAvg() > 0 || swapInStats.getAvg() > 0) {
			builder.append("Your system is swapping. Have a look at your "
					+ "memory-greedy processes or consider adding RAM.\n");
		}
	}

	private void analyzeUserspace() {
		analyzeCpuThreads();
	}

	/**
	 * Checks the proportion of lines dominated (>90%) by userspace or not
	 */
	private Dominance findDominatingConsumer() {
		Dominance r = null;
		String[] usValues = data.getValuesFor("us");

		Stats stats = new Stats(usValues);
		float userSpaceDominance = stats.getAvg();
		if (userSpaceDominance >= 90f) {
			builder.append("User space is dominating");
			r = Dominance.USERSPACE;
		} else if (userSpaceDominance > 70f) {
			builder.append("User space is mostly dominating");
			r = Dominance.USERSPACE;
		} else {
			builder.append("System is dominating");
			r = Dominance.SYSTEM;
		}
		builder.append("(userspace average=" + userSpaceDominance + "%)\n");
		// System.out.println(builder.toString());
		return r;
	}
}
