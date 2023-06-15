package org.cubeville.cvnubman.nubman;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.models.GameRegion;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;
import org.cubeville.cvnubman.CVNubman;

import java.util.*;

public class Nubman extends Game {
    Player nubman;
    BukkitTask proximityTask;
    BukkitTask effectTask;
    ArrayList<Player> ghosts = new ArrayList<>();
    Integer totalPellets = 0;
    Integer eatenPellets = 0;
    Integer currentLevel = 1;
    List<Location> remainingPowerPellets;
    int nubmanEnraged = 0;
    public Nubman(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("base-speed", new GameVariableDouble("The base bonus speed of the nubman"), 0.07);
        addGameVariable("speed-reduction", new GameVariableDouble("The speed the nubman loses per level"), 0.01);
        addGameVariable("speed-bonus", new GameVariableDouble("The speed the nubman gains while enraged"), 0.02);
        addGameVariable("play-area", new GameVariableRegion("The play area for Nubman."));
        addGameVariable("pellets", new GameVariableList<>(GameVariableLocation.class, "The locations of the consumable powerup pellets."));
        addGameVariable("nubman-spawn", new GameVariableLocation("The spawn point for the ghost."));
        addGameVariable("nubman-loadout", new GameVariableString("The loadout for the nubman."));
        addGameVariable("nubman-enraged-loadout", new GameVariableString("The loadout for the nubman in the enraged state."));
        addGameVariableObjectList("ghosts", new HashMap<>(){{
            put("name", new GameVariableString("The name of the ghost."));
            put("chat-color", new GameVariableChatColor("The chat color for the ghost."));
            put("spawn", new GameVariableLocation("The spawn point for the ghost."));
            put("loadout", new GameVariableString("The name of the loadout for the ghost."));
        }});
        addGameVariable("ghost-area", new GameVariableRegion("The region the ghosts spawn in."));
        addGameVariableObjectList("levels", new HashMap<>(){{
            put("layout", new GameVariableRegion("The build to be copied in for this level."));
            put("color", new GameVariableChatColor("The chat color representing this level."));
        }}, "The nubman levels.");
    }

    @Override
    public void onGameStart(Set<Player> set) {
        Random random = new Random();
        nubman = new ArrayList<>(set).get(random.nextInt(set.size()));
        remainingPowerPellets = (List<Location>) getVariable("pellets");
        List<HashMap<String, Object>> ghostTypes = (List<HashMap<String, Object>>) getVariable("ghosts");
        loadLevel(1);
        for (Player player : set) {
            state.put(player, new NubmanState());
            if (player.equals(nubman)) {
                getState(player).isNubman = true;
                player.sendTitle("§fYou are the §e§lNubman!", "§7Eat the pellets and avoid the ghosts!", 10, 60, 10);
                player.teleport((Location) getVariable("nubman-spawn"));
                applyLoadout(player);
            }
            else {
                HashMap<String, Object> ghost = ghostTypes.get(random.nextInt(ghostTypes.size()));
                ghostTypes.remove(ghost);
                ghosts.add(player);
                getState(player).ghostType = ghost;
                player.sendTitle("§fYou are " + (ChatColor) ghost.get("chat-color") + (String) ghost.get("name"), "§7Catch the nubman!", 10, 60, 10);
                player.teleport((Location) ghost.get("spawn"));
                applyLoadout(player);
            }
        }
        startProximityCheck();
        startEffects();
        displayScoreboard();
    }

    private List<String> getMainTeam() {
        List<String> team = new ArrayList<>();
        team.add("main");
        return team;
    }

