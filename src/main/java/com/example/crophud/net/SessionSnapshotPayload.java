package com.example.crophud.net;

import com.example.crophud.CropHudMod;
import com.example.crophud.hud.HudAnchor;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;

public record SessionSnapshotPayload(long activeMillis, int harvestedUnits, BigDecimal totalValue, String hudAnchor, boolean active, String currentCropId) implements CustomPayload {
    public static final Id<SessionSnapshotPayload> ID = new Id<>(Identifier.of(CropHudMod.MOD_ID, "session_snapshot"));
    public static final PacketCodec<RegistryByteBuf, SessionSnapshotPayload> CODEC = PacketCodec.of(
            SessionSnapshotPayload::write,
            SessionSnapshotPayload::read
    );

    private static SessionSnapshotPayload read(RegistryByteBuf buf) {
        long activeMillis = buf.readVarLong();
        int harvestedUnits = buf.readVarInt();
        BigDecimal totalValue = new BigDecimal(buf.readString());
        String hudAnchor = buf.readString();
        boolean active = buf.readBoolean();
        String currentCropId = buf.readString();
        return new SessionSnapshotPayload(activeMillis, harvestedUnits, totalValue, hudAnchor, active, currentCropId);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarLong(activeMillis);
        buf.writeVarInt(harvestedUnits);
        buf.writeString(totalValue.toPlainString());
        buf.writeString(HudAnchor.fromId(hudAnchor).id());
        buf.writeBoolean(active);
        buf.writeString(currentCropId);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
