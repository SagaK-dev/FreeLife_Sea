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
    private MarineAutonomousMotionCommitter autonomousMotion;
    private MarineBedrockFallbackRenderer bedrockRenderer;

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
        autonomousMotion = new MarineAutonomousMotionCommitter(this, mobs);
        MarineDamageFlash damageFlash = new MarineDamageFlash(this);
        bedrockRenderer = new MarineBedrockFallbackRenderer(this, mobs, damageFlash);
        MarineCommand command = new MarineCommand(this, mobs, food, shows);

        registerCommand("marine", command);
        registerCommand("freelifesea", command);

        getServer().getPluginManager().registerEvents(new MarineMobListener(mobs, damageFlash), this);
        mobs.start();
        shows.start();
        finalMotion.start();
        naturalBehavior.start();
        riddenBreach.start();
        showEnhancement.start();
        airFallGuard.start();
        autonomousMotion.start();
        bedrockRenderer.start();
        getLogger().info("FreeLifeMarineMobs 1.12.7 enabled: faster large-body autonomous swimming and Geyser/Floodgate Bedrock fallback models are active.");
    }

    private void registerCommand(String name, MarineCommand command) {
        PluginCommand registered = getCommand(name);
        if (registered == null) {
            throw new IllegalStateException("Command '" + name + "' is missing from plugin.yml");
        }
        registered.setExecutor(command);
        registered.setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (bedrockRenderer != null) bedrockRenderer.shutdown();
        if (autonomousMotion != null) autonomousMotion.shutdown();
        if (airFallGuard != null) airFallGuard.shutdown();
        if (showEnhancement != null) showEnhancement.shutdown();
        if (riddenBreach != null) riddenBreach.shutdown();
        if (naturalBehavior != null) naturalBehavior.shutdown();
        if (finalMotion != null) finalMotion.shutdown();
        if (shows != null) shows.shutdown();
        if (mobs != null) mobs.shutdown();
    }
}
