package org.aincraft.chestfind.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.aincraft.chestfind.listener.ContainerCloseCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept screen handler close events for container indexing.
 */
@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {

    @Inject(method = "onClosed", at = @At("HEAD"))
    private void onClosedHead(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ContainerCloseCallback.CONTAINER_CLOSED.invoker()
                    .onContainerClosed(serverPlayer, (ScreenHandler) (Object) this);
        }
    }
}
