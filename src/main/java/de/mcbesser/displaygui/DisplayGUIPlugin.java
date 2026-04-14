package de.mcbesser.displaygui;

import de.mcbesser.displaygui.command.DisplayGUICommand;
import de.mcbesser.displaygui.display.DisplayEntityManager;
import de.mcbesser.displaygui.feature.crafting.CraftingBannerListener;
import de.mcbesser.displaygui.feature.crafting.CraftingBannerManager;
import de.mcbesser.displaygui.feature.crafting.CraftingMenuListener;
import de.mcbesser.displaygui.feature.crafting.TitlePromptListener;
import de.mcbesser.displaygui.sidebar.DisplaySidebar;
import de.mcbesser.displaygui.sidebar.DisplaySidebarListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DisplayGUIPlugin extends JavaPlugin {
    private DisplayEntityManager displayEntityManager;
    private CraftingBannerManager craftingBannerManager;
    private TitlePromptListener titlePromptListener;
    private DisplaySidebar displaySidebar;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.displayEntityManager = new DisplayEntityManager(this);
        this.craftingBannerManager = new CraftingBannerManager(this, displayEntityManager);
        this.titlePromptListener = new TitlePromptListener(craftingBannerManager);
        this.displaySidebar = new DisplaySidebar(this);

        craftingBannerManager.load();
        displayEntityManager.start();
        craftingBannerManager.start();
        displaySidebar.start();
        craftingBannerManager.setTitlePromptListener(titlePromptListener);

        getServer().getPluginManager().registerEvents(new CraftingBannerListener(this, craftingBannerManager), this);
        getServer().getPluginManager().registerEvents(new CraftingMenuListener(craftingBannerManager), this);
        getServer().getPluginManager().registerEvents(titlePromptListener, this);
        getServer().getPluginManager().registerEvents(new DisplaySidebarListener(displaySidebar), this);

        DisplayGUICommand command = new DisplayGUICommand(this, craftingBannerManager);
        Objects.requireNonNull(getCommand("displaygui"), "displaygui command").setExecutor(command);
        Objects.requireNonNull(getCommand("displaygui"), "displaygui command").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (craftingBannerManager != null) {
            craftingBannerManager.shutdown();
        }
        if (displaySidebar != null) {
            displaySidebar.stop();
        }
        if (displayEntityManager != null) {
            displayEntityManager.shutdown();
        }
    }

    public DisplayEntityManager getDisplayEntityManager() {
        return displayEntityManager;
    }

    public CraftingBannerManager getCraftingBannerManager() {
        return craftingBannerManager;
    }
}
