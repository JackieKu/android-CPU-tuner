package ch.amana.android.cputuner.hw;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import ch.amana.android.cputuner.helper.SettingsStorage;
import ch.amana.android.cputuner.log.Logger;
import ch.amana.android.cputuner.model.IGovernorModel;
import ch.amana.android.cputuner.model.ProfileModel;

public class CpuHandler extends HardwareHandler {

	public static final String GOV_ONDEMAND = "ondemand";
	public static final String GOV_POWERSAVE = "powersave";
	public static final String GOV_CONSERVATIVE = "conservative";
	public static final String GOV_PERFORMANCE = "performance";
	public static final String GOV_INTERACTIVE = "interactive";
	public static final String GOV_USERSPACE = "userspace";
	public static final String GOV_SMARTASS = "smartass";

	public static final String CPU_BASE_DIR = "/sys/devices/system/cpu/";
	protected static final String CPUFREQ_DIR = "cpufreq/";
	public static final String CPU_DIR = CPU_BASE_DIR + "/cpu0/" + CPUFREQ_DIR;

	protected static final String SCALING_GOVERNOR = "scaling_governor";
	public static final String SCALING_MAX_FREQ = "scaling_max_freq";
	protected static final String SCALING_MIN_FREQ = "scaling_min_freq";
	protected static final String SCALING_SETSPEED = "scaling_setspeed";
	protected static final String SCALING_CUR_FREQ = "scaling_cur_freq";
	protected static final String SCALING_AVAILABLE_GOVERNORS = "scaling_available_governors";
	protected static final String SCALING_AVAILABLE_FREQUENCIES = "scaling_available_frequencies";
	protected static final String POWERSAVE_BIAS = "powersave_bias";

	protected static final String GOV_TRESHOLD_UP = "up_threshold";
	protected static final String GOV_TRESHOLD_DOWN = "down_threshold";
	protected static final String CPUINFO_MIN_FREQ = "cpuinfo_min_freq";
	protected static final String CPUINFO_MAX_FREQ = "cpuinfo_max_freq";
	protected static final String GOV_SAMPLING_RATE = "sampling_rate";

	protected static final String CPU_STATS_DIR = CPU_DIR + "stats/";
	protected static final String TIME_IN_STATE = "time_in_state";
	protected static final String TOTAL_TRANSITIONS = "total_trans";

	private boolean availCpuFreq = true;
	private final Map<String, File> fileMap = new WeakHashMap<String, File>();

	private static CpuHandler instance = null;

	public static CpuHandler resetInstance() {
		instance = null;
		return getInstance();
	}

	public synchronized static CpuHandler getInstance() {
		if (instance == null) {
			Logger.w("Creating new CpuHandler instance");
			SettingsStorage settingsStorage = SettingsStorage.getInstance();
			File cpuBase = new File(CPU_BASE_DIR);
			String[] cpus = cpuBase.list(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String filename) {
					File file = new File(dir, filename);
					return file.isDirectory() && filename.matches("cpu\\d");
				}
			});

