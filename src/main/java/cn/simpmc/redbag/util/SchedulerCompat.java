package cn.simpmc.redbag.util;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Small reflection bridge so the same jar works on Paper and Folia. */
public final class SchedulerCompat {

    private static final boolean FOLIA = detectFolia();

    private SchedulerCompat() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                return true;
            } catch (ClassNotFoundException ignoredAgain) {
                return false;
            }
        }
    }

    public static CancellableTask runGlobal(Plugin plugin, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (FOLIA) {
            CancellableTask taskHandle = invokeFolia(plugin, task, "run");
            if (taskHandle != null) {
                return taskHandle;
            }
        }
        return new BukkitHandle(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static CancellableTask runLaterGlobal(Plugin plugin, Runnable task, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (FOLIA) {
            CancellableTask taskHandle = invokeFolia(plugin, task, "runDelayed", delayTicks);
            if (taskHandle != null) {
                return taskHandle;
            }
        }
        return new BukkitHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static CancellableTask runAtFixedRateGlobal(
            Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (FOLIA) {
            CancellableTask taskHandle = invokeFolia(
                    plugin, task, "runAtFixedRate", initialDelayTicks, periodTicks);
            if (taskHandle != null) {
                return taskHandle;
            }
        }
        return new BukkitHandle(Bukkit.getScheduler().runTaskTimer(
                plugin, task, initialDelayTicks, periodTicks));
    }

    private static CancellableTask invokeFolia(Plugin plugin, Runnable task, String methodName,
            long... numbers) {
        try {
            Server server = Bukkit.getServer();
            Method schedulerMethod = server.getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = schedulerMethod.invoke(server);
            for (Method method : scheduler.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 2 + numbers.length
                        || !Plugin.class.isAssignableFrom(parameterTypes[0])) {
                    continue;
                }
                Object[] arguments = new Object[parameterTypes.length];
                arguments[0] = plugin;
                arguments[1] = (Consumer<Object>) ignored -> task.run();
                for (int index = 0; index < numbers.length; index++) {
                    arguments[index + 2] = numbers[index];
                }
                Object scheduled = method.invoke(scheduler, arguments);
                return new ReflectiveHandle(scheduled);
            }
        } catch (Throwable ignored) {
            // Fall back to Bukkit's scheduler when reflection is unavailable.
        }
        return null;
    }

    public interface CancellableTask {
        void cancel();
    }

    private record BukkitHandle(BukkitTask task) implements CancellableTask {
        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }
    }

    private static final class ReflectiveHandle implements CancellableTask {
        private final Object scheduled;
        private final Method cancel;

        private ReflectiveHandle(Object scheduled) {
            this.scheduled = scheduled;
            Method found = null;
            if (scheduled != null) {
                try {
                    found = scheduled.getClass().getMethod("cancel");
                } catch (ReflectiveOperationException ignored) {
                    // No cancellation method is acceptable for one-shot tasks.
                }
            }
            cancel = found;
        }

        @Override
        public void cancel() {
            if (scheduled == null || cancel == null) {
                return;
            }
            try {
                cancel.invoke(scheduled);
            } catch (ReflectiveOperationException ignored) {
                // Shutdown must remain best-effort.
            }
        }
    }
}
