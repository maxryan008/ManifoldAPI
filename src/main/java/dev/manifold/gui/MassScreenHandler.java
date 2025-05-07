package dev.manifold.gui;

import dev.manifold.mass.MassEntry;
import dev.manifold.network.packets.MassGuiDataS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MassScreenHandler extends AbstractContainerMenu {
    public static MenuType<MassScreenHandler> TYPE;

    public MassScreenHandler(int syncId, Inventory inventory) {
        super(TYPE, syncId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public static void open(ServerPlayer player) {
        List<MassEntry> entries = MassEntry.collect(player.server);
        MassGuiDataS2CPacket packet = new MassGuiDataS2CPacket(entries);
        ServerPlayNetworking.send(player, packet);
    }

    private static <T extends AbstractContainerMenu> MenuType<T> register(String string, MenuType.MenuSupplier<T> menuSupplier) {
        return Registry.register(BuiltInRegistries.MENU, string, new MenuType<>(menuSupplier, FeatureFlags.VANILLA_SET));
    }
}
