package dev.potionmerger;

import org.bukkit.plugin.java.JavaPlugin;

public final class PotionMergerPlugin extends JavaPlugin {

    private static PotionMergerPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        getLogger().info("PotionMerger enabled! Merge up to 3 potions on a crafting table.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PotionMerger disabled.");
    }

    public static PotionMergerPlugin getInstance() {
        return instance;
    }
}
