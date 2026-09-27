package me.adamix.lte.listener;

import lombok.RequiredArgsConstructor;
import me.adamix.lte.LifecycleService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class PlayerListener implements Listener {
    private final LifecycleService lifecycleService;

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        lifecycleService.rebuildPlayer(event.getPlayer());
    }
}