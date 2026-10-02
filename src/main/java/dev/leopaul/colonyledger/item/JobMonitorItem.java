package dev.leopaul.colonyledger.item;

import dev.leopaul.colonyledger.model.JobPeriod;
import dev.leopaul.colonyledger.network.RequestJobMonitorPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class JobMonitorItem extends Item {
    public JobMonitorItem(Properties properties) { super(properties); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) PacketDistributor.sendToServer(new RequestJobMonitorPayload(-1, JobPeriod.ONE_DAY, true));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
