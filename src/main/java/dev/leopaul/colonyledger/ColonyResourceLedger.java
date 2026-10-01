package dev.leopaul.colonyledger;

import dev.leopaul.colonyledger.item.LedgerItem;
import dev.leopaul.colonyledger.network.LedgerNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ColonyResourceLedger.MOD_ID)
public final class ColonyResourceLedger {
    public static final String MOD_ID = "colonyresourceledger";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredHolder<Item, LedgerItem> LEDGER = ITEMS.register(
            "colony_resource_ledger", () -> new LedgerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "ledger", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.colonyresourceledger"))
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .icon(() -> LEDGER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(LEDGER.get())).build());

    public ColonyResourceLedger(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(LedgerNetwork::register);
    }
}
