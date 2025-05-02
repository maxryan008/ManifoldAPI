package dev.manifold.init;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.manifold.ConstructManager;
import dev.manifold.DynamicConstruct;
import dev.manifold.Manifold;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ManifoldCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                literal("manifold")
                        .requires(source -> source.hasPermission(2))

                        // --- /manifold tp sim ---
                        .then(literal("tp")
                                .then(literal("sim")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel simWorld = player.server.getLevel(ManifoldDimensions.SIM_WORLD);
                                            if (simWorld != null) {
                                                player.teleportTo(simWorld, 0, 64, 0, player.getYRot(), player.getXRot());
                                                ctx.getSource().sendSuccess(() -> Component.literal("Teleported to simulation dimension."), true);
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("Simulation dimension not loaded."));
                                            }
                                            return 1;
                                        })
                                )

                                // --- /manifold tp overworld ---
                                .then(literal("overworld")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
                                            if (overworld != null) {
                                                player.teleportTo(overworld, 0, 64, 0, player.getYRot(), player.getXRot());
                                                ctx.getSource().sendSuccess(() -> Component.literal("Teleported back to Overworld."), true);
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("Overworld not found."));
                                            }
                                            return 1;
                                        })
                                )
                        )

                        // --- /manifold constructs list ---
                        .then(literal("constructs")
                                .then(literal("list")
                                        .executes(ctx -> {
                                            CommandSourceStack source = ctx.getSource();
                                            ConstructManager manager = ConstructManager.INSTANCE;

                                            if (manager == null || manager.getConstructs().isEmpty()) {
                                                source.sendFailure(Component.literal("No active constructs found."));
                                                return 0;
                                            }

                                            source.sendSuccess(() -> Component.literal("Listing all active constructs:"), false);

                                            for (Map.Entry<UUID, DynamicConstruct> entry : manager.getConstructs().entrySet()) {
                                                UUID id = entry.getKey();
                                                DynamicConstruct construct = entry.getValue();

                                                String posStr = String.format("(%d, %d, %d)",
                                                        construct.getSimOrigin().getX(),
                                                        construct.getSimOrigin().getY(),
                                                        construct.getSimOrigin().getZ());

                                                // Log to console
                                                //noinspection LoggingPlaceholderCountMatchesArgumentCount
                                                Manifold.LOGGER.debug("UUID: %s, Position: %s%n", id, posStr);

                                                // Create clickable components
                                                MutableComponent uuidComponent = Component.literal("UUID: " + id.toString())
                                                        .withStyle(style -> style
                                                                .withColor(ChatFormatting.AQUA)
                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id.toString()))
                                                        );

                                                MutableComponent posComponent = Component.literal(" Position: " + posStr)
                                                        .withStyle(style -> style
                                                                .withColor(ChatFormatting.GREEN)
                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, posStr))
                                                        );

                                                source.sendSuccess(() -> Component.empty().append(uuidComponent).append(posComponent), false);
                                            }

                                            return 1;
                                        })
                                )

                                // --- /manifold constructs create <block> <pos> ---
                                .then(literal("create")
                                        .then(argument("block", BlockStateArgument.block(context))
                                                .then(argument("pos", Vec3Argument.vec3())
                                                        .executes(ctx -> {
                                                            CommandSourceStack source = ctx.getSource();
                                                            ConstructManager manager = ConstructManager.INSTANCE;

                                                            if (manager == null) {
                                                                source.sendFailure(Component.literal("ConstructManager not initialized."));
                                                                return 0;
                                                            }

                                                            BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
                                                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");

                                                            UUID id = manager.createConstruct(state);
                                                            manager.setPosition(id, pos);

                                                            String msg = "Created DynamicConstruct with UUID: " + id + " at " + pos;
                                                            source.sendSuccess(() -> Component.literal(msg), false);
                                                            Manifold.LOGGER.debug(msg);

                                                            return 1;
                                                        })
                                                )
                                        )
                                )

                                // --- /manifold constructs remove <UUID> ---
                                .then(literal("remove")
                                        .then(argument("id", UuidArgument.uuid())
                                                .executes(ctx -> {
                                                    UUID id = UuidArgument.getUuid(ctx, "id");
                                                    ConstructManager manager = ConstructManager.INSTANCE;

                                                    if (manager.getConstructs().containsKey(id)) {
                                                        manager.removeConstruct(id);
                                                        ctx.getSource().sendSuccess(() ->
                                                                Component.literal("Removed construct: " + id), true);
                                                    } else {
                                                        ctx.getSource().sendFailure(Component.literal("Construct not found: " + id));
                                                    }

                                                    return 1;
                                                })
                                        )
                                )

                                // --- /manifold constructs addBlock <block> <UUID> <pos> ---
                                .then(literal("addBlock")
                                        .then(argument("block", BlockStateArgument.block(context))
                                                .then(argument("uuid", UuidArgument.uuid())
                                                        .then(argument("pos", Vec3Argument.vec3())
                                                                .executes(ctx -> {
                                                                    BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
                                                                    UUID uuid = UuidArgument.getUuid(ctx, "uuid");
                                                                    Vec3 relVec = Vec3Argument.getVec3(ctx, "pos");

                                                                    ConstructManager manager = ConstructManager.INSTANCE;
                                                                    if (manager == null) {
                                                                        ctx.getSource().sendFailure(Component.literal("ConstructManager not initialized."));
                                                                        return 0;
                                                                    }

                                                                    DynamicConstruct construct = manager.getConstructs().get(uuid);
                                                                    if (construct == null) {
                                                                        ctx.getSource().sendFailure(Component.literal("No construct with UUID " + uuid));
                                                                        return 0;
                                                                    }

                                                                    // Convert to relative BlockPos
                                                                    BlockPos rel = new BlockPos((int) relVec.x, (int) relVec.y, (int) relVec.z);

                                                                    // Place the block using ConstructManager
                                                                    manager.placeBlockInConstruct(uuid, rel, state);

                                                                    // Update bounds through ConstructManager helper

                                                                    ctx.getSource().sendSuccess(() -> Component.literal("Block added to construct."), true);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )

                                // --- /manifold constructs velocity add <UUID> <velocity> ---
                                .then(literal("velocity")
                                        .then(literal("add")
                                                .then(argument("uuid", UuidArgument.uuid())
                                                        .then(argument("x", DoubleArgumentType.doubleArg())
                                                                .then(argument("y", DoubleArgumentType.doubleArg())
                                                                        .then(argument("z", DoubleArgumentType.doubleArg())
                                                                                .executes(ctx -> {
                                                                                            double x = DoubleArgumentType.getDouble(ctx, "x");
                                                                                            double y = DoubleArgumentType.getDouble(ctx, "y");
                                                                                            double z = DoubleArgumentType.getDouble(ctx, "z");
                                                                                            UUID uuid = UuidArgument.getUuid(ctx, "uuid");

                                                                                            ConstructManager manager = ConstructManager.INSTANCE;

                                                                                            manager.addVelocity(uuid, new Vec3(x, y, z));

                                                                                            return 1;
                                                                                        }
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )

                                // --- /manifold constructs rotation set <UUID> <x> <y> <z> <w> ---
                                .then(literal("rotation")
                                        .then(literal("set")
                                                .then(argument("uuid", UuidArgument.uuid())
                                                        .then(argument("x", DoubleArgumentType.doubleArg())
                                                                .then(argument("y", DoubleArgumentType.doubleArg())
                                                                        .then(argument("z", DoubleArgumentType.doubleArg())
                                                                                .then(argument("w", DoubleArgumentType.doubleArg())
                                                                                        .executes(ctx -> {
                                                                                            UUID uuid = UuidArgument.getUuid(ctx, "uuid");
                                                                                            double x = DoubleArgumentType.getDouble(ctx, "x");
                                                                                            double y = DoubleArgumentType.getDouble(ctx, "y");
                                                                                            double z = DoubleArgumentType.getDouble(ctx, "z");
                                                                                            double w = DoubleArgumentType.getDouble(ctx, "w");

                                                                                            ConstructManager.INSTANCE.setRotationalVelocity(uuid, new Quaternionf((float) x, (float) y, (float) z, (float) w));

                                                                                            return 1;
                                                                                        })
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }
}
