package com.sagakenichi.freelifemarine;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreeLifeMarineMobsPlugin extends JavaPlugin {

    private MarineMobService mobs;
    private OrcaShowManager shows;
    private MarineFinalMotionController finalMotion;
    private MarineNaturalBehaviorController naturalBehavior;
    private RiddenOrcaBreachController riddenBreach;
    private OrcaShowEnhancementController showEnhancement;
    private MarineAirFallGuard airFallGuard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MarineFood food = new MarineFood(this);
        mobs = new MarineMobService(this, food);
        shows = new OrcaShowManager(this, mobs);
        finalMotion = new MarineFinalMotionController(this, mobs);
        naturalBehavior = new MarineNaturalBehaviorController(this, mobs);
        riddenBreach = new RiddenOrcaBreachController(this, mobs);
        showEnhancement = new OrcaShowEnhancementController(this, mobs);
        airFallGuard = new MarineAirFallGuard(this, mobs);
        MarineDamageFlash damageFlash = new MarineDamageFlash(this);
        MarineCommand command = new MarineCommand(mobs, food, shows);
        PluginCommand marine = getCommand("marine");
        if (marine == null) {
            throw new IllegalStateException("Command 'marine' is missing from plugin.yml");
        }
        marine.setExecutor(command);
        marine.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new MarineMobListener(mobs, damageFlash), this);
        mobs.start();
        shows.start();
        finalMotion.start();
        naturalBehavior.start();
        // Run after normal ridden steering. This lets the breach detector measure the
        // actual movement produced by W and turn only a fast upward surface approach
        // into an airborne jump.
        riddenBreach.start();
        showEnhancement.start();
        // Last motion pass: match falling-block gravity and keep resting surface mobs stable.
        airFallGuard.start();
        getLogger().info("FreeLifeMarineMobs 1.11.2 enabled: falling-block gravity, water-surface stabilization, red damage flashes, mounted-only tuning, and natural marine movement are active.");
    }

    @Override
    public void onDisable() {
        if (airFallGuard != null) {
            airFallGuard.shutdown();
        }
        if (showEnhancement != null) {
            showEnhancement.shutdown();
        }
        if (riddenBreach != null) {
            riddenBreach.shutdown();
        }
        if (naturalBehavior != null) {
            naturalBehavior.shutdown();
        }
        if (finalMotion != null) {
            finalMotion.shutdown();
        }
        if (shows != null) {
            shows.shutdown();
        }
        if (mobs != null) {
            mobs.shutdown();
        }
    }
}
