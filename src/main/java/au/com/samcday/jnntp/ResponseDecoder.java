package au.com.samcday.jnntp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import static au.com.samcday.jnntp.Util.pullAsciiNumberFromBuffer;

/**
 * This decoder handles the raw responses from the server, and relies on an injected {@link ResponseStateNotifier}
 * to determine if it should parse a single line response, or a multiline one. It assumes framing has already been
 * handled by a downstream {@link org.jboss.netty.handler.codec.frame.LineBasedFrameDecoder}.
 */
public class ResponseDecoder extends OneToOneDecoder {
    private byte LINE_TERMINATOR = 0x2E;

    private CommandPipelinePeeker pipelinePeeker;
    private NntpResponseFactory responseFactory;

    private NntpResponse currentMultilineResponse;

    private ResponseStateNotifier responseStateNotifier;
    private boolean decodingMultiline;

    public ResponseDecoder(ResponseStateNotifier responseStateNotifier) {
        this.responseStateNotifier = responseStateNotifier;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if(!(msg instanceof ChannelBuffer)) return msg;
        ChannelBuffer buffer = (ChannelBuffer)msg;

        if(this.decodingMultiline) {
            if(buffer.getByte(buffer.readerIndex()) == LINE_TERMINATOR) {
                buffer.skipBytes(1);
                if(!buffer.readable()) {
                    this.decodingMultiline = false;
                    return MultilineEndMessage.INSTANCE;
                }
            }

            return buffer.slice();
        }

        int code = pullAsciiNumberFromBuffer(buffer, 3);
        buffer.skipBytes(1);

        this.decodingMultiline = this.responseStateNotifier.isMultiline(code);

        return new RawResponseMessage(code, buffer.slice(), this.decodingMultiline);
        /*

        NntpResponse.ResponseType type = this.pipelinePeeker.peekType();
        NntpResponse response = responseFactory.newResponse(type);
        response.setCode(code);
        response.process(buffer);

        // Just in case the response class didn't fully parse the buffer for whatever reason...
        buffer.skipBytes(buffer.readableBytes());

        if(response.isMultiline()) {
            this.currentMultilineResponse = response;
            return null;
        }
        else {
            return response;
        }*/
    }
}
