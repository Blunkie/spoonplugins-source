package net.runelite.client.plugins.entityhiderplus;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.util.Text;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.WildcardMatcher;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.*;

@Extension
@PluginDescriptor(
        name = "<html><font color=#25c550>[S] Entity Hider Plus",
        enabledByDefault = false,
        description = "Hide dead NPCs animations",
        tags = {"npcs", "spoon", ""}
)
public class EntityHiderPlusPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private EntityHiderPlusConfig config;

    @Inject
    private EntityHiderPlusOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Hooks hooks;

    private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

    @Provides
    EntityHiderPlusConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EntityHiderPlusConfig.class);
    }

    public ArrayList<Integer> hiddenIndices;
    public ArrayList<Integer> animationHiddenIndices;
    public Set<String> hideAliveNPCsName;
    public Set<Integer> hideAliveNPCsID;
    public Set<Integer> hideNPCsOnAnimationID;
    public Set<String> hideNPCsOnDeathName;
    public Set<Integer> hideNPCsOnDeathID;
    public Set<String> blacklistName;
    public Set<Integer> blacklistID;
    public Set<Integer> hideGraphicsObjects;

    public ArrayList<Integer> totemIDs = new ArrayList<>(Arrays.asList(9434, 9435, 9436, 9437, 9438, 9439, 9440, 9441, 9442, 9443, 9444, 9445));
    public ArrayList<Integer> olmIDs = new ArrayList<>(Arrays.asList(7550, 7551, 7552, 7553, 7554, 7555, 7556, 7557));

    @Override
    protected void startUp() {
        hiddenIndices = new ArrayList<>();
        animationHiddenIndices = new ArrayList<>();
        updateConfig();
        overlayManager.add(overlay);
        hooks.registerRenderableDrawListener(drawListener);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            clearHiddenNpcs();
        }
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        clearHiddenNpcs();
        hiddenIndices = null;
        animationHiddenIndices = null;
        hooks.unregisterRenderableDrawListener(drawListener);
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        for (NPC npc : client.getNpcs()) {
            if (npc != null) {
                if (!olmIDs.contains(npc.getId()) && !totemIDs.contains(npc.getId())) {
                    if (((npc.getName() != null && matchWildCards(hideAliveNPCsName, Text.standardize(npc.getName())))
                            || (hideAliveNPCsID.contains(npc.getId()))
                            || (hideNPCsOnAnimationID.contains(npc.getAnimation()))
                            || (config.hideDeadNPCs() && npc.getHealthRatio() == 0 && npc.getName() != null && !matchWildCards(blacklistName, Text.standardize(npc.getName())) && !blacklistID.contains(npc.getId()))
                            || (npc.getHealthRatio() == 0 && npc.getName() != null && matchWildCards(hideNPCsOnDeathName, Text.standardize(npc.getName())))
                            || (npc.getHealthRatio() == 0 && hideNPCsOnDeathID.contains(npc.getId())))) {
                        if (!hiddenIndices.contains(npc.getIndex())) {
                            setHiddenNpc(npc, true);
                        }
                    }
                }

                if (animationHiddenIndices.contains(npc.getIndex()) && !hideNPCsOnAnimationID.contains(npc.getAnimation())) {
                    if (hiddenIndices.contains(npc.getIndex())) {
                        setHiddenNpc(npc, false);
                    }
                }

                if (hiddenIndices.contains(npc.getIndex()) && (!animationHiddenIndices.contains(npc.getIndex()) || !hideNPCsOnAnimationID.contains(npc.getAnimation()))
                        && !hideAliveNPCsID.contains(npc.getId()) && (npc.getName() != null && !matchWildCards(hideAliveNPCsName, Text.standardize(npc.getName())))
                        && (!npc.isDead() || !config.hideDeadNPCs())) {
                    System.out.println("Unhide: " + npc.getName());
                    setHiddenNpc(npc, false);
                }
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (hiddenIndices.contains(event.getNpc().getIndex())) {
            setHiddenNpc(event.getNpc(), false);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("ehplus")) {
            updateConfig();
        }
    }

    private void updateConfig() {
        hideAliveNPCsName = new HashSet<>();
        hideAliveNPCsID = new HashSet<>();
        hideNPCsOnAnimationID = new HashSet<>();
        hideNPCsOnDeathName = new HashSet<>();
        hideNPCsOnDeathID = new HashSet<>();
        blacklistID = new HashSet<>();
        blacklistName = new HashSet<>();
        hideGraphicsObjects = new HashSet<>();

        for (String s : Text.COMMA_SPLITTER.split(config.hideAliveName().toLowerCase())) {
            hideAliveNPCsName.add(s);
        }
        for (String s : Text.COMMA_SPLITTER.split(config.hideAliveId())) {
            try {
                hideAliveNPCsID.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }

        }
        for (String s : Text.COMMA_SPLITTER.split(config.hideAnimation())) {
            try {
                hideNPCsOnAnimationID.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }

        }
        for (String s : Text.COMMA_SPLITTER.split(config.hideDeadName().toLowerCase())) {
            hideNPCsOnDeathName.add(s);
        }
        for (String s : Text.COMMA_SPLITTER.split(config.hideDeadID())) {
            try {
                hideNPCsOnDeathID.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }

        }
        for (String s : Text.COMMA_SPLITTER.split(config.ignoreNPCS().toLowerCase())) {
            blacklistName.add(s);
        }
        for (String s : Text.COMMA_SPLITTER.split(config.ignoreNPCId())) {
            try {
                blacklistID.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }

        }
        for (String s : Text.COMMA_SPLITTER.split(config.hideGraphicsObjects())) {
            try {
                hideGraphicsObjects.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void setHiddenNpc(NPC npc, boolean hidden) {
        if (hidden) {
            hiddenIndices.add(npc.getIndex());
            if (hideNPCsOnAnimationID.contains(npc.getAnimation())) {
                animationHiddenIndices.add(npc.getIndex());
            }
        } else {
            if (hiddenIndices.contains(npc.getIndex()))
            {
                hiddenIndices.remove((Integer) npc.getIndex());
            }

            if (animationHiddenIndices.contains(npc.getIndex()))
            {
                animationHiddenIndices.remove((Integer) npc.getIndex());
            }
        }
    }

    private void clearHiddenNpcs() {
        hiddenIndices.clear();
        animationHiddenIndices.clear();
    }

    private boolean matchWildCards(Set<String> items, String pattern) {
        boolean matched = false;
        for (final String item : items) {
            matched = WildcardMatcher.matches(item, pattern);
            if (matched) {
                break;
            }
        }
        return matched;
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        int type = event.getType();
        int id = event.getIdentifier();

        if (this.config.hideAttackDead()) {
            try {
                if(type >= 7 && type <= 13 && type != 8){
                    NPC npc = this.client.getCachedNPCs()[id];
                        if (npc != null && !olmIDs.contains(npc.getId()) && !totemIDs.contains(npc.getId())){
                            if (npc.getName() != null && npc.isDead()) {
                                String name = npc.getName().toLowerCase();
                                if (!blacklistID.contains(id) && !blacklistName.contains(name)) {
                                    for(String str : blacklistName){
                                        if(str.contains("*") && ((str.startsWith("*") && str.endsWith("*") && name.contains(str.replace("*", ""))) || (str.startsWith("*") && name.endsWith(str.replace("*", "")))
                                            || name.startsWith(str.replace("*", "")))){
                                        return;
                                    }
                                }
                                MenuEntry[] entries = this.client.getMenuEntries();
                                MenuEntry[] newEntries = new MenuEntry[entries.length - 1];
                                System.arraycopy(entries, 0, newEntries, 0, newEntries.length);
                                this.client.setMenuEntries(newEntries);
                            }
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored){}
        }
    }

    @VisibleForTesting
    boolean shouldDraw(Renderable renderable, boolean drawingUI)
    {
        if (renderable instanceof NPC)
        {
            NPC npc = (NPC) renderable;
            return !hiddenIndices.contains(npc.getIndex()) && !animationHiddenIndices.contains(npc.getIndex());
        }
        else if (renderable instanceof GraphicsObject)
        {
            GraphicsObject graphicsObject = (GraphicsObject) renderable;
            return !hideGraphicsObjects.contains(graphicsObject.getId());
        }

        return true;
    }
}
