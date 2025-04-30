package dev.manifold;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.OptionalInt;

public class ConstructContainerMenu extends AbstractContainerMenu {
    private final AbstractContainerMenu wrapped;

    public ConstructContainerMenu(AbstractContainerMenu wrapped) {
        super(wrapped.getType(), wrapped.containerId);
        this.wrapped = wrapped;
    }

    @Override
    public boolean stillValid(Player player) {
        return true; //todo add distance variable from construct
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return wrapped.quickMoveStack(player, i);
    }

    @Override
    public void removed(Player player) {
        wrapped.removed(player);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return wrapped.getItems();
    }

    @Override
    public MenuType<?> getType() {
        return wrapped.getType();
    }

    @Override
    public Slot getSlot(int i) {
        return wrapped.getSlot(i);
    }

    @Override
    public ItemStack getCarried() {
        return wrapped.getCarried();
    }

    @Override
    public int getStateId() {
        return wrapped.getStateId();
    }

    @Override
    public int incrementStateId() {
        return wrapped.incrementStateId();
    }

    @Override
    public OptionalInt findSlot(Container container, int i) {
        return wrapped.findSlot(container, i);
    }

    @Override
    public void transferState(AbstractContainerMenu abstractContainerMenu) {
        wrapped.transferState(abstractContainerMenu);
    }

    @Override
    public void resumeRemoteUpdates() {
        wrapped.resumeRemoteUpdates();
    }

    @Override
    public void suppressRemoteUpdates() {
        wrapped.suppressRemoteUpdates();
    }

    @Override
    public void setCarried(ItemStack itemStack) {
        wrapped.setCarried(itemStack);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return wrapped.canDragTo(slot);
    }

    @Override
    public void setData(int i, int j) {
        wrapped.setData(i, j);
    }

    @Override
    public void initializeContents(int i, List<ItemStack> list, ItemStack itemStack) {
        wrapped.initializeContents(i, list, itemStack);
    }

    @Override
    public void setItem(int i, int j, ItemStack itemStack) {
        wrapped.setItem(i, j, itemStack);
    }

    @Override
    public void slotsChanged(Container container) {
        wrapped.slotsChanged(container);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack itemStack, Slot slot) {
        return wrapped.canTakeItemForPickAll(itemStack, slot);
    }

    @Override
    public void clicked(int i, int j, ClickType clickType, Player player) {
        wrapped.clicked(i, j, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int i) {
        return wrapped.clickMenuButton(player, i);
    }

    @Override
    public void setRemoteCarried(ItemStack itemStack) {
        wrapped.setRemoteCarried(itemStack);
    }

    @Override
    public void setRemoteSlotNoCopy(int i, ItemStack itemStack) {
        wrapped.setRemoteSlotNoCopy(i, itemStack);
    }

    @Override
    public void setRemoteSlot(int i, ItemStack itemStack) {
        wrapped.setRemoteSlot(i, itemStack);
    }

    @Override
    public void broadcastFullState() {
        wrapped.broadcastFullState();
    }

    @Override
    public void broadcastChanges() {
        wrapped.broadcastChanges();
    }

    @Override
    public void removeSlotListener(ContainerListener containerListener) {
        wrapped.removeSlotListener(containerListener);
    }

    @Override
    public void sendAllDataToRemote() {
        wrapped.sendAllDataToRemote();
    }

    @Override
    public void setSynchronizer(ContainerSynchronizer containerSynchronizer) {
        wrapped.setSynchronizer(containerSynchronizer);
    }

    @Override
    public void addSlotListener(ContainerListener containerListener) {
        wrapped.addSlotListener(containerListener);
    }

    @Override
    public boolean isValidSlotIndex(int i) {
        return wrapped.isValidSlotIndex(i);
    }
}
