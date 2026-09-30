package net.multyfora.compat.mixin;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SawBlockEntity.class)
public class SawBlockEntityMixin {

    @Inject(method = "isSawable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsSawable(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof io.github.mortuusars.envelope.world.block.PackageBlock) {
            cir.setReturnValue(true);
        }
    }
}
