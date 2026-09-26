package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.action.ActionNextTick;
import com.moulberry.flashback.action.ActionRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;

/**
 * Cuts a Flashback chunk file to a tick range. The actions before the cut are folded into the snapshot with their tick breaks
 * removed, so entering the chunk plays them all at once and the chunk starts right at the new first tick; actions
 * after the last kept tick are dropped.
 */
final class ChunkCutter {
    private ChunkCutter() {}

    static byte[] cut(byte[] chunk, int dropTicks, int keepTicks) throws IOException {
        FriendlyByteBuf in = new FriendlyByteBuf(Unpooled.wrappedBuffer(chunk));
        in.readInt();
        int registrySize = in.readVarInt();
        int nextTick = -1;
        for (int i = 0; i < registrySize; i++) {
            if (ActionRegistry.getAction(in.readIdentifier()) instanceof ActionNextTick) nextTick = i;
        }
        if (nextTick < 0) throw new IOException("Chunk has no next-tick action");
        int headerEnd = in.readerIndex();
        int snapshotSize = in.readInt();
        if (snapshotSize < 0 || snapshotSize > in.readableBytes()) throw new IOException("Invalid snapshot size " + snapshotSize);
        ByteBuf snapshot = Unpooled.buffer();
        snapshot.writeBytes(chunk, in.readerIndex(), snapshotSize);
        in.skipBytes(snapshotSize);

        ByteBuf actions = Unpooled.buffer();
        int ticks = 0;
        while (in.isReadable() && ticks < dropTicks + keepTicks) {
            int start = in.readerIndex();
            int id = in.readVarInt();
            int length = in.readInt();
            in.skipBytes(length);
            boolean tick = id == nextTick;
            if (ticks < dropTicks) {
                if (!tick) snapshot.writeBytes(chunk, start, in.readerIndex() - start);
            } else {
                actions.writeBytes(chunk, start, in.readerIndex() - start);
            }
            if (tick) ticks++;
        }

        ByteBuf out = Unpooled.buffer(headerEnd + 4 + snapshot.readableBytes() + actions.readableBytes());
        out.writeBytes(chunk, 0, headerEnd);
        out.writeInt(snapshot.readableBytes());
        out.writeBytes(snapshot);
        out.writeBytes(actions);
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        return bytes;
    }
}
