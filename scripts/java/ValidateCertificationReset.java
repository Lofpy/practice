import com.poppy.practice.rating.EloCalculator;
import com.poppy.practice.rating.RatingService;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Logger;

/** Read-only preflight for reset-certifications.ps1. No server shutdown or data writes. */
public final class ValidateCertificationReset {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("expectedRuntimeDirectory");
        Path runtime = Paths.get(args[0]).toRealPath();
        assertStopped(runtime);
        Path data = runtime.resolve("plugins/PoppyPractice");
        Path ratings = data.resolve("ratings.yml");
        if (!Files.isRegularFile(ratings)) {
            throw new IllegalStateException("ratings.yml must exist; nothing was changed");
        }
        RatingService service = new RatingService(data.toFile(), Logger.getLogger("CertificationResetPreflight"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(new String(Files.readAllBytes(ratings), StandardCharsets.UTF_8));
        ConfigurationSection matches = yaml.getConfigurationSection("matches");
        int placements = 0;
        for (String id : matches.getKeys(false)) {
            if (!matches.getString(id).startsWith("placement:")) {
                throw new IllegalStateException("Ranked matches exist; certification-only reset requires manual review");
            }
            placements++;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        int kits = 0;
        for (String id : players.getKeys(false)) {
            UUID player = UUID.fromString(id);
            ConfigurationSection rows = players.getConfigurationSection(id + ".kits");
            for (String kit : rows.getKeys(false)) {
                long expected = service.isQualified(player, kit)
                        ? EloCalculator.initialRating(service.getPlacementAverage(player, kit)) : 0L;
                if (service.getRatingMilli(player, kit) != expected) {
                    throw new IllegalStateException("Non-initial ELO exists; refusing to reset " + id + "/" + kit);
                }
                kits++;
            }
        }
        System.out.println("Validated " + players.getKeys(false).size() + " players, " + kits
                + " player/kit records, " + placements + " placements, no ranked games.");
    }

    private static void assertStopped(Path expectedRuntime) throws Exception {
        String self = Long.toString(ProcessHandle.current().pid());
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            if (descriptor.id().equals(self)) continue;
            // WindSpigot can close its listening port before finishing its final save.
            if (!descriptor.displayName().toLowerCase(java.util.Locale.ROOT).contains("windspigot")) continue;
            VirtualMachine vm = VirtualMachine.attach(descriptor);
            try {
                String workingDirectory = vm.getSystemProperties().getProperty("user.dir");
                if (workingDirectory == null) throw new IllegalStateException("Could not establish a WindSpigot runtime");
                if (Paths.get(workingDirectory).toRealPath().equals(expectedRuntime)) {
                    throw new IllegalStateException("PvP JVM " + descriptor.id() + " is still running; wait for normal shutdown");
                }
            } finally {
                vm.detach();
            }
        }
    }
}
