/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.protocol.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;
import rx.functions.Func1;

public class RequestProcessor implements RequestHandler<ByteBuf, ByteBuf> {

    public static final List<String> smallStreamContent;

    public static final List<String> largeStreamContent;

    static {

        List<String> smallStreamListLocal = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            smallStreamListLocal.add("line " + i);
        }
        smallStreamContent = Collections.unmodifiableList(smallStreamListLocal);

        List<String> largeStreamListLocal = new ArrayList<String>();
        for (int i = 0; i < 1000; i++) {
            largeStreamListLocal.add("line " + i);
        }
        largeStreamContent = Collections.unmodifiableList(largeStreamListLocal);
    }
    
    public Observable<Void> handleSingleEntity(HttpServerResponse<ByteBuf> response) {
        byte[] responseBytes = "Hello world".getBytes();
        return response.writeBytesAndFlush(responseBytes);
    }

    public Observable<Void> handleStreamWithoutChunking(HttpServerResponse<ByteBuf> response) {
        response.getHeaders().add(HttpHeaders.Names.CONTENT_TYPE, "text/event-stream");
        for (String contentPart : smallStreamContent) {
            response.writeString("data:");
            response.writeString(contentPart);
            response.writeString("\n\n");
        }
        return response.flush();
    }

    public Observable<Void> handleStream(HttpServerResponse<ByteBuf> response) {
        return sendStreamingResponse(response, smallStreamContent);
    }

    public Observable<Void> handleLargeStream(HttpServerResponse<ByteBuf> response) {
        return sendStreamingResponse(response, largeStreamContent);
    }

    public Observable<Void> simulateTimeout(HttpServerRequest<ByteBuf> httpRequest, HttpServerResponse<ByteBuf> response) {
        String uri = httpRequest.getUri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> timeout = decoder.parameters().get("timeout");
        byte[] contentBytes;
        HttpResponseStatus status = HttpResponseStatus.NO_CONTENT;
        if (null != timeout && !timeout.isEmpty()) {
            try {
                Thread.sleep(Integer.parseInt(timeout.get(0)));
                contentBytes = "".getBytes();
            } catch (Exception e) {
                contentBytes = e.getMessage().getBytes();
                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            }
        } else {
            status = HttpResponseStatus.BAD_REQUEST;
            contentBytes = "Please provide a timeout parameter.".getBytes();
        }

        response.setStatus(status);
        return response.writeBytesAndFlush(contentBytes);
    }

    public Observable<Void> handlePost(final HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        return request.getContent().flatMap(new Func1<ByteBuf, Observable<Void>>() {
            @Override
            public Observable<Void> call(ByteBuf t1) {
                String content = t1.toString(Charset.defaultCharset());
                return response.writeBytesAndFlush(content.getBytes(Charset.defaultCharset()));
            }}
        );
    }
    
    public Observable<Void> handleCloseConnection(final HttpServerResponse<ByteBuf> response) {
        response.getHeaders().add("Connection", "close");
        byte[] responseBytes = "Hello world".getBytes();
        return response.writeBytesAndFlush(responseBytes);
    }
    
    public Observable<Void> handleKeepAliveTimeout(final HttpServerResponse<ByteBuf> response) {
        response.getHeaders().add("Keep-Alive", "timeout=1");
        byte[] responseBytes = "Hello world".getBytes();
        return response.writeBytesAndFlush(responseBytes);
    }

    public Observable<Void> redirectGet(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        response.getHeaders().add("Location", "http://localhost:" + request.getQueryParameters().get("port").get(0) + "/test/singleEntity");
        response.setStatus(HttpResponseStatus.MOVED_PERMANENTLY);
        return response.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }
    
    public Observable<Void> redirectPost(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        response.getHeaders().add("Location", "http://localhost:" + request.getQueryParameters().get("port").get(0) + "/test/post");
        response.setStatus(HttpResponseStatus.MOVED_PERMANENTLY);
        return response.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }
    
    private static Observable<Void> sendStreamingResponse(HttpServerResponse<ByteBuf> response, List<String> data) {
        response.getHeaders().add(HttpHeaders.Names.CONTENT_TYPE, "text/event-stream");
        response.getHeaders().add(HttpHeaders.Names.TRANSFER_ENCODING, "chunked");
        for (String line : data) {
            byte[] contentBytes = ("data:" + line + "\n\n").getBytes();
            response.writeBytes(contentBytes);
        }

        return response.flush();
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        String uri = request.getUri();
        if (uri.contains("test/singleEntity")) {
            // in case of redirect, uri starts with /test/singleEntity 
            return handleSingleEntity(response);
        } else if (uri.startsWith("test/stream")) {
            return handleStream(response);
        } else if (uri.startsWith("test/nochunk_stream")) {
            return handleStreamWithoutChunking(response);
        } else if (uri.startsWith("test/largeStream")) {
            return handleLargeStream(response);
        } else if (uri.startsWith("test/timeout")) {
            return simulateTimeout(request, response);
        } else if (uri.contains("test/post")) {
            return handlePost(request, response);
        } else if (uri.startsWith("test/closeConnection")) {
            return handleCloseConnection(response);
        } else if (uri.startsWith("test/keepAliveTimeout")) {
            return handleKeepAliveTimeout(response);
        } else if (uri.startsWith("test/redirect") && request.getHttpMethod().equals(HttpMethod.GET)) {
            return redirectGet(request, response);
        } else if (uri.startsWith("test/redirectPost") && request.getHttpMethod().equals(HttpMethod.POST)) {
            return redirectPost(request, response);
        } else {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return response.flush();
        }
    }
}
