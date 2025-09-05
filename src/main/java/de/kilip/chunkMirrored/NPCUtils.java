package de.kilip.chunkMirrored;

import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;

public class NPCUtils {

    public static ClientInformation dummyClientInformation() {
        return new ClientInformation(
                "en_us",
                10,
                ChatVisiblity.FULL,
                true,
                0,
                HumanoidArm.RIGHT,
                false,
                false,
                ParticleStatus.MINIMAL
        );
    }

}
