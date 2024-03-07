package fr.flaton.walkietalkie;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.audio.AudioConverter;
import fr.flaton.walkietalkie.block.entity.SpeakerBlockEntity;
import fr.flaton.walkietalkie.config.ModConfig;
import fr.flaton.walkietalkie.item.WalkieTalkieItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

@ForgeVoicechatPlugin
public class WalkieTalkieVoiceChatPlugin implements VoicechatPlugin {

    public final static String SPEAKER_CATEGORY = "speakers";

    @Nullable
    public static VoicechatServerApi api;

    @Override
    public String getPluginId() {
        return Constants.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicPacket);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();

        VolumeCategory speakers = api.volumeCategoryBuilder()
                .setId(SPEAKER_CATEGORY)
                .setName("Speakers")
                .setDescription("The volume of all speakers")
                .setIcon(getIcon("assets/walkietalkie/textures/block/speaker.png"))
                .build();
        api.registerVolumeCategory(speakers);
    }

    @Nullable
    private int[][] getIcon(String path) {
        try {
            Enumeration<URL> resources = WalkieTalkieVoiceChatPlugin.class.getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                BufferedImage bufferedImage = ImageIO.read(resources.nextElement().openStream());
                if (bufferedImage.getWidth() != 16) {
                    continue;
                }
                if (bufferedImage.getHeight() != 16) {
                    continue;
                }
                int[][] image = new int[16][16];
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        image[x][y] = bufferedImage.getRGB(x, y);
                    }
                }
                return image;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void onMicPacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();

        if (senderConnection == null) {
            return;
        }

        if (!(senderConnection.getPlayer().getPlayer() instanceof PlayerEntity senderPlayer)) {
            return;
        }

        ItemStack senderItemStack = Util.getWalkieTalkieInHand(senderPlayer);

        if (senderItemStack == null) {
            return;
        }

        if (!isWalkieTalkieActivate(senderItemStack)) {
            return;
        }

        if (isWalkieTalkieMute(senderItemStack)) {
            return;
        }

        int senderCanal = getCanal(senderItemStack);

        SpeakerBlockEntity.getSpeakersActivatedInRange(senderCanal, senderPlayer.getWorld(), senderPlayer.getPos(), getRange(senderItemStack))
                .forEach(speakerBlockEntity -> speakerBlockEntity.playSound(api, event));

        for (PlayerEntity receiverPlayer : Objects.requireNonNull(senderPlayer.getServer()).getPlayerManager().getPlayerList()) {

            if (receiverPlayer.getUuid().equals(senderPlayer.getUuid())) {
                continue;
            }

            if (!ModConfig.crossDimensionsEnabled && !receiverPlayer.getWorld().getDimension().equals(senderPlayer.getWorld().getDimension())) {
                continue;
            }

            ItemStack receiverStack = Util.getWalkieTalkieActivated(receiverPlayer);

            if (receiverStack == null) {
                continue;
            }

            int receiverRange = getRange(receiverStack);
            int receiverCanal = getCanal(receiverStack);

            if (!canBroadcastToReceiver(senderPlayer, receiverPlayer, receiverRange)) {
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

            byte[] data = applyWalkieTalkieEffect(event.getPacket().getOpusEncodedData(), 0.2f, api.createDecoder(), api.createEncoder());

            api.sendStaticSoundPacketTo(connection, event.getPacket().staticSoundPacketBuilder().opusEncodedData(data).build());
        }

        byte[] data = applyWalkieTalkieEffect(event.getPacket().getOpusEncodedData(), 0.1f, api.createDecoder(), api.createEncoder());

        api.sendStaticSoundPacketTo(event.getSenderConnection(), event.getPacket().staticSoundPacketBuilder().opusEncodedData(data).build());
    }

    public static byte[] applyWalkieTalkieEffect(byte[] opusData, float noiseLevel, OpusDecoder decoder, OpusEncoder encoder) {
        short[] pcmData = decoder.decode(opusData);

        int numSamples = pcmData.length / 2;
        short[] noise = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            noise[i] = (short) ((Math.random() - 0.5) * Short.MAX_VALUE);
        }

        short[] pcmDataWithNoise = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            short pcmSample = (short) ((pcmData[2 * i] & 0xFF) | (pcmData[2 * i + 1] << 8));
            int noisySample = (int) (pcmSample + noise[i] * noiseLevel);
            noisySample = Math.min(Short.MAX_VALUE, Math.max(Short.MIN_VALUE, noisySample));
            pcmDataWithNoise[i] = (short) noisySample;
        }

        byte[] opusDataWithNoise = encoder.encode(pcmDataWithNoise);

        decoder.close();
        encoder.close();

        return opusDataWithNoise;
    }

    private int getCanal(ItemStack stack) {
        return Objects.requireNonNull(stack.getNbt()).getInt(WalkieTalkieItem.NBT_KEY_CANAL);
    }

    private int getRange(ItemStack stack) {
        WalkieTalkieItem item = (WalkieTalkieItem) Objects.requireNonNull(stack.getItem());
        return item.getRange();
    }

    private boolean isWalkieTalkieActivate(ItemStack stack) {
        return Objects.requireNonNull(stack.getNbt()).getBoolean(WalkieTalkieItem.NBT_KEY_ACTIVATE);
    }

    private boolean isWalkieTalkieMute(ItemStack stack) {
        return Objects.requireNonNull(stack.getNbt()).getBoolean(WalkieTalkieItem.NBT_KEY_MUTE);
    }

    private boolean canBroadcastToReceiver(PlayerEntity senderPlayer, PlayerEntity receiverPlayer, int receiverRange) {
        World senderWorld = senderPlayer.getWorld();
        World receiverWorld = receiverPlayer.getWorld();

        return Util.canBroadcastToReceiver(senderWorld, receiverWorld, senderPlayer.getPos(), receiverPlayer.getPos(), receiverRange);
    }
}