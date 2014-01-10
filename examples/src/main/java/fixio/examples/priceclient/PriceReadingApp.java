/*
 * Copyright 2014 The FIX.io Project
 *
 * The FIX.io Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package fixio.examples.priceclient;

import fixio.events.LogonEvent;
import fixio.fixprotocol.*;
import fixio.handlers.FixApplicationAdapter;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class PriceReadingApp extends FixApplicationAdapter {

    public static final int MAX_QUOTE_COUNT = 100_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(PriceReadingApp.class);
    private int counter;
    private long startTimeNanos;
    private boolean finished;
    private String quoteRequestId;

    @Override
    protected void onLogon(ChannelHandlerContext ctx, LogonEvent msg) {
        counter = 0;
        ctx.writeAndFlush(createQuoteRequest());
        startTimeNanos = System.nanoTime();
    }

    @Override
    protected void onMessage(ChannelHandlerContext ctx, FixMessage msg, List<Object> out) throws Exception {
        assert (msg != null) : "Message can't be null";
        switch (msg.getMessageType()) {
            case MessageTypes.QUOTE:
                onQuote(msg);
                break;
        }
        if (counter % 10000 == 0) {
            LOGGER.debug("Read {} Quotes", counter);
        }
        if (counter > MAX_QUOTE_COUNT && !finished) {
            finished = true;

            long timeMillis = (System.nanoTime() - startTimeNanos) / 1000000;
            LOGGER.info("Read {} Quotes in {} ms, ~{} Quote/sec", counter, timeMillis, counter * 1000.0 / timeMillis);

            ctx.writeAndFlush(createQuoteCancel()).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void onQuote(FixMessage quote) {
        LOGGER.trace("quote = {}", quote);
        counter++;
    }

    private FixMessageBuilder createQuoteCancel() {
        FixMessageBuilder quoteCancel = new FixMessageBuilderImpl(MessageTypes.QUOTE_CANCEL);//QuoteCancel
        quoteCancel.add(FieldType.QuoteRequestType, "4"); //QuoteRequestType=AUTOMATIC
        quoteCancel.add(FieldType.QuoteReqID, quoteRequestId); //quoteReqId
        return quoteCancel;
    }

    private FixMessageBuilder createQuoteRequest() {
        FixMessageBuilder quoteRequest = new FixMessageBuilderImpl(MessageTypes.QUOTE_REQUEST);
        quoteRequestId = Long.toHexString(System.currentTimeMillis());
        quoteRequest.add(FieldType.QuoteReqID, quoteRequestId); //quoteReqId
        String clientReqId = quoteRequestId + counter;
        quoteRequest.add(11, clientReqId);

        Group instrument1 = quoteRequest.newGroup(FieldType.NoRelatedSym);//noRelatedSym
        instrument1.add(FieldType.Symbol, "EUR/USD");
        instrument1.add(FieldType.SecurityType, "CURRENCY");

        Group instrument2 = quoteRequest.newGroup(FieldType.NoRelatedSym);//noRelatedSym
        instrument2.add(FieldType.Symbol, "EUR/CHF");
        instrument2.add(FieldType.SecurityType, "CURRENCY");

        quoteRequest.add(FieldType.QuoteRequestType, 2); //QuoteRequestType=AUTOMATIC
        return quoteRequest;
    }
}