			Logger.i("Found " + cpus.length + " CPUs");
			int useMulticore = settingsStorage.isUseMulticoreCode();
			if ((cpus.length > 1 || useMulticore == SettingsStorage.MULTICORE_CODE_ENABLE) && useMulticore != SettingsStorage.MULTICORE_CODE_DISABLE) {
				Logger.i("Using multicore code");
				instance = new CpuHandlerMulticore(cpus);
			} else {
				Logger.i("Using singlecore code");
				instance = new CpuHandler();
			}
		}
		return instance;
	}

	public ProfileModel getCurrentCpuSettings() {
		return new ProfileModel(getCurCpuGov(), getMaxCpuFreq(), getMinCpuFreq(), getGovThresholdUp(), getGovThresholdDown(), getPowersaveBias());
	}

	public void applyGovernorSettings(IGovernorModel governor) {
		setCurGov(governor.getGov());
		int thresholdUp = governor.getGovernorThresholdUp();
		int thresholdDown = governor.getGovernorThresholdDown();
		if (thresholdDown >= thresholdUp) {
			if (thresholdUp > 30) {
				thresholdDown = thresholdUp - 10;
			} else {
				thresholdDown = thresholdUp - 1;
			}
		}
		setGovThresholdUp(thresholdUp);
		setGovThresholdDown(thresholdDown);
		if (governor.hasScript()) {
			StringBuilder result = new StringBuilder();
			RootHandler.execute(governor.getScript(), result);
		}
		setPowersaveBias(governor.getPowersaveBias());
	}

	public void applyCpuSettings(ProfileModel profile) {
		if (GOV_USERSPACE.equals(profile.getGov())) {
			setUserCpuFreq(profile.getMaxFreq());
		} else {
			setMaxCpuFreq(profile.getMaxFreq());
			setMinCpuFreq(profile.getMinFreq());
		}
		applyGovernorSettings(profile);
	}

	public int getCurCpuFreq() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR, SCALING_CUR_FREQ)));
	}

	public String getCurCpuGov() {
		return RootHandler.readFile(getFile(CPU_DIR, SCALING_GOVERNOR));
	}

	public boolean setCurGov(String gov) {
		Logger.i("Setting governor to " + gov);
		return RootHandler.writeFile(getFile(CPU_DIR, SCALING_GOVERNOR), gov);
	}

	public String[] getAvailCpuGov() {
		String readFile = RootHandler.readFile(getFile(CPU_DIR, SCALING_AVAILABLE_GOVERNORS));
		if (!SettingsStorage.getInstance().isEnableUserspaceGovernor()) {
			readFile = readFile.replace(GOV_USERSPACE, "");
		}
		return moveCurListElementTop(createListStr(readFile), getCurCpuGov());
	}

	public boolean hasGovernor(String governor) {
		return RootHandler.readFile(getFile(CPU_DIR, SCALING_AVAILABLE_GOVERNORS)).contains(governor);
	}

	public int getMaxCpuFreq() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR, SCALING_MAX_FREQ)));
	}

	public boolean setUserCpuFreq(int val) {
		Logger.i("Setting user frequency to " + val);
		return RootHandler.writeFile(getFile(CPU_DIR, SCALING_SETSPEED), val + "");
	}

	public boolean setMaxCpuFreq(int val) {
		Logger.i("Setting max frequency to " + val);
		return RootHandler.writeFile(getFile(CPU_DIR, SCALING_MAX_FREQ), Integer.toString(val));
	}

	public int getUserCpuFreq() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR, SCALING_SETSPEED)));
	}

	public int getMinCpuFreq() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR, SCALING_MIN_FREQ)));
	}

	public boolean setMinCpuFreq(int i) {
		Logger.i("Setting min frequency to " + i);
		return RootHandler.writeFile(getFile(CPU_DIR, SCALING_MIN_FREQ), Integer.toString(i));
	}

	public int getGovThresholdUp() {
		String path = CPU_DIR + getCurCpuGov();
		return getIntFromStr(RootHandler.readFile(getFile(path, GOV_TRESHOLD_UP)));
	}

	public int getGovSamplingRate() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR + getCurCpuGov(), GOV_SAMPLING_RATE)));
	}

	public boolean setGovSamplingRate(int i) {
		return RootHandler.writeFile(getFile(CPU_DIR + getCurCpuGov(), GOV_SAMPLING_RATE), i + "");
	}

	public int getGovThresholdDown() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR + getCurCpuGov(), GOV_TRESHOLD_DOWN)));
	}

	public boolean setPowersaveBias(int i) {
		if (i < 0) {
			return false;
		}
		return RootHandler.writeFile(getFile(CPU_DIR + getCurCpuGov(), POWERSAVE_BIAS), i + "");
	}

	public int getPowersaveBias() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR + getCurCpuGov(), POWERSAVE_BIAS)));
	}

	public boolean setGovThresholdUp(int i) {
		if (i < 1) {
			return false;
		}
		if (i > 100) {
			i = 98;
		}
		Logger.i("Setting threshold up to " + i);
		return RootHandler.writeFile(getFile(CPU_DIR + getCurCpuGov(), GOV_TRESHOLD_UP), i + "");
	}

	protected File getFile(String path, String name) {
		StringBuilder sb = new StringBuilder(path.length() + 30);
		sb.append(path).append("/").append(name);
		File file = fileMap.get(sb.toString());
		if (file == null) {
			file = new File(path, name);
			fileMap.put(name, file);
		}
		return file;
	}

	public boolean setGovThresholdDown(int i) {
		if (i < 1) {
			return false;
		}
		if (i > 100) {
			i = 95;
		}
		Logger.i("Setting threshold down to " + i);
		return RootHandler.writeFile(getFile(CPU_DIR + getCurCpuGov(), GOV_TRESHOLD_DOWN), i + "");
	}

	public int[] getAvailCpuFreq(boolean allowLowFreqs) {
		int[] ret = getAvailCpuFreqInternal(allowLowFreqs);
		if (ret.length < 1) {
			ret = new int[1];
			ret[0] = NO_VALUE_INT;
		}
		return ret;
	}

	private int[] getAvailCpuFreqInternal(boolean allowLowFreqs) {

		int[] freqs = createListInt(RootHandler.readFile(getFile(CPU_DIR, SCALING_AVAILABLE_FREQUENCIES)));
		if (freqs[0] == NO_VALUE_INT) {
			availCpuFreq = false;
			String settingsFreqs = SettingsStorage.getInstance().getCpuFreqs();
			freqs = createListInt(settingsFreqs);
			boolean success = true;
			for (int i = 0; i < freqs.length && success; i++) {
				success = freqs[i] != NO_VALUE_INT;
			}
			if (success) {
				Arrays.sort(freqs);
				return freqs;
			} else {
				SortedSet<Integer> sortedSet = new TreeSet<Integer>();
				sortedSet.add(getCpuInfoMinFreq());
				sortedSet.add(getMinCpuFreq());
				sortedSet.add(getCurCpuFreq());
				sortedSet.add(getMaxCpuFreq());
				sortedSet.add(getCpuInfoMaxFreq());
				int[] res = new int[sortedSet.size()];
				int i = 0;
				for (int freq : sortedSet) {
					res[i++] = freq;
				}
				// TODO save to settings?
				Logger.w("No available frequencies found... generating from min/max");
				return res;
			}

		}
		availCpuFreq = true;
		if (allowLowFreqs || SettingsStorage.getInstance().isPowerUser()) {
			Arrays.sort(freqs);
			return freqs;
		}

		List<Integer> freqList = new ArrayList<Integer>(freqs.length);
		for (int i = 0; i < freqs.length; i++) {
			if (freqs[i] >= getMinimumSensibleFrequency()) {
				freqList.add(freqs[i]);
			}
		}
		freqs = new int[freqList.size()];
		int i = 0;
		for (Iterator<Integer> iterator = freqList.iterator(); iterator.hasNext();) {
			freqs[i++] = iterator.next();
		}
		Arrays.sort(freqs);
		return freqs;
	}

	public int getCpuInfoMaxFreq() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR, CPUINFO_MAX_FREQ)));
	}

	public int getCpuInfoMinFreq() {
		return getIntFromStr(RootHandler.readFile(getFile(CPU_DIR, CPUINFO_MIN_FREQ)));
	}

	public int getMinimumSensibleFrequency() {
		return SettingsStorage.getInstance().getMinimumSensibeFrequency() * 1000;
	}

	public boolean hasGov() {
		return !RootHandler.NOT_AVAILABLE.equals(getCurCpuGov());
	}

	public boolean hasAvailCpuFreq() {
		return availCpuFreq;
	}

	public boolean hasUpThreshold() {
		return (new File(CPU_DIR + getCurCpuGov(), GOV_TRESHOLD_UP)).exists();
	}

	public boolean hasDownThreshold() {
		return (new File(CPU_DIR + getCurCpuGov(), GOV_TRESHOLD_DOWN)).exists();
	}

	public String getCpuTimeinstate() {
		return RootHandler.readFile(getFile(CPU_STATS_DIR, TIME_IN_STATE));
	}

	public String getCpuTotalTransitions() {
		return RootHandler.readFile(getFile(CPU_STATS_DIR, TOTAL_TRANSITIONS));
	}

	public int getNumberOfCpus() {
		return 1;
	}

	public void setNumberOfActiveCpus(int position) {
	}

	public int getNumberOfActiveCpus() {
		return 1;
	}

}
