package top.likoslupus.ferrum.core.neoforge;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.likoslupus.ferrum.core.FerrumCore;
import top.likoslupus.ferrum.core.FerrumCoreBootstrap;

/**
 * NeoForge entrypoint: initializes FerrumCore and registers the {@code /ferrum status} command.
 */
@Mod(FerrumCore.MOD_ID)
public final class FerrumCoreNeoForge {

    public FerrumCoreNeoForge(IEventBus modEventBus) {
        FerrumCoreBootstrap.initialize(FMLPaths.GAMEDIR.get());
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ferrum").then(
                        Commands.literal("status").executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(FerrumCoreBootstrap.status()),
                                    false
                            );
                            return 1;
                        })
                )
        );
    }

}
