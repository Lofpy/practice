import com.sun.jna.Native;
import com.sun.jna.platform.linux.Udev;
import java.nio.file.Files;
import java.nio.file.Path;
import oshi.SystemInfo;

/** Loads the actual Paper-native dependencies without starting/accepting Minecraft. */
public final class NativeStartupProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected immutable native directory");
        }
        if (Native.POINTER_SIZE != Long.BYTES) {
            throw new IllegalStateException("Expected 64-bit JNA native dispatch");
        }
        String loaded = System.getProperty("jnidispatch.path");
        Path expected = Path.of(args[0], "libjnidispatch.so").toRealPath();
        if (loaded == null || !Path.of(loaded).toRealPath().equals(expected)) {
            throw new IllegalStateException("JNA did not load immutable native library: " + loaded);
        }
        if (Files.isWritable(expected)) {
            throw new IllegalStateException("JNA native code is writable by runtime user");
        }
        // Paper's system report reaches these APIs before the normal server loop.
        SystemInfo information = new SystemInfo();
        String operatingSystem = information.getOperatingSystem().getFamily();
        int processors = information.getHardware().getProcessor().getLogicalProcessorCount();
        long memory = information.getHardware().getMemory().getTotal();
        if (operatingSystem.isBlank() || processors < 1 || memory < 1 || Udev.INSTANCE == null) {
            throw new IllegalStateException("Incomplete Linux system information");
        }
        System.out.println("SURVIVAL_NATIVE_READY JNA=" + Native.VERSION
                + " path=" + loaded + " os=" + operatingSystem
                + " processors=" + processors + " memory=" + memory);
    }
}