    private void applyLoadout(Player player) {
        if (nubman == player) {
            double speed = (double) getVariable("base-speed") - ((double) getVariable("speed-reduction") * (currentLevel - 1));
            if (nubmanEnraged > 0) {
                CVLoadouts.getInstance().applyLoadoutToPlayer(player, (String) getVariable("nubman-enraged-loadout"), getMainTeam());
                speed = speed + (double) getVariable("speed-bonus");
            } else {
                CVLoadouts.getInstance().applyLoadoutToPlayer(player, (String) getVariable("nubman-loadout"), getMainTeam());
            }
            ItemMeta meta = player.getInventory().getBoots().getItemMeta();
            meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, new AttributeModifier(UUID.randomUUID(), "nubman-speed", speed, AttributeModifier.Operation.ADD_NUMBER));
            player.getInventory().getBoots().setItemMeta(meta);
        } else {
            CVLoadouts.getInstance().applyLoadoutToPlayer(player, (String) getState(player).ghostType.get("loadout"), getMainTeam());
        }
    }
    private void startProximityCheck() {
        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                Block block = nubman.getLocation().getBlock().getRelative(BlockFace.DOWN, 2);
                if (block.getType().equals(Material.REDSTONE_BLOCK)) {
                    block.setType(Material.AIR);
                    eatenPellets++;
                    nubman.getWorld().playSound(nubman.getLocation(), Sound.ENTITY_GENERIC_EAT, 15.0f, 1.0f);
                    if (eatenPellets >= totalPellets) {
                        progressLevel();
                    }
                    displayScoreboard();
                }
                List<Location> locs = new ArrayList<>(remainingPowerPellets);
                for (Location loc : locs) {
                    if (nubman.getLocation().distance(loc) <= 1) {
                        remainingPowerPellets.remove(loc);
                        nubmanEnraged++;
                        applyLoadout(nubman);
                        nubman.sendTitle("§aYou are §nENRAGED", "§fCatch those ghosts!", 5, 40, 5);
                        for (Player player : ghosts) {
                            player.sendTitle("§cNubman is §nENRAGED", "§fDon't get caught!", 5, 40, 5);
                        }
                        Bukkit.getScheduler().runTaskLater(CVNubman.getInstance(), () -> {
                            if (nubmanEnraged > 0) {
                                nubmanEnraged--;
                                if (nubmanEnraged <= 0) {
                                    nubman.sendTitle("§cYou are no longer enraged", "§fWatch out for ghosts!", 5, 40, 5);
                                    for (Player player : ghosts) {
                                        player.sendTitle("§aNubman is no longer enraged", "§fGo catch nubman!", 5, 40, 5);
                                        applyLoadout(nubman);
                                    }
                                }
                            }
                        }, 200L);
                    }
                }
                for (Player player: ghosts) {
                    if (((GameRegion) getVariable("ghost-area")).containsPlayer(player) && getState(player).isDead) {
                        getState(player).isDead = false;
                        player.setGlowing(false);
                        player.sendTitle("§aHealed!", "", 5, 20, 5);
                    }
                    if (player.getLocation().distance(nubman.getLocation()) < 1 && !getState(player).isDead) {
                        if (nubmanEnraged > 0) {
                            getState(player).isDead = true;
                            player.setGlowing(true);
                            player.sendTitle("§cYou've been caught!", "§fReturn to the center!", 5, 50, 5);
                            player.playSound(player, Sound.ENTITY_GHAST_SCREAM, 15.0f, 1.0f);
                        } else {
                            finishGame();
                            proximityTask.cancel();
                        }
                    }
                }
            }
        }.runTaskTimer(CVNubman.getInstance(), 1L, 1L);
    }

    private void startEffects() {
        effectTask = new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                for (Location loc : remainingPowerPellets) {
                    loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 2, .2, .2, .2, new Particle.DustOptions(Color.RED, 1));
                }
                if (nubmanEnraged > 0 && count % 4 == 0) {
                    nubman.getWorld().playSound(nubman.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 15.0f, 1.0f);
                }
                count++;
            }
        }.runTaskTimer(CVNubman.getInstance(), 5L, 5L);
    }

    @Override
    public void onPlayerLeave(Player player) {
        state.remove(player);
        player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
        ghosts.remove(player);
        if (player == nubman || ghosts.size() <= 0) {
            finishGame();
        }
    }

    @Override
    protected NubmanState getState(Player player) {
        return (NubmanState) state.get(player);
    }

    @Override
    public void onGameFinish() {
        proximityTask.cancel();
        effectTask.cancel();
        sendStatistics();
        for (Player ghost : ghosts) {
            ghost.setGlowing(false);
        }
        for (Player player : state.keySet()) {
            player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
        }
        nubman = null;
        ghosts = new ArrayList<>();
        eatenPellets = 0;
        totalPellets = 0;
        currentLevel = 1;
        nubmanEnraged = 0;
    }
    private void loadLevel(Integer level) {
        HashMap<String, Object> levelData = ((List<HashMap<String, Object>>) getVariable("levels")).get(level - 1);
        GameRegion gameRegion;
        gameRegion = (GameRegion) levelData.get("layout");
        totalPellets = countBlocks(gameRegion, Material.REDSTONE_LAMP);
        GameRegion pasteGameRegion = (GameRegion) getVariable("play-area");
        BlockVector3 min = BlockVector3.at(gameRegion.getMin().getX(), gameRegion.getMin().getY(), gameRegion.getMin().getZ());
        BlockVector3 max = BlockVector3.at(gameRegion.getMax().getX(), gameRegion.getMax().getY(), gameRegion.getMax().getZ());
        BlockVector3 pasteMin = BlockVector3.at(pasteGameRegion.getMin().getX(), pasteGameRegion.getMin().getY(), pasteGameRegion.getMin().getZ());
        CuboidRegion region = new CuboidRegion(min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        try (EditSession session = WorldEdit.getInstance().newEditSession(new BukkitWorld(gameRegion.getMax().getWorld()))) {
            ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(session, region, clipboard, region.getMinimumPoint());
            Operations.complete(forwardExtentCopy);
            Operation operation = new ClipboardHolder(clipboard).createPaste(session).to(pasteMin).build();
            Operations.complete(operation);
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }
    }

    private int countBlocks(GameRegion region, Material blockType) {
        int count = 0;
            for(int x = region.getMin().getBlockX(); x <= region.getMax().getBlockX(); x++) {
                for(int y = region.getMin().getBlockY(); y <= region.getMax().getBlockY(); y++) {
                    for(int z = region.getMin().getBlockZ(); z <= region.getMax().getBlockZ(); z++) {
                        if(region.getMin().getWorld().getBlockAt(x, y, z).getType() == blockType)
                            count++;
                    }
                }
            }
        return count;
    }

    private void displayScoreboard() {
        if (nubman == null) return;
        List<String> scoreboardLines = new ArrayList<>();
        scoreboardLines.add("§eNubman: §f" + nubman.getDisplayName());
        scoreboardLines.add(" §7Current Level: " + getLevels().get(currentLevel - 1).get("color") + currentLevel + "§7/§f" + getLevels().size());
        scoreboardLines.add(" §7Pellets Eaten: §a" + eatenPellets + "§7/§f" + totalPellets);
        scoreboardLines.add("");
        scoreboardLines.add("§fGhosts:");
        for (Player player : ghosts) {
            HashMap<String, Object> ghost = getState(player).ghostType;
            scoreboardLines.add(" §f" + player.getDisplayName() + " §7(" + ghost.get("chat-color") + ghost.get("name") + "§7)");
        }
        Scoreboard scoreboard = GameUtils.createScoreboard(arena, "§e§lNubman", scoreboardLines);
        sendScoreboardToArena(scoreboard);
    }

    private void progressLevel() {
        List<HashMap<String, Object>> levels = getLevels();
        if (currentLevel >= levels.size()) {
            finishGame();
            return;
        }
        nubman.teleport((Location) getVariable("nubman-spawn"));
        for (Player player : ghosts) {
            player.teleport((Location) getState(player).ghostType.get("spawn"));
            getState(player).isDead = false;
            player.setGlowing(false);
        }
        nubmanEnraged = 0;
        remainingPowerPellets = (List<Location>) getVariable("pellets");
        currentLevel++;
        applyLoadout(nubman);
        eatenPellets = 0;
        loadLevel(currentLevel);
    }

    private void sendStatistics() {
        String message = "";
        for (int i = 0; i < getLevels().size(); i++) {
            HashMap<String, Object> level = getLevels().get(i);
            String modifier = "";
            if (currentLevel == i+1) {
                modifier = (ChatColor) level.get("color") + "§n§l";
            } else if (currentLevel > i+1) {
                modifier = String.valueOf((ChatColor) level.get("color"));
            } else {
                modifier = "§7";
            }
            message = message + modifier + i + "§r ";
        }
        String messageAddition = "";
        String initialMessage = "";
        int completedLevels = 0;
        if (currentLevel >= getLevels().size() && eatenPellets >= totalPellets) {
            messageAddition = "\n§a§lFINISHED GAME!";
            completedLevels = currentLevel;
        } else {
            messageAddition = "\n§7Current Level's Pellets Eaten: §a" + eatenPellets + "§7/§f" + totalPellets;
            initialMessage = "§aNubman has been caught!\n";
            completedLevels = currentLevel - 1;
        }
        message = initialMessage + message + "\n\n§7Completed Levels: §a" + completedLevels + messageAddition;
        sendMessageToArena(message);
    }

    private List<HashMap<String, Object>> getLevels() {
        return (List<HashMap<String, Object>>) getVariable("levels");
    }
}