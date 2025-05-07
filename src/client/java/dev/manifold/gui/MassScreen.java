package dev.manifold.gui;

import dev.manifold.mass.MassEntry;
import dev.manifold.mass.MassManager;
import dev.manifold.network.packets.MassReloadC2SPacket;
import dev.manifold.network.packets.MassUpdateC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MassScreen extends Screen implements MenuAccess<AbstractContainerMenu> {
    private List<MassEntry> allEntries;
    private List<MassEntry> filteredEntries = new ArrayList<>();
    private final List<MassRow> rows = new ArrayList<>();

    private final Set<Item> autoEnabledItems = new HashSet<>();

    private int selectedCategory = 0; // 0 = All, 1 = Unset, 2 = Base
    private EditBox searchBox;

    private int scrollOffset = 0;
    private static final int rowHeight = 26;
    private int maxVisibleRows;

    public MassScreen(List<MassEntry> entries) {
        super(Component.literal("Mass Editor"));
        this.allEntries = new ArrayList<>(entries);
    }

    @Override
    protected void init() {
        int top = 10;
        int sidePadding = 10;
        int innerPadding = 5;
        int searchBoxWidth = 160;
        int buttonCount = 5;

        int availableWidth = this.width - sidePadding * 2 - searchBoxWidth - innerPadding * (buttonCount + 1);
        int buttonWidth = availableWidth / buttonCount;

        int x = sidePadding;

        this.searchBox = new EditBox(this.font, x, top, searchBoxWidth, 20, Component.literal("Search"));
        this.searchBox.setResponder(this::updateFilter);
        this.addRenderableWidget(this.searchBox);
        x += searchBoxWidth + innerPadding;

        this.addRenderableWidget(Button.builder(Component.literal("All"), b -> setCategory(0)).bounds(x, top, buttonWidth, 20).build());
        x += buttonWidth + innerPadding;

        this.addRenderableWidget(Button.builder(Component.literal("Unset"), b -> setCategory(1)).bounds(x, top, buttonWidth, 20).build());
        x += buttonWidth + innerPadding;

        this.addRenderableWidget(Button.builder(Component.literal("Base"), b -> setCategory(2)).bounds(x, top, buttonWidth, 20).build());
        x += buttonWidth + innerPadding;

        this.addRenderableWidget(Button.builder(Component.literal("Reload"), b -> {
            ClientPlayNetworking.send(new MassReloadC2SPacket());
        }).bounds(x, top, buttonWidth, 20).build());
        x += buttonWidth + innerPadding;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x, top, buttonWidth, 20).build());

        updateFilter("");

        this.maxVisibleRows = (this.height - 60) / rowHeight;

        setCategory(0);
    }

    private void setCategory(int category) {
        this.selectedCategory = category;
        updateFilter(this.searchBox.getValue());
    }

    private void updateFilter(String searchText) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        Predicate<MassEntry> filter = switch (selectedCategory) {
            case 1 -> e -> MassManager.isBase(e.item(), server) && !MassManager.isOverridden(e.item());
            case 2 -> e -> MassManager.isBase(e.item(), server);
            default -> e -> true;
        };

        this.filteredEntries = allEntries.stream()
                .filter(e -> Component.translatable(e.item().getDescriptionId())
                        .getString()
                        .toLowerCase()
                        .contains(searchText.toLowerCase()))
                .filter(filter)
                .sorted(Comparator.comparing(e -> e.item().getDescriptionId()))
                .collect(Collectors.toList());

        this.scrollOffset = 0;
        refreshVisibleRows(server);
    }

    private void refreshVisibleRows(MinecraftServer server) {
        // Only remove dynamic row widgets
        for (var widget : new ArrayList<>(this.children())) {
            if (widget instanceof MassRow row) {
                row.dispose(this);
                this.removeWidget(row);
            }
        }
        this.rows.clear();

        int from = Math.min(scrollOffset, Math.max(0, filteredEntries.size() - maxVisibleRows));
        int to = Math.min(filteredEntries.size(), from + maxVisibleRows);

        int y = 40;
        for (int i = from; i < to; i++) {
            MassEntry entry = filteredEntries.get(i);
            MassRow row = new MassRow(entry, 10, y, this.width - 20, server, this);
            this.addRenderableWidget(row);
            this.rows.add(row);
            y += rowHeight;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawString(this.font, "Mass Editor", this.width / 2 - 40, 2, 0xFFFFFF);

        // Render all widgets first
        super.render(graphics, mouseX, mouseY, delta);

        // Then render the scrollbar *after* everything else
        if (filteredEntries.size() > maxVisibleRows) {
            int totalHeight = maxVisibleRows * rowHeight;
            int fullHeight = filteredEntries.size() * rowHeight;

            float scrollPercent = (float) scrollOffset / (filteredEntries.size() - maxVisibleRows);
            int barHeight = Math.max(20, (int) ((float) totalHeight * maxVisibleRows / filteredEntries.size()));
            int barY = 40 + (int) (scrollPercent * (totalHeight - barHeight));

            graphics.fill(this.width - 6, barY, this.width - 2, barY + barHeight, 0xFF888888);
        }

        // Render item tooltips manually
        for (MassRow row : rows) {
            if (row.isHoveringItem(mouseX, mouseY)) {
                graphics.renderTooltip(this.font, row.getItemStack(), mouseX, mouseY);
            }
        }
    }

    public void refreshEntries(List<MassEntry> entries) {
        this.allEntries = entries;

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();

        Predicate<MassEntry> filter = switch (selectedCategory) {
            case 1 -> e -> MassManager.isBase(e.item(), server) && !MassManager.isOverridden(e.item());
            case 2 -> e -> MassManager.isBase(e.item(), server);
            default -> e -> true;
        };

        this.filteredEntries = allEntries.stream()
                .filter(e -> Component.translatable(e.item().getDescriptionId())
                        .getString()
                        .toLowerCase()
                        .contains(this.searchBox.getValue().toLowerCase()))
                .filter(filter)
                .sorted(Comparator.comparing(e -> e.item().getDescriptionId()))
                .collect(Collectors.toList());

        this.refreshVisibleRows(server);
    }

    private static class MassRow extends AbstractWidget {
        private final MassEntry entry;
        private final EditBox massField;
        private final Button setButton;
        private final Button autoToggle;
        private boolean autoEnabled;
        private final MinecraftServer server;
        private final List<GuiEventListener> children;

        private final int itemX, itemY, itemSize = 16;

        public MassRow(MassEntry entry, int x, int y, int width, MinecraftServer server, MassScreen parent) {
            super(x, y, width, 20, Component.empty());
            this.itemX = x;
            this.itemY = y;
            this.server = server;
            this.entry = entry;
            this.autoEnabled = MassManager.isAuto(entry.item());

            ItemStack stack = new ItemStack(entry.item());
            this.massField = new EditBox(Minecraft.getInstance().font, x + 40, y, 60, 20, Component.literal("Mass"));
            if (true) { //todo fix up
                this.massField.setValue(String.valueOf(entry.mass()));
            } else {
                this.massField.setValue("1000");
            }

            this.setButton = Button.builder(Component.literal("Set"), b -> {
                try {
                    double val = Double.parseDouble(massField.getValue());
                    ClientPlayNetworking.send(new MassUpdateC2SPacket(entry.item(), val, autoEnabled));
                } catch (NumberFormatException ignored) {}
            }).bounds(x + 105, y, 40, 20).build();

            boolean isBase = MassManager.isBase(entry.item(), server);
            this.autoToggle = Button.builder(Component.literal("Auto"), b -> {
                if (!isBase) {
                    autoEnabled = !autoEnabled;
                    if (autoEnabled) parent.autoEnabledItems.add(entry.item());
                    else parent.autoEnabledItems.remove(entry.item());
                }
            }).bounds(x + 150, y, 40, 20).build();

            this.children = List.of(massField, setButton, autoToggle);

            parent.addWidget(this.massField);
            parent.addWidget(this.setButton);
            parent.addWidget(this.autoToggle);
        }

        public ItemStack getItemStack() {
            return new ItemStack(entry.item());
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = new ItemStack(entry.item());

            // Render item slot
            graphics.renderItem(stack, this.getX(), this.getY());
            graphics.renderItemDecorations(mc.font, stack, this.getX(), this.getY());

            // Set mass color
            int color;
            String mode;
            if (MassManager.isBase(entry.item(), server)) {
                if (!MassManager.isOverridden(entry.item())) {
                    mode = "Default";
                    color = 0xFFFF0000;
                }
                else {
                    mode = "Manual";
                    color = 0xFF0000FF;
                }
            } else {
                mode = MassManager.isAuto(entry.item()) ? "Auto" : "Manual";
                color = MassManager.isAuto(entry.item()) ? 0xFF00FF00 : 0xFF0000FF;
                if (!((mode.equals("Auto") && autoEnabled) || (mode.equals("Manual") && !autoEnabled))) {
                    if (autoEnabled) {
                        mode = "Auto";
                        color = 0xFF00FF00;
                    } else {
                        mode = "Manual";
                        color = 0xFF0000FF;
                    }
                }
            }

            this.massField.setTextColor(color);
            this.massField.render(graphics, mouseX, mouseY, delta);
            this.setButton.render(graphics, mouseX, mouseY, delta);
            this.autoToggle.active = !MassManager.isBase(entry.item(), server);
            this.autoToggle.render(graphics, mouseX, mouseY, delta);

            // Mode column
            graphics.drawString(mc.font, mode, this.getX() + 195, this.getY() + 6, 0xFFFFFF);

            // Base column
            graphics.drawString(mc.font, MassManager.isBase(entry.item(), server) ? "Base" : "Child", this.getX() + 250, this.getY() + 6, 0xFFFFFF);

            // Tooltip for Auto button
            if (!this.autoToggle.active && isHovered(mouseX, mouseY, this.autoToggle)) {
                graphics.renderTooltip(mc.font, Component.literal("Auto is disabled for base items."), mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            //todo not sure what this one does
        }

        private boolean isHovered(int mouseX, int mouseY, Button button) {
            return mouseX >= button.getX() && mouseY >= button.getY()
                    && mouseX < button.getX() + button.getWidth() && mouseY < button.getY() + button.getHeight();
        }

        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (GuiEventListener child : children()) {
                if (child.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (GuiEventListener child : children()) {
                if (child.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        public void dispose(MassScreen screen) {
            screen.removeWidget(this.massField);
            screen.removeWidget(this.setButton);
            screen.removeWidget(this.autoToggle);
        }

        public boolean isHoveringItem(int mouseX, int mouseY) {
            return mouseX >= itemX && mouseY >= itemY && mouseX < itemX + itemSize && mouseY < itemY + itemSize;
        }
    }

    @Override
    public AbstractContainerMenu getMenu() {
        return Minecraft.getInstance().player.containerMenu;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount, double horizontalAmount) {
        if (filteredEntries.size() <= maxVisibleRows) return false;

        int scrollStep = horizontalAmount > 0 ? -1 : 1;
        scrollOffset = Mth.clamp(scrollOffset + scrollStep, 0, filteredEntries.size() - maxVisibleRows);

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            refreshVisibleRows(server);
        }

        return true;
    }
}