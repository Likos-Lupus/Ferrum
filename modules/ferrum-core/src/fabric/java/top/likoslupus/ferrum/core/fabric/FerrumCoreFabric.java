package top.likoslupus.ferrum.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.likoslupus.ferrum.core.FerrumCoreBootstrap;

/**
 * Fabric entrypoint: initializes FerrumCore and registers the {@code /ferrum status} command.
 */
public final class FerrumCoreFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FerrumCoreBootstrap.initialize(FabricLoader.getInstance().getGameDir());
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) ->
                dispatcher.register(
                        Commands.literal("ferrum").then(
                                Commands.literal("status").executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(FerrumCoreBootstrap.status()),
                                            false
                                    );
                                    return 1;
                                })
                        )
                )
        );
    }

}
