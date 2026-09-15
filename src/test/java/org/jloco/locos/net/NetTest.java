package org.jloco.locos.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NetTest {

    private static EmbeddedChannel dofusChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DofusCodec.install(channel.pipeline());
        return channel;
    }

    private static ByteBuf bytes(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    @Test
    void splitsClientFramesOnNul() {
        EmbeddedChannel channel = dofusChannel();
        channel.writeInbound(bytes("1.39.8e\n\0account\n#1ab"), bytes("cd\n\0"));
        assertThat((String) channel.readInbound()).isEqualTo("1.39.8e\n");
        assertThat((String) channel.readInbound()).isEqualTo("account\n#1abcd\n");
        assertThat(channel.<Object>readInbound()).isNull();
    }

    @Test
    void decodesUtf8() {
        EmbeddedChannel channel = dofusChannel();
        channel.writeInbound(bytes("Aé\0"));
        assertThat((String) channel.readInbound()).isEqualTo("Aé");
    }

    @Test
    void terminatesServerPacketsWithNul() {
        EmbeddedChannel channel = dofusChannel();
        channel.writeOutbound("HCkey");
        ByteBuf out = channel.readOutbound();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("HCkey\0");
        out.release();
    }

    @Test
    void refusesFramesOverTheLimit() {
        EmbeddedChannel channel = dofusChannel();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> channel.writeInbound(bytes("A".repeat(DofusCodec.MAX_FRAME_BYTES + 1))))
                .isInstanceOf(TooLongFrameException.class);
    }

    @Test
    void serialExecutorKeepsOrderAcrossVirtualThreads() throws InterruptedException {
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            SerialExecutor serial = new SerialExecutor(virtualThreads);
            for (int i = 0; i < 1000; i++) {
                int value = i;
                serial.execute(() -> {
                    order.add(value);
                    if (value == 999) {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(order).hasSize(1000).isSorted();
    }

    @Test
    void serialExecutorSurvivesAFailingTask() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        try (ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            SerialExecutor serial = new SerialExecutor(virtualThreads);
            serial.execute(() -> {
                throw new IllegalStateException("boom");
            });
            serial.execute(done::countDown);
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
