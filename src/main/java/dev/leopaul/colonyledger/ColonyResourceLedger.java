package dev.leopaul.colonyledger;

import dev.leopaul.colonyledger.item.LedgerItem;
import dev.leopaul.colonyledger.item.JobMonitorItem;
import dev.leopaul.colonyledger.item.HousingManagerItem;
import dev.leopaul.colonyledger.config.HousingConfig;
import dev.leopaul.colonyledger.integration.minecolonies.JobStatusTracker;
import dev.leopaul.colonyledger.network.LedgerNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ColonyResourceLedger.MOD_ID)
public final class ColonyResourceLedger {
    public static final String MOD_ID = "colonyresourceledger";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredHolder<Item, LedgerItem> LEDGER = ITEMS.register(
            "colony_resource_ledger", () -> new LedgerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, JobMonitorItem> JOB_MONITOR = ITEMS.register(
            "colony_job_monitor", () -> new JobMonitorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, HousingManagerItem> HOUSING_MANAGER = ITEMS.register(
            "colony_housing_manager", () -> new HousingManagerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "ledger", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.colonyresourceledger"))
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .icon(() -> LEDGER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(LEDGER.get());
                        output.accept(JOB_MONITOR.get());
                        output.accept(HOUSING_MANAGER.get());
                    }).build());

    public ColonyResourceLedger(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, HousingConfig.SPEC);
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(LedgerNetwork::register);
        NeoForge.EVENT_BUS.addListener(JobStatusTracker::onLevelTick);
    }
}
