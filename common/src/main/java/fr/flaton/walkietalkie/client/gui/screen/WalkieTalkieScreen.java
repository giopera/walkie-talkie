package fr.flaton.walkietalkie.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.flaton.walkietalkie.Constants;
import fr.flaton.walkietalkie.client.gui.widget.ToggleImageButton;
import fr.flaton.walkietalkie.item.WalkieTalkieItem;
import fr.flaton.walkietalkie.network.ModMessages;
import io.netty.buffer.Unpooled;
import me.shedaniel.architectury.networking.NetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

public class WalkieTalkieScreen extends Screen {

    private static WalkieTalkieScreen instance;

    private final int xSize = 195;
    private final int ySize = 76;

    private int guiLeft;
    private int guiTop;

    private final ItemStack stack;

    private ToggleImageButton mute;
    private ToggleImageButton activate;
    private Text canal;

    private static final Identifier BG_TEXTURE = new Identifier(Constants.MOD_ID, "textures/gui/gui_walkietalkie.png");
    private static final Identifier MUTE_TEXTURE = new Identifier("voicechat", "textures/icons/microphone_button.png");
    private static final Identifier ACTIVATE_TEXTURE = new Identifier(Constants.MOD_ID, "textures/icons/activate.png");

    public WalkieTalkieScreen(ItemStack stack) {
        super(new TranslatableText("gui.walkietalkie.title"));
        instance = this;
        this.stack = stack;

        MinecraftClient.getInstance().openScreen(this);
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - xSize) / 2;
        this.guiTop = (this.height - ySize) / 2;

        mute = new ToggleImageButton(guiLeft + 6, guiTop + ySize - 6 - 20, MUTE_TEXTURE, button -> sendUpdateWalkieTalkie(2, false), stack.getTag().getBoolean(WalkieTalkieItem.NBT_KEY_MUTE));
        this.addButton(mute);

        activate = new ToggleImageButton(guiLeft + 28, guiTop + ySize - 26, ACTIVATE_TEXTURE, button -> sendUpdateWalkieTalkie(0, false), stack.getTag().getBoolean(WalkieTalkieItem.NBT_KEY_ACTIVATE));
        this.addButton(activate);

        this.addButton(new ButtonWidget(this.width / 2 - 10 + 40, guiTop + 20, 20, 20, Text.of(">"), button -> sendUpdateWalkieTalkie(1, true)));

        this.addButton(new ButtonWidget(this.width / 2 - 10 - 40, guiTop + 20, 20, 20, Text.of("<"), button -> sendUpdateWalkieTalkie(1, false)));

        canal = Text.of(String.valueOf(stack.getTag().getInt(WalkieTalkieItem.NBT_KEY_CANAL)));

    }

    private void sendUpdateWalkieTalkie(int index, boolean status) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(index);
        buf.writeBoolean(status);
        NetworkManager.sendToServer(ModMessages.UPDATE_WALKIETALKIE_C2S, buf);
    }

    @Override
    public void renderBackground(MatrixStack matrices) {
        super.renderBackground(matrices);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        this.client.getTextureManager().bindTexture(BG_TEXTURE);
        drawTexture(matrices, guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawCenteredText(matrices, this.textRenderer, this.title.getString(), guiTop + 7, 4210752);
        drawCenteredText(matrices, this.textRenderer, this.canal.getString(), guiTop + 26, 4210752);
    }

    protected void drawCenteredText(MatrixStack matrices, TextRenderer textRenderer, String text, int y, int color) {
        textRenderer.draw(matrices, text, (float) (this.width / 2 - textRenderer.getWidth(text) / 2), y, color);
    }

    public void updateButtons(ItemStack stack) {
        mute.setState(stack.getTag().getBoolean(WalkieTalkieItem.NBT_KEY_MUTE));
        activate.setState(stack.getTag().getBoolean(WalkieTalkieItem.NBT_KEY_ACTIVATE));
        canal = Text.of(String.valueOf(stack.getTag().getInt(WalkieTalkieItem.NBT_KEY_CANAL)));
    }

    public static WalkieTalkieScreen getInstance() {
        return instance;
    }

}
