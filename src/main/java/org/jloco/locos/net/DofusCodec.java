package org.jloco.locos.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.string.StringDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Dofus 1.x framing: every frame ends with a NUL byte. Client frames carry one or more packets separated by
 * "\n"; server frames carry one packet. Text is UTF-8.
 */
public final class DofusCodec {

    public static final int MAX_FRAME_BYTES = 4096;

    private DofusCodec() {}

    public static void install(ChannelPipeline pipeline) {
        pipeline.addLast(
                        "frames",
                        new DelimiterBasedFrameDecoder(
                                MAX_FRAME_BYTES, true, true, Unpooled.wrappedBuffer(new byte[] {0})))
                .addLast("decoder", new StringDecoder(StandardCharsets.UTF_8))
                .addLast("encoder", new FrameEncoder());
    }

    @ChannelHandler.Sharable
    static final class FrameEncoder extends MessageToByteEncoder<String> {

        FrameEncoder() {
            super(String.class);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, String packet, ByteBuf out) {
            out.writeCharSequence(packet, StandardCharsets.UTF_8);
            out.writeByte(0);
        }
    }
}
