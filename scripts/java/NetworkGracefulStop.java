import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Paths;

/** Local administrator migration helper: validates the exact JVM and queues Bukkit's normal stop. */
public final class NetworkGracefulStop {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("pid agentJar expectedRuntime expectedPort");
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            String directory = vm.getSystemProperties().getProperty("user.dir");
            if (!Paths.get(directory).toRealPath().equals(Paths.get(args[2]).toRealPath())) {
                throw new IllegalStateException("Refusing to stop a JVM outside the exact PvP runtime");
            }
            vm.loadAgent(Paths.get(args[1]).toRealPath().toString(), args[3]);
        } finally { vm.detach(); }
    }

    public static void agentmain(String expectedPort, Instrumentation instrumentation) throws Exception {
        Class<?> bukkit = null;
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals("org.bukkit.Bukkit")) { bukkit = loaded; break; }
        }
        if (bukkit == null) throw new IllegalStateException("Not a Bukkit server");
        if (((Number) bukkit.getMethod("getPort").invoke(null)).intValue() != Integer.parseInt(expectedPort)) {
            throw new IllegalStateException("Unexpected server port; no shutdown requested");
        }
        Object manager = bukkit.getMethod("getPluginManager").invoke(null);
        ClassLoader loader = bukkit.getClassLoader();
        Object plugin = Class.forName("org.bukkit.plugin.PluginManager", false, loader)
                .getMethod("getPlugin", String.class).invoke(manager, "PoppyPractice");
        if (plugin == null) throw new IllegalStateException("Expected PoppyPractice plugin is absent");
        Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
        Method runTask = Class.forName("org.bukkit.scheduler.BukkitScheduler", false, loader)
                .getMethod("runTask", Class.forName("org.bukkit.plugin.Plugin", false, loader), Runnable.class);
        final Method shutdown = bukkit.getMethod("shutdown");
        runTask.invoke(scheduler, plugin, (Runnable) () -> {
            System.out.println("[Poppy Network] Authorized migration: saving and stopping PvP normally.");
            try { shutdown.invoke(null); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
}
