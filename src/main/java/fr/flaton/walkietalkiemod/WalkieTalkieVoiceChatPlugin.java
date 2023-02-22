package fr.flaton.walkietalkiemod;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import fr.flaton.walkietalkiemod.item.WalkieTalkieItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public class WalkieTalkieVoiceChatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return WalkieTalkieMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicPacket);
    }

    private void onMicPacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();

        if (senderConnection == null) {
            return;
        }

        if (!(senderConnection.getPlayer().getPlayer() instanceof PlayerEntity senderPlayer)) {
            return;
        }

        if (hasWalkieTalkieNotActivate(senderPlayer)) {
            return;
        }

        if (hasWalkieTalkieMute(senderPlayer)) {
            return;
        }

        int senderCanal = getCanal(senderPlayer);
        int senderRange = getRange(senderPlayer);

        VoicechatServerApi api = event.getVoicechat();

        for (PlayerEntity receiverPlayer : Objects.requireNonNull(senderPlayer.getServer()).getPlayerManager().getPlayerList()) {

            int receiverCanal = getCanal(receiverPlayer);

            if (receiverPlayer.getUuid().equals(senderPlayer.getUuid())) {
                continue;
            }

            if (hasWalkieTalkieNotActivate(receiverPlayer)) {
                continue;
            }

            if (!receiverPlayer.getPos().isInRange(senderPlayer.getPos(), senderRange)) {
                continue;
            }

            if (receiverCanal != senderCanal) {
                continue;
            }


            // Send audio
            VoicechatConnection connection = api.getConnectionOf(receiverPlayer.getUuid());
            if (connection == null) {
                continue;
            }

            StaticSoundPacket packet = event.getPacket().toStaticSoundPacket();

            api.sendStaticSoundPacketTo(connection, packet);


        }
    }

    private ItemStack getWalkieTalkieItemStack(PlayerEntity player) {

        ItemStack itemStack = null;

        int range = 0;

        for (ItemStack item : player.getInventory().main) {

            if (item.getItem().getClass().equals(WalkieTalkieItem.class) && item.hasNbt()) {

                WalkieTalkieItem walkieTalkieItem = (WalkieTalkieItem) Objects.requireNonNull(getWalkieTalkieItemStack(player)).getItem();

                if (walkieTalkieItem.getRange() > range) {
                    itemStack = item;
                    range = walkieTalkieItem.getRange();
                }
            }
            
        }

        return itemStack;

    }

    private boolean hasWalkieTalkieNotActivate(PlayerEntity player) {

        if (getWalkieTalkieItemStack(player) == null) {
            return true;
        }

        return !Objects.requireNonNull(Objects.requireNonNull(getWalkieTalkieItemStack(player)).getNbt()).getBoolean(WalkieTalkieItem.NBT_KEY_ACTIVATE);
    }

    private int getCanal(PlayerEntity player) {
        return Objects.requireNonNull(Objects.requireNonNull(getWalkieTalkieItemStack(player)).getNbt()).getInt(WalkieTalkieItem.NBT_KEY_CANAL);
    }

    private int getRange(PlayerEntity player) {
        WalkieTalkieItem item = (WalkieTalkieItem) Objects.requireNonNull(getWalkieTalkieItemStack(player)).getItem();
        return item.getRange();
    }

    private boolean hasWalkieTalkieMute(PlayerEntity player) {
        return Objects.requireNonNull(Objects.requireNonNull(getWalkieTalkieItemStack(player)).getNbt()).getBoolean(WalkieTalkieItem.NBT_KEY_MUTE);
    }
}