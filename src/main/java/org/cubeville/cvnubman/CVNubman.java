package org.cubeville.cvnubman;

import org.bukkit.plugin.java.JavaPlugin;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvnubman.nubman.Nubman;

public final class CVNubman extends JavaPlugin {

    private static CVNubman instance;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        CVGames.gameManager().registerGame("nubman", Nubman::new);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static CVNubman getInstance() { return instance; }

}
